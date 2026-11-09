/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainHead;

public class ForkChoiceTrigger {
  public static final int WARNING_TIME_MILLIS = 250;
  public static final int DEBUG_TIME_MILLIS = 10;
  private static final Logger LOG = LogManager.getLogger();
  private final ForkChoiceRatchet forkChoiceRatchet;
  private final ForkChoice forkChoice;
  private final int attestationWaitLimitMillis;
  private final TimeProvider timeProvider;

  public ForkChoiceTrigger(
      final ForkChoice forkChoice,
      final int attestationWaitLimitMillis,
      final TimeProvider timeProvider) {
    this.forkChoiceRatchet = new ForkChoiceRatchet(forkChoice);
    this.forkChoice = forkChoice;
    this.attestationWaitLimitMillis = attestationWaitLimitMillis;
    this.timeProvider = timeProvider;
  }

  public void onSlotStartedWhileSyncing(final UInt64 nodeSlot) {
    forkChoiceRatchet.ensureForkChoiceCompleteForSlot(nodeSlot).join();
  }

  public void onAttestationsDueForSlot(final UInt64 nodeSlot) {
    try {
      final UInt64 startTimeMillis = timeProvider.getTimeInMillis();
      forkChoiceRatchet
          .ensureForkChoiceCompleteForSlot(nodeSlot)
          .get(attestationWaitLimitMillis, TimeUnit.MILLISECONDS);
      final UInt64 duration = timeProvider.getTimeInMillis().minusMinZero(startTimeMillis);
      if (duration.isGreaterThanOrEqualTo(WARNING_TIME_MILLIS)) {
        LOG.warn(
            "Took {} ms waiting for fork choice to complete at slot {}, when attestations were due.",
            duration,
            nodeSlot);
      } else if (duration.isGreaterThanOrEqualTo(DEBUG_TIME_MILLIS)) {
        LOG.debug(
            "Took {} ms waiting for fork choice to complete at slot {}, when attestations were due.",
            duration,
            nodeSlot);
      }
    } catch (InterruptedException e) {
      LOG.error("Interrupted while waiting for fork choice to complete for slot " + nodeSlot, e);
      Thread.currentThread().interrupt();
    } catch (TimeoutException e) {
      LOG.warn(
          "Timeout waiting for fork choice to complete for slot {} (limit: {} ms). Continuing without waiting.",
          nodeSlot,
          attestationWaitLimitMillis);
      // Don't throw exception - continue execution to avoid blocking milestone transitions
    } catch (ExecutionException e) {
      LOG.error("Execution exception waiting for fork choice at slot " + nodeSlot, e);
      throw new RuntimeException(e);
    }
  }

  public SafeFuture<ChainHead> prepareForBlockProduction(
      final UInt64 slot, final BlockProductionPerformance blockProductionPerformance) {
    return forkChoice.prepareForBlockProduction(slot, blockProductionPerformance);
  }

  public SafeFuture<Void> prepareForAttestationProduction(final UInt64 slot) {
    return forkChoiceRatchet.ensureForkChoiceCompleteForSlot(slot);
  }
}

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

package tech.pegasys.teku.spec.logic.versions.bellatrix.helpers;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

public class BellatrixTransitionHelpers {

  private final SpecConfigBellatrix specConfig;
  private final MiscHelpersBellatrix miscHelpers;

  public BellatrixTransitionHelpers(
      final SpecConfigBellatrix specConfig, final MiscHelpersBellatrix miscHelpers) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
  }

  /**
   * When the first non-empty execution payload is received we need to perform some extra checks on
   * what that payload uses as the PoW chain head (the PoW chain block it uses as parentRoot).
   *
   * <p>Specifically we need to check that the PoW chain head block is above TTD and it's parent
   * (second last PoW chain block) is below TTD.
   *
   * <p>That is, the PoW chain stops as soon as one block has exceeded TTD and from that point on
   * merges into the beacon chain.
   *
   * @param executionPayloadSummary the first non-empty payload on the beacon chain
   * @param blockSlot the slot of the block the executionPayload is from
   * @return a future containing the validation result for the execution payload
   */
  public SafeFuture<PayloadStatus> validateMergeBlock(
      final ExecutionPayloadSummary executionPayloadSummary, final UInt64 blockSlot) {
    if (!specConfig.getTerminalBlockHash().isZero()) {
      return validateWithTerminalBlockHash(executionPayloadSummary, blockSlot);
    }

    return SafeFuture.completedFuture(
        PayloadStatus.invalid(
            Optional.empty(), Optional.of("Total difficulty check is no more supported")));
  }

  private SafeFuture<PayloadStatus> validateWithTerminalBlockHash(
      final ExecutionPayloadSummary executionPayloadSummary, final UInt64 blockSlot) {
    if (miscHelpers
        .computeEpochAtSlot(blockSlot)
        .isLessThan(specConfig.getTerminalBlockHashActivationEpoch())) {
      return invalid("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH has not been reached");
    }
    if (!executionPayloadSummary.getParentHash().equals(specConfig.getTerminalBlockHash())) {
      return invalid("Terminal PoW block does not match TERMINAL_BLOCK_HASH");
    }
    return SafeFuture.completedFuture(PayloadStatus.VALID);
  }

  private static SafeFuture<PayloadStatus> invalid(final String message) {
    return completedFuture(PayloadStatus.invalid(Optional.empty(), Optional.of(message)));
  }
}

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

package tech.pegasys.teku.statetransition.util;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

/**
 * This class implements common functionalities for a pool. In particular, it is responsible for:
 *
 * <p>- filtering elements based on slot, which needs to fall under a given range (historic and
 * future limits)
 *
 * <p>- handling pruning calculation logic based on slot finalization
 */
public abstract class AbstractIgnoringFutureHistoricalSlot
    implements SlotEventsChannel, FinalizedCheckpointChannel {
  public static final UInt64 PRUNE_SLOT = UInt64.ONE;
  private final Spec spec;

  // Define the range of slots we care about
  private final UInt64 futureSlotTolerance;
  private final UInt64 historicalSlotTolerance;
  private final UInt64 slotsInEpoch;

  private volatile UInt64 currentSlot = UInt64.ZERO;
  private volatile UInt64 latestFinalizedSlot = GENESIS_SLOT;

  public AbstractIgnoringFutureHistoricalSlot(
      final Spec spec, final UInt64 futureSlotTolerance, final UInt64 historicalSlotTolerance) {
    this.spec = spec;
    this.futureSlotTolerance = futureSlotTolerance;
    this.historicalSlotTolerance = historicalSlotTolerance;
    this.slotsInEpoch = UInt64.valueOf(spec.getGenesisSpec().getSlotsPerEpoch());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
    if (currentSlot.mod(slotsInEpoch).equals(PRUNE_SLOT)) {
      // Purge old items
      prune();
    }
  }

  @VisibleForTesting
  public void prune() {
    final UInt64 slotLimit = latestFinalizedSlot.max(calculateItemAgeLimit());
    prune(slotLimit);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    this.latestFinalizedSlot = checkpoint.getEpochStartSlot(spec);
  }

  protected abstract void prune(UInt64 slotLimit);

  protected UInt64 getCurrentSlot() {
    return currentSlot;
  }

  protected boolean shouldIgnoreItemAtSlot(final UInt64 slot) {
    return isSlotTooOld(slot) || isSlotFromFarFuture(slot);
  }

  private boolean isSlotTooOld(final UInt64 slot) {
    return isSlotFromAFinalizedSlot(slot) || isSlotOutsideOfHistoricalLimit(slot);
  }

  private boolean isSlotFromFarFuture(final UInt64 slot) {
    final UInt64 slotLimit = calculateFutureItemLimit();
    return slot.isGreaterThan(slotLimit);
  }

  private boolean isSlotOutsideOfHistoricalLimit(final UInt64 slot) {
    final UInt64 slotLimit = calculateItemAgeLimit();
    return slot.isLessThanOrEqualTo(slotLimit);
  }

  private boolean isSlotFromAFinalizedSlot(final UInt64 slot) {
    return slot.isLessThanOrEqualTo(latestFinalizedSlot);
  }

  private UInt64 calculateItemAgeLimit() {
    return currentSlot.isGreaterThan(historicalSlotTolerance.plus(UInt64.ONE))
        ? currentSlot.minus(UInt64.ONE).minus(historicalSlotTolerance)
        : GENESIS_SLOT;
  }

  private UInt64 calculateFutureItemLimit() {
    return currentSlot.plus(futureSlotTolerance);
  }
}

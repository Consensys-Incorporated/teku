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

package tech.pegasys.teku.ethereum.executionlayer;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BuilderCircuitBreakerImpl implements BuilderCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final int faultInspectionWindow;
  private final int minimumUniqueBlockRootsInWindow;
  private final int consecutiveAllowedFaults;

  public BuilderCircuitBreakerImpl(
      final Spec spec,
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.spec = spec;
    this.faultInspectionWindow = faultInspectionWindow;
    this.minimumUniqueBlockRootsInWindow = faultInspectionWindow - allowedFaults;
    this.consecutiveAllowedFaults = consecutiveAllowedFaults;
  }

  @Override
  public boolean isEngaged(final BeaconState state) {

    final InspectionWindowCounters inspectionWindowCounters = getInspectionWindowCounters(state);
    if (inspectionWindowCounters.uniqueBlockRootsCount < minimumUniqueBlockRootsInWindow) {
      LOG.debug(
          "Builder circuit breaker engaged: slot: {}, uniqueBlockRootsCount: {}, window: {},  minimumUniqueBlockRootsInWindow: {}",
          state.getSlot(),
          inspectionWindowCounters.uniqueBlockRootsCount,
          faultInspectionWindow,
          minimumUniqueBlockRootsInWindow);
      return true;
    }

    if (inspectionWindowCounters.lastConsecutiveEmptySlots > consecutiveAllowedFaults) {
      LOG.debug(
          "Builder circuit breaker engaged: slot: {}, lastConsecutiveEmptySlots: {}, window: {},  consecutiveAllowedFaults: {}",
          state.getSlot(),
          inspectionWindowCounters.lastConsecutiveEmptySlots,
          faultInspectionWindow,
          consecutiveAllowedFaults);
      return true;
    }

    LOG.debug("Builder circuit breaker has not engaged.");

    return false;
  }

  @VisibleForTesting
  InspectionWindowCounters getInspectionWindowCounters(final BeaconState state)
      throws IllegalArgumentException {
    final int slotsPerHistoricalRoot =
        spec.atSlot(state.getSlot()).getConfig().getSlotsPerHistoricalRoot();
    checkArgument(
        faultInspectionWindow <= slotsPerHistoricalRoot,
        "faultInspectionWindow (%s) cannot exceed slotsPerHistoricalRoot config (%s)",
        faultInspectionWindow,
        slotsPerHistoricalRoot);

    final HashSet<Bytes32> uniqueBlockRoots = new HashSet<>();
    final SszBytes32Vector blockRoots = state.getBlockRoots();

    // state slot is the slot we are building for
    // thus our fault window will be (inclusive)
    // FROM (state_slot-1)-(faultInspectionWindow-1) TO state_slot-1

    // of which:
    // state_slot-1 -> will be represented by getLatestBlockHeader
    // FROM (state_slot-1)-(faultInspectionWindow-1) TO state_slot-2 -> to be found in blockRoots

    // (state_slot-1)-(faultInspectionWindow-1) = state_slot-faultInspectionWindow
    final UInt64 firstSlotOfInspectionWindow = state.getSlot().minusMinZero(faultInspectionWindow);
    final UInt64 lastSlotOfInspectionWindow = state.getSlot().minusMinZero(1);

    int lastConsecutiveEmptySlots =
        lastSlotOfInspectionWindow.minus(state.getLatestBlockHeader().getSlot()).intValue();

    if (lastConsecutiveEmptySlots >= faultInspectionWindow) {
      return new InspectionWindowCounters(0, lastConsecutiveEmptySlots);
    }

    // we can consider getLatestBlockHeader root because at this stage has been already updated with
    // state root
    uniqueBlockRoots.add(state.getLatestBlockHeader().getRoot());

    UInt64 currentSlot = firstSlotOfInspectionWindow;
    while (currentSlot.isLessThan(lastSlotOfInspectionWindow)) {
      final int currentBlockRootIndex = currentSlot.mod(slotsPerHistoricalRoot).intValue();
      uniqueBlockRoots.add(blockRoots.getElement(currentBlockRootIndex));
      currentSlot = currentSlot.increment();
    }

    return new InspectionWindowCounters(uniqueBlockRoots.size(), lastConsecutiveEmptySlots);
  }

  protected static class InspectionWindowCounters {
    final int uniqueBlockRootsCount;
    final int lastConsecutiveEmptySlots;

    private InspectionWindowCounters(
        final int uniqueBlockRootsCount, final int lastConsecutiveEmptySlots) {
      this.uniqueBlockRootsCount = uniqueBlockRootsCount;
      this.lastConsecutiveEmptySlots = lastConsecutiveEmptySlots;
    }
  }
}

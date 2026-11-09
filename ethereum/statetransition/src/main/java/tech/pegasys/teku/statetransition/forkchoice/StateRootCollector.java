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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StateRootCollector {
  private static final Logger LOG = LogManager.getLogger();

  public static void addParentStateRoots(
      final Spec spec, final BeaconState blockSlotState, final StoreTransaction transaction) {
    final UInt64 newBlockSlot = blockSlotState.getSlot();
    final int slotsPerHistoricalRoot = spec.getSpecConfig(newBlockSlot).getSlotsPerHistoricalRoot();
    final SszBytes32Vector blockRoots = blockSlotState.getBlockRoots();
    final SszBytes32Vector stateRoots = blockSlotState.getStateRoots();
    final UInt64 minimumSlot = newBlockSlot.minusMinZero(slotsPerHistoricalRoot);
    // Get the parent block root from the state as the genesis block root is recorded as 0
    final Bytes32 parentBlockRoot =
        blockRoots.getElement(newBlockSlot.minusMinZero(1).mod(slotsPerHistoricalRoot).intValue());
    UInt64 slot = newBlockSlot.minusMinZero(1);
    while (slot.isGreaterThanOrEqualTo(minimumSlot) && !slot.isZero()) {
      final Bytes32 previousBlockRoot = getValue(blockRoots, slot.minus(1), slotsPerHistoricalRoot);
      if (!previousBlockRoot.equals(parentBlockRoot)) {
        // Reached the first slot of the parent block - its state root is already be imported
        return;
      }
      transaction.putStateRoot(
          getValue(stateRoots, slot, slotsPerHistoricalRoot),
          new SlotAndBlockRoot(slot, parentBlockRoot));
      slot = slot.decrement();
    }
    if (!slot.isZero()) {
      LOG.warn("Missing some state root mappings prior to slot {}", minimumSlot);
    }
  }

  private static Bytes32 getValue(
      final SszBytes32Vector roots, final UInt64 slot, final int slotsPerHistoricalRoot) {
    return roots.getElement(slot.mod(slotsPerHistoricalRoot).intValue());
  }
}

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

package tech.pegasys.teku.storage.server.state;

import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateRootRecorder {
  private static final Logger LOG = LogManager.getLogger();
  private final UInt64 slotsPerHistoricalRoot;
  private UInt64 slot;
  private final BiConsumer<Bytes32, UInt64> stateRootConsumer;

  public StateRootRecorder(
      final UInt64 slot, final BiConsumer<Bytes32, UInt64> stateRootConsumer, final Spec spec) {
    this.stateRootConsumer = stateRootConsumer;
    this.slot = slot;
    this.slotsPerHistoricalRoot = UInt64.valueOf(spec.getSlotsPerHistoricalRoot(slot));
  }

  public void acceptNextState(final BeaconState state) {
    if (slot.plus(slotsPerHistoricalRoot).compareTo(state.getSlot()) < 0) {
      final UInt64 floor = state.getSlot().minus(slotsPerHistoricalRoot);
      LOG.warn("Missing state root mappings from slot {} to {}", slot, floor);
      slot = floor;
    }

    while (slot.compareTo(state.getSlot()) < 0) {
      stateRootConsumer.accept(
          state.getStateRoots().getElement(slot.mod(slotsPerHistoricalRoot).intValue()), slot);
      slot = slot.plus(UInt64.ONE);
    }

    stateRootConsumer.accept(state.hashTreeRoot(), state.getSlot());
  }
}

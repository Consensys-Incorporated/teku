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

package tech.pegasys.teku.spec.logic;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecProvider specProvider;

  public StateTransition(final SpecProvider specProvider) {
    this.specProvider = specProvider;
  }

  public BeaconState processSlots(final BeaconState preState, final UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    try {
      checkArgument(
          preState.getSlot().compareTo(slot) < 0,
          "process_slots: State slot %s higher than given slot %s",
          preState.getSlot(),
          slot);
      BeaconState state = preState;

      SpecVersion currentSpec = specProvider.getSpec(state.getSlot());
      while (state.getSlot().compareTo(slot) < 0) {
        // Transition from current to new slot (advance by 1)
        final UInt64 currentSlot = state.getSlot();
        final UInt64 newSlot = currentSlot.plus(1);
        final boolean isEpochTransition =
            newSlot.mod(currentSpec.getSlotsPerEpoch()).equals(UInt64.ZERO);

        state = processSlot(currentSpec, state);
        // Process epoch on the start slot of the next epoch
        if (isEpochTransition) {
          state = currentSpec.getEpochProcessor().processEpoch(state);
        }
        state = state.updated(s -> s.setSlot(newSlot));

        // Update spec, perform state upgrades on epoch boundaries
        if (isEpochTransition) {
          final SpecVersion newSpec = specProvider.getSpec(newSlot);
          if (!newSpec.getMilestone().equals(currentSpec.getMilestone())) {
            // We've just transition to a new milestone - upgrade the state if necessary
            final BeaconState prevMilestoneState = state;
            state =
                newSpec
                    .getStateUpgrade()
                    .map(u -> (BeaconState) u.upgrade(prevMilestoneState))
                    .orElse(prevMilestoneState);
            // Update spec
            currentSpec = newSpec;
          }
        }
      }
      return state;
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage(), e);
      throw new SlotProcessingException(e);
    }
  }

  private BeaconState processSlot(final SpecVersion spec, final BeaconState preState) {
    // Cache state root
    final Bytes32 previousStateRoot = preState.hashTreeRoot();
    return preState.updated(
        state -> {
          int index = state.getSlot().mod(spec.getSlotsPerHistoricalRoot()).intValue();
          state.getStateRoots().setElement(index, previousStateRoot);

          // Cache latest block header state root
          BeaconBlockHeader latestBlockHeader = state.getLatestBlockHeader();
          if (latestBlockHeader.getStateRoot().equals(Bytes32.ZERO)) {
            BeaconBlockHeader latestBlockHeaderNew =
                new BeaconBlockHeader(
                    latestBlockHeader.getSlot(),
                    latestBlockHeader.getProposerIndex(),
                    latestBlockHeader.getParentRoot(),
                    previousStateRoot,
                    latestBlockHeader.getBodyRoot());
            state.setLatestBlockHeader(latestBlockHeaderNew);
            BeaconStateCache.getSlotCaches(state).onSlotProcessed();
          }

          // Cache block root
          final Bytes32 previousBlockRoot = state.getLatestBlockHeader().hashTreeRoot();
          state.getBlockRoots().setElement(index, previousBlockRoot);

          state
              .toMutableVersionGloas()
              .ifPresent(
                  stateGloas -> {
                    final SszBitvector currentPayloadAvailability =
                        stateGloas.getExecutionPayloadAvailability();
                    final BitSet newPayloadAvailability = currentPayloadAvailability.getAsBitSet();
                    // Unset the next payload availability
                    final int nextAvailabilityIndex =
                        state.getSlot().plus(1).mod(spec.getSlotsPerHistoricalRoot()).intValue();
                    newPayloadAvailability.set(nextAvailabilityIndex, false);
                    stateGloas.setExecutionPayloadAvailability(
                        currentPayloadAvailability
                            .getSchema()
                            .wrapBitSet(currentPayloadAvailability.size(), newPayloadAvailability));
                  });
        });
  }

  public interface SpecProvider {
    SpecVersion getSpec(final UInt64 slot);
  }
}

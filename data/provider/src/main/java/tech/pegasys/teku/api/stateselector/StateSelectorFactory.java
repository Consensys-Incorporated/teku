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

package tech.pegasys.teku.api.stateselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class StateSelectorFactory extends AbstractSelectorFactory<StateSelector> {

  private final Spec spec;

  public StateSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public StateSelector stateRootSelector(final Bytes32 stateRoot) {
    return () -> client.getStateByStateRoot(stateRoot).thenApply(this::addMetaData);
  }

  @Override
  public StateSelector blockRootSelector(final Bytes32 blockRoot) {
    return () -> client.getStateByBlockRoot(blockRoot).thenApply(this::addMetaData);
  }

  @Override
  public StateSelector headSelector() {
    return () -> {
      final Optional<ChainHead> maybeChainHead = client.getChainHead();
      if (maybeChainHead.isEmpty()) {
        // Can't have a state past the current chain head.
        return SafeFuture.completedFuture(Optional.empty());
      }
      final ChainHead chainHead = maybeChainHead.get();
      return chainHead
          .getState()
          .thenApply(
              state ->
                  Optional.of(
                      addMetaData(
                          state,
                          chainHead.isOptimistic(),
                          true,
                          client.isFinalized(state.getSlot()))));
    };
  }

  @Override
  public StateSelector genesisSelector() {
    return () ->
        client
            .getStateAtSlotExact(GENESIS_SLOT)
            .thenApply(
                maybeState -> maybeState.map(state -> addMetaData(state, false, true, true)));
  }

  @Override
  public StateSelector finalizedSelector() {
    return () ->
        client
            .getBestFinalizedState()
            .thenApply(
                maybeFinalized ->
                    maybeFinalized.map(
                        finalized ->
                            addMetaData(finalized, client.isChainHeadOptimistic(), true, true)));
  }

  @Override
  public StateSelector justifiedSelector() {
    return () ->
        client
            .getJustifiedState()
            .thenApply(
                maybeState ->
                    maybeState.map(
                        // The justified checkpoint may change because of optimistically
                        // imported blocks at the head and if the head isn't optimistic, the
                        // justified block can't be optimistic.
                        state -> addMetaData(state, client.isChainHeadOptimistic(), true, false)));
  }

  @Override
  public StateSelector slotSelector(final UInt64 slot) {
    return () ->
        client
            .getChainHead()
            .map(
                head -> {
                  if (slot.isGreaterThan(head.getSlot())) {
                    return SafeFuture.completedFuture(Optional.<StateAndMetaData>empty());
                  }
                  return client
                      .getStateAtSlotExact(slot, head.getRoot())
                      .thenApply(
                          maybeState ->
                              maybeState.map(
                                  state ->
                                      addMetaData(
                                          state,
                                          head.isOptimistic(),
                                          true,
                                          client.isFinalized(slot))));
                })
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private Optional<StateAndMetaData> addMetaData(final Optional<BeaconState> maybeState) {
    final Optional<ChainHead> maybeChainHead = client.getChainHead();
    if (maybeChainHead.isEmpty() || maybeState.isEmpty()) {
      return Optional.empty();
    } else {
      final ChainHead chainHead = maybeChainHead.get();
      final BeaconState state = maybeState.get();
      final Bytes32 blockRoot = BeaconBlockHeader.fromState(state).getRoot();
      return Optional.of(
          addMetaData(
              state,
              chainHead.isOptimistic() || client.isOptimisticBlock(blockRoot),
              client.isCanonicalBlock(state.getSlot(), blockRoot, chainHead.getRoot()),
              client.isFinalized(state.getSlot())));
    }
  }

  private StateAndMetaData addMetaData(
      final BeaconState state,
      final boolean executionOptimistic,
      final boolean canonical,
      final boolean finalized) {
    return new StateAndMetaData(
        state,
        spec.atSlot(state.getSlot()).getMilestone(),
        executionOptimistic,
        canonical,
        finalized);
  }
}

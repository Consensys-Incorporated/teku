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

package tech.pegasys.teku.statetransition;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class EpochCachePrimer {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final AsyncRunner asyncRunner;

  public EpochCachePrimer(
      final Spec spec, final RecentChainData recentChainData, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.asyncRunner = asyncRunner;
  }

  public void primeCacheForEpoch(final UInt64 epoch) {
    final UInt64 firstSlot = spec.computeStartSlotAtEpoch(epoch);
    recentChainData
        .getHeadBlock()
        // Don't preprocess epoch if we're more than an epoch behind as we likely need to sync
        .filter(
            headBlock ->
                isWithinOneEpochOfHeadBlock(firstSlot, headBlock)
                    && isAfterHeadBlockEpoch(epoch, headBlock))
        .ifPresent(
            headBlock ->
                asyncRunner
                    .runAsync(() -> primeCacheForBlockAtSlot(headBlock, firstSlot))
                    .finishStackTrace());
  }

  private void primeCacheForBlockAtSlot(
      final MinimalBeaconBlockSummary headBlock, final UInt64 firstSlotOfEpoch) {
    recentChainData
        .retrieveBlockState(new SlotAndBlockRoot(firstSlotOfEpoch, headBlock.getRoot()))
        .finish(
            maybeState -> maybeState.ifPresent(this::primeEpochStateCaches),
            error -> LOG.warn("Failed to precompute epoch transition", error));
  }

  private boolean isWithinOneEpochOfHeadBlock(
      final UInt64 firstSlot, final MinimalBeaconBlockSummary block) {
    return block.getSlot().plus(spec.getSlotsPerEpoch(firstSlot)).isGreaterThanOrEqualTo(firstSlot);
  }

  private boolean isAfterHeadBlockEpoch(
      final UInt64 epoch, final MinimalBeaconBlockSummary headBlock) {
    return spec.computeEpochAtSlot(headBlock.getSlot()).isLessThan(epoch);
  }

  private void primeEpochStateCaches(final BeaconState state) {
    primeJustifiedState(state.getCurrentJustifiedCheckpoint());
    UInt64.range(state.getSlot(), state.getSlot().plus(spec.getSlotsPerEpoch(state.getSlot())))
        .forEach(
            slot -> {
              final BeaconStateUtil beaconStateUtil = spec.getBeaconStateUtil(state.getSlot());
              // Calculate all proposers
              spec.getBeaconProposerIndex(state, slot);

              // Calculate attesters total effective balance
              beaconStateUtil.getAttestersTotalEffectiveBalance(state, slot);
            });

    // Calculate committees for furthest future epoch that can be calculated from this state
    // (assume earlier epochs were already requested)
    final UInt64 stateEpoch = spec.getCurrentEpoch(state);
    final UInt64 lookaheadEpoch =
        stateEpoch.plus(spec.getSpecConfig(stateEpoch).getMinSeedLookahead());
    final UInt64 lookAheadEpochStartSlot = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    UInt64.range(lookAheadEpochStartSlot, spec.computeStartSlotAtEpoch(lookaheadEpoch.plus(1)))
        // Note: calculating the committeesSize for a slot also calculates the committees
        .forEach(slot -> spec.getBeaconCommitteesSize(state, slot));
  }

  private void primeJustifiedState(final Checkpoint justifiedCheckpoint) {
    recentChainData
        .retrieveCheckpointState(justifiedCheckpoint)
        .thenAccept(
            maybeJustifiedState -> {
              if (maybeJustifiedState.isEmpty()) {
                return;
              }
              final BeaconState justifiedState = maybeJustifiedState.get();
              spec.getBeaconStateUtil(justifiedState.getSlot())
                  .getEffectiveActiveUnslashedBalances(justifiedState);
            })
        .finish(error -> LOG.warn("Failed to prime justified state caches"));
  }
}

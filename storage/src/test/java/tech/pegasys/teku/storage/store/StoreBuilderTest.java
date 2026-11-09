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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.GloasForkChoiceRebuildData;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

class StoreBuilderTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void forkChoiceStoreBuilder_shouldUseAnchorBlockSlotAndStateRootForNonTransitionedState()
      throws Exception {
    chainBuilder.generateGenesis();
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(6));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(spec, anchorBlockAndState);

    assertThat(anchor.getState().getSlot()).isEqualTo(anchor.getBlockSlot());

    assertStoreRebuiltFromOnDiskStoreDataUsesBlockSlot(anchor);
  }

  @Test
  void forkChoiceStoreBuilder_shouldUseAnchorBlockSlotAndStateRootForTransitionedState()
      throws Exception {
    chainBuilder.generateGenesis();
    final UInt64 checkpointStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final UInt64 blockSlotBeforeCheckpointBoundary = checkpointStartSlot.minus(2);
    final SignedBlockAndState anchorBlock =
        chainBuilder.generateBlockAtSlot(blockSlotBeforeCheckpointBoundary);
    final BeaconState anchorState = spec.processSlots(anchorBlock.getState(), checkpointStartSlot);
    final Checkpoint checkpoint =
        new Checkpoint(spec.computeNextEpochBoundary(anchorState.getSlot()), anchorBlock.getRoot());
    final AnchorPoint anchor =
        AnchorPoint.create(spec, checkpoint, anchorState, Optional.of(anchorBlock.getBlock()));

    assertThat(anchorBlock.getSlot()).isLessThan(checkpointStartSlot.minus(1));
    assertThat(anchorState.getSlot()).isEqualTo(checkpointStartSlot);
    assertThat(anchorState.getSlot()).isNotEqualTo(anchorBlock.getSlot());

    assertStoreRebuiltFromOnDiskStoreDataUsesBlockSlot(anchor);
  }

  private void assertStoreRebuiltFromOnDiskStoreDataUsesBlockSlot(final AnchorPoint anchor) {
    final UInt64 expectedBlockTime =
        spec.computeTimeAtSlot(anchor.getBlockSlot(), anchor.getState().getGenesisTime());
    final OnDiskStoreData storeData =
        StoreBuilder.forkChoiceStoreBuilder(spec, anchor, UInt64.ZERO);
    final UpdatableStore store =
        StoreBuilder.create()
            .onDiskStoreData(storeData)
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(BlockProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .earliestBlobSidecarSlotProvider(EarliestBlobSidecarSlotProvider.NOOP)
            .build();

    assertThat(store.getTimeSeconds()).isEqualTo(expectedBlockTime);
    assertThat(store.getForkChoiceStrategy().blockSlot(anchor.getRoot()))
        .contains(anchor.getBlockSlot());
    assertThat(store.getForkChoiceStrategy().getAncestor(anchor.getRoot(), anchor.getBlockSlot()))
        .contains(anchor.getRoot());
    assertThat(
            store
                .getForkChoiceStrategy()
                .getAncestor(anchor.getRoot(), anchor.getState().getSlot()))
        .contains(anchor.getRoot());
  }

  @Test
  void build_shouldPreserveParentBidHashWhenStartingFromCheckpoint() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder gloasChainBuilder = ChainBuilder.create(gloasSpec);
    gloasChainBuilder.generateGenesis();
    gloasChainBuilder.generateBlocksUpToSlot(gloasSpec.computeStartSlotAtEpoch(UInt64.ONE));
    final Checkpoint finalizedCheckpoint =
        gloasChainBuilder.getCurrentCheckpointForEpoch(UInt64.ONE);
    final SignedBlockAndState finalizedBlockAndState =
        gloasChainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();
    final BeaconState finalizedState = finalizedBlockAndState.getState();
    final ExecutionPayloadBid latestBid =
        finalizedState
            .toVersionGloas()
            .map(BeaconStateGloas::getLatestExecutionPayloadBid)
            .orElseThrow();
    final AnchorPoint stateOnlyAnchor =
        AnchorPoint.create(gloasSpec, finalizedCheckpoint, finalizedState, Optional.empty());
    final StoredBlockMetadata stateOnlyAnchorMetadata =
        StoredBlockMetadata.fromBlockAndState(
            gloasSpec, StateAndBlockSummary.create(finalizedState));
    assertThat(latestBid.getParentBlockHash()).isNotEqualTo(latestBid.getBlockHash());
    assertThat(stateOnlyAnchor.getSignedBeaconBlock()).isEmpty();
    assertThat(stateOnlyAnchorMetadata.getExecutionBlockHash()).contains(latestBid.getBlockHash());
    assertThat(stateOnlyAnchorMetadata.getGloasForkChoiceRebuildData())
        .map(GloasForkChoiceRebuildData::payloadParentBlockHash)
        .contains(latestBid.getParentBlockHash());
    final UInt64 time =
        gloasSpec.computeTimeAtSlot(finalizedState.getSlot(), finalizedState.getGenesisTime());
    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(gloasSpec)
            .blockProvider(BlockProvider.NOOP)
            .earliestBlobSidecarSlotProvider(EarliestBlobSidecarSlotProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .anchor(Optional.of(finalizedCheckpoint))
            .genesisTime(finalizedState.getGenesisTime())
            .time(time)
            .latestFinalized(stateOnlyAnchor)
            .justifiedCheckpoint(finalizedCheckpoint)
            .bestJustifiedCheckpoint(finalizedCheckpoint)
            .blockInformation(Map.of(finalizedCheckpoint.getRoot(), stateOnlyAnchorMetadata))
            .storeConfig(StoreConfig.createDefault())
            .votes(Map.of())
            .latestCanonicalBlockRoot(Optional.empty())
            .build();

    final ForkChoiceState forkChoiceState =
        store
            .getForkChoiceStrategy()
            .getForkChoiceState(
                Optional.empty(),
                finalizedCheckpoint.getEpoch(),
                finalizedCheckpoint,
                finalizedCheckpoint,
                Optional.empty());

    assertThat(forkChoiceState.safeExecutionBlockHash()).isEqualTo(latestBid.getParentBlockHash());
    assertThat(forkChoiceState.finalizedExecutionBlockHash())
        .isEqualTo(latestBid.getParentBlockHash());
  }
}

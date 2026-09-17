/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.protoarray;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ForkChoiceStrategyTest extends AbstractBlockMetadataStoreTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Override
  protected BlockMetadataStore createBlockMetadataStore(
      final ChainBuilder chainBuilder, final ChainBuilder... additionalBuilders) {
    final BeaconState latestState = chainBuilder.getLatestBlockAndState().getState();
    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(spec)
            .currentEpoch(ZERO)
            .finalizedCheckpoint(latestState.getFinalizedCheckpoint())
            .justifiedCheckpoint(latestState.getCurrentJustifiedCheckpoint())
            .build();
    addBlocksFromBuilder(chainBuilder, protoArray);

    for (ChainBuilder builder : additionalBuilders) {
      addBlocksFromBuilder(builder, protoArray);
    }
    return ForkChoiceStrategy.initialize(spec, protoArray);
  }

  private void addBlocksFromBuilder(final ChainBuilder chainBuilder, final ProtoArray protoArray) {
    chainBuilder
        .streamBlocksAndStates()
        .filter(block -> !protoArray.containsNode(ForkChoiceNode.createBase(block.getRoot())))
        .forEach(blockAndState -> addBlockToProtoArray(protoArray, blockAndState));
  }

  @Test
  void processAllBeaconBlocksInOrder_shouldExcludeInternalPayloadChildNodes() {
    final InternalPayloadTraversalFixture fixture = createInternalPayloadTraversalFixture();
    final List<Bytes32> visitedRoots = new ArrayList<>();

    fixture
        .strategy()
        .processAllBeaconBlocksInOrder((root, slot, parent) -> visitedRoots.add(root));

    assertThat(visitedRoots)
        .containsExactly(
            fixture.genesis().getRoot(), fixture.block1().getRoot(), fixture.block2().getRoot());
  }

  @Test
  void processBeaconBlockChain_shouldSkipInternalPayloadChildNodes() {
    final InternalPayloadTraversalFixture fixture = createInternalPayloadTraversalFixture();
    final List<Bytes32> visitedRoots = new ArrayList<>();

    fixture
        .strategy()
        .processBeaconBlockChain(
            fixture.block2().getRoot(), (root, slot, parent) -> visitedRoots.add(root));

    assertThat(visitedRoots)
        .containsExactly(
            fixture.block2().getRoot(), fixture.block1().getRoot(), fixture.genesis().getRoot());
  }

  @Test
  void onPayloadExecution_shouldNotPenalizeNodeOnFailedExecution() {
    final ProtoArray protoArray = mock(ProtoArray.class);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    forkChoiceStrategy.onExecutionPayloadResult(
        dataStructureUtil.randomBytes32(), PayloadStatus.failedExecution(new Error()), true);
    verify(protoArray, never()).markNodeInvalid(any(), any(), any());
    verify(protoArray, never()).markNodeValid(any());
  }

  @Test
  void onPayloadExecution_shouldPenalizeNodeOnInvalidExecutionWithoutTransition() {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final ProtoArray protoArray = spy(createProtoArray(genesis.getState()));
    addBlockToProtoArray(protoArray, genesis);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    forkChoiceStrategy.onExecutionPayloadResult(
        genesis.getRoot(),
        PayloadStatus.invalid(Optional.of(dataStructureUtil.randomBytes32()), Optional.empty()),
        false);
    verify(protoArray, times(1)).markParentChainInvalid(any(), any(), any());
    verify(protoArray, never()).markNodeValid(any());
  }

  @Test
  void shouldExtendPayload_shouldReturnFalseForPreGloasBlock() {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final SignedBeaconBlock genesisBlock = chainBuilder.generateGenesis().getBlock();
    final ForkChoiceStrategy forkChoiceStrategy =
        ForkChoiceStrategy.initialize(
            spec, createProtoArray(chainBuilder.getLatestBlockAndState().getState()));
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    // if pre-gloas returns true, the gloas transition breaks, so instead we return false pre-gloas
    assertThat(
            forkChoiceStrategy.shouldExtendPayload(
                store, new SlotAndBlockRoot(genesisBlock.getSlot(), genesisBlock.getRoot())))
        .isFalse();
  }

  @Test
  void shouldExtendPayload_shouldNotLookUpBlockByRootToSelectForkChoiceModel() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final ForkChoiceStrategy forkChoiceStrategy =
        ForkChoiceStrategy.initialize(
            gloasSpec, createProtoArray(gloasSpec, chainBuilder.generateGenesis().getState()));
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    assertThat(forkChoiceStrategy.shouldExtendPayload(store, new SlotAndBlockRoot(ONE, blockRoot)))
        .isFalse();

    verify(store, never()).getBlockIfAvailable(blockRoot);
  }

  @Test
  void shouldBuildOnFull_shouldReturnFalseWhenPtcVotesDataUnavailable() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final int threshold =
        SpecConfigGloas.required(fixture.spec().atSlot(fixture.block().getSlot()).getConfig())
            .getDataAvailabilityTimelyThreshold();

    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isTrue();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(blockRoot, ptcPositions(threshold), true, false);
    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isTrue();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(threshold), true, false);
    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isFalse();
    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createEmpty(blockRoot))).isFalse();
  }

  @Test
  void getSupportedNode_shouldResolveGloasVoteVariant() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final UInt64 blockSlot = fixture.block().getSlot();
    final UInt64 laterSlot = blockSlot.plus(ONE);

    assertThat(fixture.strategy().getSupportedNode(laterSlot, blockRoot, blockSlot, true))
        .contains(ForkChoiceNode.createBase(blockRoot));
    assertThat(fixture.strategy().getSupportedNode(laterSlot, blockRoot, laterSlot, false))
        .contains(ForkChoiceNode.createEmpty(blockRoot));
    assertThat(fixture.strategy().getSupportedNode(laterSlot, blockRoot, laterSlot, true))
        .contains(ForkChoiceNode.createFull(blockRoot));
  }

  @Test
  void shouldBuildOnFull_shouldReturnFalseWhenPtcVotesPayloadUntimely() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final int threshold =
        SpecConfigGloas.required(fixture.spec().atSlot(fixture.block().getSlot()).getConfig())
            .getPayloadTimelyThreshold();

    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isTrue();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(blockRoot, ptcPositions(threshold), false, true);
    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isTrue();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(threshold), false, true);
    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isFalse();
  }

  @Test
  void shouldBuildOnFull_shouldIgnorePtcVotesWhenHeadIsOlderThanPreviousSlot() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final int threshold =
        SpecConfigGloas.required(fixture.spec().atSlot(fixture.block().getSlot()).getConfig())
            .getDataAvailabilityTimelyThreshold();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(blockRoot, ptcPositions(threshold + 1), true, false);

    assertThat(shouldBuildOnFull(fixture, UInt64.valueOf(3), ForkChoiceNode.createFull(blockRoot)))
        .isTrue();
  }

  @Test
  void shouldBuildOnFull_shouldRejectPendingHead() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();

    assertThatThrownBy(() -> shouldBuildOnFull(fixture, ForkChoiceNode.createBase(blockRoot)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-pending head");
  }

  @Test
  void shouldBuildOnFull_shouldReturnFalseForUnvalidatedFullNode() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture(true, false);
    final Bytes32 blockRoot = fixture.block().getRoot();

    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isFalse();
  }

  @Test
  void shouldBuildOnFull_shouldReturnFalseWhenFullNodeIsMissing() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture(false, false);
    final Bytes32 blockRoot = fixture.block().getRoot();

    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isFalse();
  }

  @Test
  void shouldBuildOnFull_shouldIgnorePtcVotesForOtherRoots() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final Bytes32 otherRoot = dataStructureUtil.randomBytes32();
    final int threshold =
        SpecConfigGloas.required(fixture.spec().atSlot(fixture.block().getSlot()).getConfig())
            .getDataAvailabilityTimelyThreshold();

    fixture
        .strategy()
        .onPayloadTimelinessCommitteeVote(otherRoot, ptcPositions(threshold + 1), true, false);

    assertThat(shouldBuildOnFull(fixture, ForkChoiceNode.createFull(blockRoot))).isTrue();
  }

  @Test
  void getPtcVoteCounts_shouldReturnAggregateCountsForBlockRoot() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();

    fixture.strategy().onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(0, 1), true, true);
    fixture.strategy().onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(2), true, false);
    fixture.strategy().onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(3), false, true);
    fixture.strategy().onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(1), false, false);

    assertThat(fixture.strategy().getPayloadAttesterCount(blockRoot)).isEqualTo(UInt64.valueOf(4));
    assertThat(fixture.strategy().getPayloadAvailabilityYesCount(blockRoot))
        .isEqualTo(UInt64.valueOf(2));
    assertThat(fixture.strategy().getPayloadDataAvailabilityYesCount(blockRoot))
        .isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void getParentBeaconBlockNode_phase0_returnsParentBlockBaseNode() {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(ONE);
    final ProtoArray protoArray = createProtoArray(block.getState());
    addBlockToProtoArray(protoArray, genesis);
    addBlockToProtoArray(protoArray, block);
    final ForkChoiceStrategy strategy = ForkChoiceStrategy.initialize(spec, protoArray);

    assertThat(strategy.getParentBeaconBlockNode(ForkChoiceNode.createBase(block.getRoot())))
        .contains(ForkChoiceNode.createBase(genesis.getRoot()));
  }

  @Test
  void getParentBeaconBlockNode_gloas_skipsSameBlockBaseWhenInputIsFull() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final Bytes32 genesisRoot = fixture.block().getParentRoot();

    // FULL(block) → BASE(block) → parent block's variant.
    assertThat(fixture.strategy().getParentBeaconBlockNode(ForkChoiceNode.createFull(blockRoot)))
        .contains(ForkChoiceNode.createBase(genesisRoot));
  }

  @Test
  void getParentBeaconBlockNode_gloas_walksDirectlyFromBase() {
    final GloasPayloadDecisionFixture fixture = createGloasPayloadDecisionFixture();
    final Bytes32 blockRoot = fixture.block().getRoot();
    final Bytes32 genesisRoot = fixture.block().getParentRoot();

    // BASE(block) → parent block's variant directly (one hop).
    assertThat(fixture.strategy().getParentBeaconBlockNode(ForkChoiceNode.createBase(blockRoot)))
        .contains(ForkChoiceNode.createBase(genesisRoot));
  }

  @Test
  public void findHead_worksForChainInitializedFromNonGenesisAnchor() {
    // Set up store with an anchor point that has justified and finalized checkpoints prior to its
    // epoch
    final UInt64 initialEpoch = UInt64.valueOf(100);
    final BeaconState anchorState =
        dataStructureUtil
            .stateBuilderPhase0()
            .setJustifiedCheckpointsToEpoch(initialEpoch.minus(2))
            .setFinalizedCheckpointToEpoch(initialEpoch.minus(3))
            .setSlotToStartOfEpoch(initialEpoch)
            .build();
    AnchorPoint anchor = dataStructureUtil.createAnchorFromState(anchorState);
    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(spec)
            .initialCheckpoint(Optional.of(anchor.getCheckpoint()))
            .currentEpoch(anchor.getEpoch())
            .justifiedCheckpoint(anchorState.getCurrentJustifiedCheckpoint())
            .finalizedCheckpoint(anchorState.getFinalizedCheckpoint())
            .build();
    protoArray.addNode(
        ForkChoiceNode.createBase(anchor.getRoot()),
        anchor.getBlockSlot(),
        anchor.getParentRoot(),
        Optional.empty(),
        anchor.getStateRoot(),
        new BlockCheckpoints(
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            anchor.getCheckpoint()),
        ZERO,
        Bytes32.ZERO,
        false);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    TestStoreImpl store = new TestStoreFactory().createAnchorStore(anchor);

    assertThat(forkChoiceStrategy.getTotalTrackedNodeCount()).isEqualTo(1);
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(anchor.getState().getSlot())
            .getEffectiveActiveUnslashedBalances(anchor.getState());
    final UInt64 currentSlot = spec.getCurrentSlot(store);
    final SlotAndForkChoiceNode head =
        forkChoiceStrategy.applyPendingVotes(
            store,
            Optional.empty(),
            currentSlot,
            currentSlot,
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            effectiveBalances,
            ZERO);
    assertThat(head.node().blockRoot()).isEqualTo(anchor.getRoot());
  }

  @Test
  void getAncestor_specifiedBlockIsAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(block.getRoot(), block.getSlot()))
        .contains(block.getRoot());
  }

  @Test
  void getBlockRootsAtSlot_shouldReturnAllRoots() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().addNewBestBlock();
    ChainBuilder fork = storageSystem.chainBuilder().fork();
    final SignedBlockAndState bestBlock = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState forkBlock =
        fork.generateBlockAtSlot(
            bestBlock.getSlot(),
            ChainBuilder.BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    strategy.applyUpdate(
        List.of(
            BlockAndCheckpoints.fromBlockAndState(spec, bestBlock),
            BlockAndCheckpoints.fromBlockAndState(spec, forkBlock)),
        emptyMap(),
        emptySet(),
        emptyMap(),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());
    assertThat(strategy.getBlockRootsAtSlot(bestBlock.getSlot()))
        .containsExactlyInAnyOrder(bestBlock.getRoot(), forkBlock.getRoot());
  }

  @Test
  void getAncestor_ancestorIsFound() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChain(1);
    final SignedBlockAndState ancestor = storageSystem.chainUpdater().advanceChain(2);
    storageSystem.chainUpdater().advanceChain(3);
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ancestor.getSlot()))
        .contains(ancestor.getRoot());
  }

  @Test
  void getAncestorNode_returnsBaseNodeForKnownAncestor() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChain(1);
    final SignedBlockAndState ancestor = storageSystem.chainUpdater().advanceChain(2);
    storageSystem.chainUpdater().advanceChain(3);
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    assertThat(
            strategy.getAncestorNode(ForkChoiceNode.createBase(head.getRoot()), ancestor.getSlot()))
        .contains(
            new ForkChoiceNode(ancestor.getRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
  }

  @Test
  void getChainHeads() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getChainHeads())
        .isEqualTo(
            List.of(
                new ProtoNodeData(
                    head.getSlot(),
                    head.getRoot(),
                    head.getParentRoot(),
                    head.getStateRoot(),
                    head.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
                    head.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
                    ZERO,
                    ProtoNodeValidationStatus.VALID,
                    spec.calculateBlockCheckpoints(head.getState()),
                    ZERO,
                    ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING)));
  }

  @Test
  void getAncestor_headIsUnknown() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(dataStructureUtil.randomBytes32(), ZERO)).isEmpty();
  }

  @Test
  void getAncestor_noBlockAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ONE)).contains(head.getParentRoot());
  }

  @Test
  void applyTransaction_shouldNotContainRemovedBlocks() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();

    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    strategy.applyUpdate(
        emptyList(),
        emptyMap(),
        emptySet(),
        Map.of(block2.getRoot(), block2.getSlot()),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());

    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isFalse();
  }

  @Test
  void applyTransaction_shouldAddNewBlocks() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();

    strategy.applyUpdate(
        List.of(
            BlockAndCheckpoints.fromBlockAndState(spec, block1),
            BlockAndCheckpoints.fromBlockAndState(spec, block2)),
        emptyMap(),
        emptySet(),
        emptyMap(),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());

    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isTrue();
  }

  @Test
  void applyTransaction_shouldPruneWhenFinalizedCheckpointExceedsPruningThreshold() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block3 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block4 = storageSystem.chainUpdater().addNewBestBlock();

    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    // Genesis = 0, block1 = 1, block2 = 2, block3 = 3, block4 = 4
    strategy.setPruneThreshold(3);

    // Not pruned because threshold isn't reached.
    strategy.applyUpdate(
        emptyList(), emptyMap(), emptySet(), emptyMap(), new Checkpoint(ONE, block2.getRoot()));
    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isTrue();
    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    // Prune when threshold is exceeded
    strategy.applyUpdate(
        emptyList(), emptyMap(), emptySet(), emptyMap(), new Checkpoint(ONE, block3.getRoot()));
    assertThat(strategy.contains(block1.getRoot())).isFalse();
    assertThat(strategy.contains(block2.getRoot())).isFalse();
    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();
  }

  @Test
  void applyUpdate_shouldPruneBlocksPriorToLatestFinalized() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChainUntil(10);
    final ForkChoiceStrategy forkChoiceStrategy = getProtoArray(storageSystem);

    final SignedBeaconBlock finalizedBlock = storageSystem.chainBuilder().getBlockAtSlot(4);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ONE, finalizedBlock.getRoot());
    forkChoiceStrategy.setPruneThreshold(0);
    forkChoiceStrategy.applyUpdate(
        emptyList(), emptyMap(), emptySet(), emptyMap(), finalizedCheckpoint);

    // Check that all blocks prior to latest finalized have been pruned
    final List<SignedBlockAndState> allBlocks =
        storageSystem.chainBuilder().streamBlocksAndStates().toList();
    for (SignedBlockAndState block : allBlocks) {
      if (block.getSlot().isLessThan(finalizedBlock.getSlot())) {
        assertThat(forkChoiceStrategy.contains(block.getRoot())).isFalse();
        assertThat(forkChoiceStrategy.blockSlot(block.getRoot())).isEmpty();
        assertThat(forkChoiceStrategy.blockParentRoot(block.getRoot())).isEmpty();
      } else {
        assertThat(forkChoiceStrategy.contains(block.getRoot())).isTrue();
        assertThat(forkChoiceStrategy.blockSlot(block.getRoot())).contains(block.getSlot());
        assertThat(forkChoiceStrategy.blockParentRoot(block.getRoot()))
            .contains(block.getParentRoot());
      }
    }
  }

  @Test
  void applyUpdate_shouldAttachDescendantToFinalizedBoundaryFullNode() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();

    assertThat(
            BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())
                .getExecutionBlockHash())
        .contains(
            fixture
                .boundaryExecutionPayload()
                .executionPayload()
                .getMessage()
                .getPayload()
                .getBlockHash());

    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            Map.of(fixture.boundary().getRoot(), fixture.boundaryExecutionPayload()),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(fixture.boundaryBlockAndCheckpoints()));

    final Optional<Integer> boundaryFullNodeIndex =
        fixture.protoArray().getNodeIndex(ForkChoiceNode.createFull(fixture.boundary().getRoot()));
    assertThat(boundaryFullNodeIndex).isPresent();
    assertThat(boundaryBaseNode(fixture).getParentIndex()).isEmpty();
    assertThat(childBaseNode(fixture).getParentIndex()).isEqualTo(boundaryFullNodeIndex);
  }

  @Test
  void applyUpdate_shouldAttachDescendantToFinalizedBoundaryEmptyNodeWhenPayloadIsUnavailable() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();

    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            emptyMap(),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(fixture.boundaryBlockAndCheckpoints()));

    final Optional<Integer> boundaryEmptyNodeIndex =
        fixture.protoArray().getNodeIndex(ForkChoiceNode.createEmpty(fixture.boundary().getRoot()));
    assertThat(boundaryEmptyNodeIndex).isPresent();
    assertThat(
            fixture
                .protoArray()
                .getNodeIndex(ForkChoiceNode.createFull(fixture.boundary().getRoot())))
        .isEmpty();
    assertThat(boundaryBaseNode(fixture).getParentIndex()).isEmpty();
    assertThat(childBaseNode(fixture).getParentIndex()).isEqualTo(boundaryEmptyNodeIndex);
  }

  @Test
  void applyUpdate_shouldSeedFinalizedBoundaryEmptyExecutionContext() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();
    final UInt64 boundaryBlockNumber = UInt64.valueOf(123);
    final UInt64 boundaryGasLimit = UInt64.valueOf(60_000_000);
    final BlockAndCheckpoints boundaryBlockAndCheckpoints =
        new BlockAndCheckpoints(
            fixture.boundary().getBlock(),
            fixture.boundaryBlockAndCheckpoints().getBlockCheckpoints(),
            Optional.of(boundaryBlockNumber),
            Optional.of(boundaryGasLimit));

    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            emptyMap(),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(boundaryBlockAndCheckpoints));

    assertThat(childBaseNode(fixture).getExecutionBlockNumber()).isEqualTo(boundaryBlockNumber);
    assertThat(childBaseNode(fixture).getExecutionGasLimit()).isEqualTo(boundaryGasLimit);
  }

  @Test
  void applyUpdate_shouldPruneToFinalizedBoundaryAnchorInsertedIntoNonEmptyProtoArray() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();
    fixture.protoArray().setPruneThreshold(0);

    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            emptyMap(),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(fixture.boundaryBlockAndCheckpoints()));

    assertThat(fixture.protoArray().getNode(ForkChoiceNode.createBase(fixture.genesis().getRoot())))
        .isEmpty();
    assertThat(fixture.protoArray().getNodes().get(0).getForkChoiceNode())
        .isEqualTo(ForkChoiceNode.createBase(fixture.boundary().getRoot()));
    assertThat(
            fixture
                .protoArray()
                .getNodeIndex(ForkChoiceNode.createBase(fixture.boundary().getRoot())))
        .contains(0);
    assertThat(boundaryBaseNode(fixture).getParentIndex()).isEmpty();
    assertThat(boundaryBaseNode(fixture).getParentRoot())
        .isEqualTo(fixture.boundary().getParentRoot());
    assertProtoArrayParentIndicesAreValid(fixture.protoArray());
  }

  @Test
  void applyUpdate_shouldRejectNormalGloasBlockWithMissingParentVariants() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState child = chainBuilder.generateBlockAtSlot(2);
    final ProtoArray protoArray = createProtoArray(gloasSpec, genesis.getState());
    addBlockToProtoArray(gloasSpec, protoArray, genesis);
    final ForkChoiceStrategy strategy = ForkChoiceStrategy.initialize(gloasSpec, protoArray);

    assertThatThrownBy(
            () ->
                strategy.applyUpdate(
                    List.of(BlockAndCheckpoints.fromBlockAndState(gloasSpec, child)),
                    emptyMap(),
                    emptySet(),
                    emptyMap(),
                    new Checkpoint(ZERO, genesis.getRoot())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing GLOAS parent variants");
  }

  @Test
  void applyUpdate_shouldAcceptPreGloasBlockWithMissingParentVariants() {
    final Spec preGloasSpec = TestSpecFactory.createMinimalBellatrix();
    final ChainBuilder chainBuilder = ChainBuilder.create(preGloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState child = chainBuilder.generateBlockAtSlot(2);
    final ProtoArray protoArray = createProtoArray(preGloasSpec, genesis.getState());
    addBlockToProtoArray(preGloasSpec, protoArray, genesis);
    final ForkChoiceStrategy strategy = ForkChoiceStrategy.initialize(preGloasSpec, protoArray);

    strategy.applyUpdate(
        List.of(BlockAndCheckpoints.fromBlockAndState(preGloasSpec, child)),
        emptyMap(),
        emptySet(),
        emptyMap(),
        new Checkpoint(ZERO, genesis.getRoot()));

    assertThat(strategy.contains(child.getRoot())).isTrue();
    assertThat(
            protoArray
                .getNode(ForkChoiceNode.createBase(child.getRoot()))
                .orElseThrow()
                .getParentIndex())
        .isEmpty();
  }

  @Test
  void getExecutionContextForBlockRootAndHash_gloasShouldResolveFullAndEmptyContexts() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalGloas());
    final SignedBlockAndState parentBlock = storageSystem.chainUpdater().advanceChain(1);
    final SignedBlockAndState block = storageSystem.chainUpdater().advanceChain(2);

    final Bytes32 fullParentBlockHash =
        block
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage()
            .getBlockHash();
    final UInt64 fullParentGasLimit =
        block
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage()
            .getGasLimit();
    final Bytes32 emptyParentBlockHash =
        BeaconStateGloas.required(block.getState()).getLatestBlockHash();
    final UInt64 emptyParentGasLimit =
        parentBlock
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage()
            .getGasLimit();

    final ReadOnlyForkChoiceStrategy strategy =
        storageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();

    assertThat(fullParentBlockHash).isNotEqualTo(emptyParentBlockHash);
    assertThat(
            strategy.getExecutionGasLimitForBlockRootAndHash(block.getRoot(), fullParentBlockHash))
        .contains(fullParentGasLimit);
    assertThat(
            strategy.getExecutionGasLimitForBlockRootAndHash(block.getRoot(), emptyParentBlockHash))
        .contains(emptyParentGasLimit);
    assertThat(
            strategy.getExecutionGasLimitForBlockRootAndHash(
                block.getRoot(), dataStructureUtil.randomBytes32()))
        .isEmpty();

    final StorageSystem restartedStorageSystem = storageSystem.restarted();

    final ReadOnlyForkChoiceStrategy rebuiltStrategy =
        restartedStorageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();
    assertThat(
            rebuiltStrategy.getExecutionGasLimitForBlockRootAndHash(
                block.getRoot(), fullParentBlockHash))
        .contains(fullParentGasLimit);
    assertThat(
            rebuiltStrategy.getExecutionGasLimitForBlockRootAndHash(
                block.getRoot(), emptyParentBlockHash))
        .contains(emptyParentGasLimit);
  }

  @Test
  void getExecutionContextForBlockRootAndHash_shouldPreserveGasAcrossGloasForkBoundary() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE);
    final StorageSystem storageSystem = initStorageSystem(transitionSpec);
    final UInt64 gloasForkSlot = transitionSpec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState preGloasParent =
        storageSystem.chainUpdater().advanceChain(gloasForkSlot.minusMinZero(1));
    storageSystem.chainUpdater().advanceChain(gloasForkSlot);
    final var parentPayload =
        preGloasParent
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalExecutionPayload()
            .orElseThrow();

    assertExecutionGasLimit(
        storageSystem, preGloasParent, parentPayload.getBlockHash(), parentPayload.getGasLimit());

    assertExecutionGasLimit(
        storageSystem.restarted(),
        preGloasParent,
        parentPayload.getBlockHash(),
        parentPayload.getGasLimit());
  }

  @Test
  void getExecutionContextForBlockRootAndHash_shouldSurviveGloasFinalizationAndRestart() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final StorageSystem storageSystem = initStorageSystem(gloasSpec);
    storageSystem
        .chainUpdater()
        .advanceChainUntil(gloasSpec.computeStartSlotAtEpoch(UInt64.valueOf(2)));
    final SignedBlockAndState finalizedBlock =
        storageSystem.chainBuilder().getLatestBlockAndStateAtEpochBoundary(UInt64.ONE);
    final var finalizedBid =
        finalizedBlock
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage();
    final ReadOnlyForkChoiceStrategy strategy =
        storageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();
    final UInt64 emptyGasLimit =
        strategy
            .getExecutionGasLimitForBlockRootAndHash(
                finalizedBlock.getRoot(), finalizedBid.getParentBlockHash())
            .orElseThrow();
    final UInt64 fullGasLimit =
        strategy
            .getExecutionGasLimitForBlockRootAndHash(
                finalizedBlock.getRoot(), finalizedBid.getBlockHash())
            .orElseThrow();

    storageSystem.chainUpdater().finalizeEpoch(UInt64.ONE);

    assertExecutionGasLimit(
        storageSystem, finalizedBlock, finalizedBid.getParentBlockHash(), emptyGasLimit);
    assertExecutionGasLimit(
        storageSystem, finalizedBlock, finalizedBid.getBlockHash(), fullGasLimit);

    final StorageSystem restartedStorageSystem = storageSystem.restarted();

    assertExecutionGasLimit(
        restartedStorageSystem, finalizedBlock, finalizedBid.getParentBlockHash(), emptyGasLimit);
    assertExecutionGasLimit(
        restartedStorageSystem, finalizedBlock, finalizedBid.getBlockHash(), fullGasLimit);
  }

  @Test
  void applyScoreChanges_shouldWorkAfterRemovingNodes() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block3 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block4 = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    final UInt64 block3Epoch = spec.computeEpochAtSlot(block3.getSlot());

    strategy.applyUpdate(
        emptyList(),
        emptyMap(),
        emptySet(),
        Map.of(block1.getRoot(), block1.getSlot(), block2.getRoot(), block2.getSlot()),
        new Checkpoint(ONE, block3.getRoot()));

    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    final VoteUpdater transaction = storageSystem.recentChainData().startVoteUpdate();
    strategy.processAttestation(transaction, ZERO, block3.getRoot(), block3Epoch);

    final BeaconState block3State = block3.getState();

    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block3State.getSlot())
            .getEffectiveActiveUnslashedBalances(block3State);
    final SlotAndForkChoiceNode bestHead =
        strategy.applyPendingVotes(
            transaction,
            Optional.empty(),
            storageSystem.recentChainData().getCurrentSlot().orElseThrow(),
            storageSystem.recentChainData().getCurrentEpoch().orElseThrow(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO);
    transaction.commit();

    assertThat(bestHead.node().blockRoot()).isEqualTo(block4.getRoot());
  }

  @Test
  void executionBlockNumber_shouldBeEmptyForUnknownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    assertThat(strategy.executionBlockNumber(Bytes32.ZERO)).isEmpty();
  }

  @Test
  void executionBlockNumber_shouldGetExecutionBlockNumberForKnownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final SignedBlockAndState block1 =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(1, BlockOptions.create().setTransactions());
    storageSystem.chainUpdater().saveBlock(block1);
    assertThat(block1.getExecutionBlockNumber()).isNotEmpty();

    final ReadOnlyForkChoiceStrategy strategy =
        storageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();
    assertThat(strategy.executionBlockNumber(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockNumber());

    storageSystem.restarted();
    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .executionBlockNumber(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockNumber());
  }

  @Test
  void executionBlockHash_shouldBeEmptyForUnknownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    assertThat(strategy.executionBlockHash(Bytes32.ZERO)).isEmpty();
  }

  @Test
  void executionBlockHash_shouldGetExecutionRootForKnownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final SignedBlockAndState block1 =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(1, BlockOptions.create().setTransactions());
    storageSystem.chainUpdater().saveBlock(block1);
    assertThat(block1.getExecutionBlockHash()).isNotEmpty();

    final ReadOnlyForkChoiceStrategy strategy =
        storageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();
    assertThat(strategy.executionBlockHash(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockHash());

    storageSystem.restarted();
    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .executionBlockHash(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockHash());
  }

  @Test
  void applyPendingVotes_shouldMarkEquivocation() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final VoteUpdater transaction1 = storageSystem.recentChainData().startVoteUpdate();
    final UInt64 block1Epoch = spec.computeEpochAtSlot(block1.getSlot());
    strategy.processAttestation(transaction1, ZERO, block1.getRoot(), block1Epoch);
    transaction1.commit();

    // Mark our Validator as going to be equivocated like when AttesterSlashing received
    final VoteUpdater transaction2 = storageSystem.recentChainData().startVoteUpdate();
    VoteTracker voteTracker = transaction2.getVote(ZERO);
    transaction2.putVote(ZERO, voteTracker.createNextEquivocating());
    transaction2.commit();

    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final VoteUpdater transaction3 = storageSystem.recentChainData().startVoteUpdate();
    final UInt64 block2Epoch = spec.computeEpochAtSlot(block2.getSlot());
    strategy.processAttestation(transaction3, ZERO, block2.getRoot(), block2Epoch);

    final BeaconState block2State = block2.getState();
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block2State.getSlot())
            .getEffectiveActiveUnslashedBalances(block2State);
    final SlotAndForkChoiceNode bestHead =
        strategy.applyPendingVotes(
            transaction3,
            Optional.empty(),
            storageSystem.recentChainData().getCurrentSlot().orElseThrow(),
            storageSystem.recentChainData().getCurrentEpoch().orElseThrow(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO);
    transaction3.commit();
    assertThat(bestHead.node().blockRoot()).isEqualTo(block2.getRoot());

    assertThat(transaction3.getVote(ZERO).isCurrentEquivocating()).isTrue();
    // Not updated after equivocation
    assertThat(transaction3.getVote(ZERO).getNextRoot()).isEqualTo(block1.getRoot());
  }

  @Test
  void shouldConsiderHeadOptimisticWhenItIsNotViable() {
    // If we optimistically import blocks which include enough attestations to update the justified
    // checkpoint, then later invalidate some non-justified blocks such that the head block no
    // longer includes enough attestations to support that justification, then none of our blocks
    // will be viable because they won't have the same checkpoints as our store.
    // In that case we will use the justified checkpoint as chain head and should remain in
    // optimistic sync mode

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    chainUpdater.initializeGenesisWithPayload(
        true, dataStructureUtil.randomExecutionPayloadHeader());
    final ForkChoiceStrategy protoArray = getProtoArray(storageSystem);
    final RecentChainData recentChainData = storageSystem.recentChainData();
    final SignedBlockAndState optimisticHead = chainUpdater.finalizeCurrentChainOptimistically();
    ProtoNodeData firstJustifiedData =
        protoArray.getBlockData(optimisticHead.getRoot()).orElseThrow();
    ProtoNodeData lastUnjustifiedData =
        protoArray.getBlockData(firstJustifiedData.getParentRoot()).orElseThrow();
    final Checkpoint currentJustified = recentChainData.getJustifiedCheckpoint().orElseThrow();
    while (lastUnjustifiedData.getCheckpoints().getJustifiedCheckpoint().equals(currentJustified)) {
      firstJustifiedData = lastUnjustifiedData;
      lastUnjustifiedData =
          protoArray.getBlockData(lastUnjustifiedData.getParentRoot()).orElseThrow();
    }
    assertThat(firstJustifiedData.getCheckpoints().getJustifiedCheckpoint())
        .isEqualTo(currentJustified);
    assertThat(lastUnjustifiedData.getCheckpoints().getJustifiedCheckpoint())
        .isNotEqualTo(currentJustified);

    // Invalidate blocks that are viable for head (all blocks after the justified one)
    final UInt64 justifiedSlot =
        SafeFutureAssert.safeJoin(recentChainData.retrieveBlockByRoot(currentJustified.getRoot()))
            .orElseThrow()
            .getSlot();

    final Bytes32 rootToBeInvalidated =
        recentChainData.getBlockRootInEffectBySlot(justifiedSlot.increment()).orElseThrow();

    protoArray.onExecutionPayloadResult(
        rootToBeInvalidated,
        PayloadStatus.invalid(Optional.empty(), Optional.of("No good, very bad block")),
        true);
    // And validate the justified checkpoint
    protoArray.onExecutionPayloadResult(currentJustified.getRoot(), PayloadStatus.VALID, false);

    // Find new chain head
    final SlotAndForkChoiceNode revertHead =
        protoArray.findHead(
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow());
    recentChainData.updateHead(revertHead.node().blockRoot(), revertHead.slot());

    // Advance current slot so that current head is no more viable
    chainUpdater.setCurrentSlot(UInt64.valueOf(60));

    final ForkChoiceState forkChoiceState =
        protoArray.getForkChoiceState(
            Optional.empty(),
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow(),
            Optional.empty());

    // Should have reverted to the justified checkpoint as head
    assertThat(forkChoiceState.headBlock().blockRoot()).isEqualTo(currentJustified.getRoot());
    // The current head block itself is fully validated
    assertThat(protoArray.isFullyValidated(currentJustified.getRoot())).isTrue();
    // the head is optimistic because it is not viable
    assertThat(forkChoiceState.isHeadOptimistic()).isTrue();
  }

  @Test
  void getForkChoiceState_shouldUseProposingHeadWhenProvided() {
    final InternalPayloadTraversalFixture fixture = createInternalPayloadTraversalFixture();
    final ProtoNodeData fullPayloadBlockData =
        fixture
            .strategy()
            .getBlockData(fixture.block1().getRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)
            .orElseThrow();
    final ChainHead proposingHead =
        ChainHead.create(
            fullPayloadBlockData,
            SafeFuture.completedFuture(StateAndBlockSummary.create(fixture.block1())));
    final UInt64 currentEpoch = spec.computeEpochAtSlot(fixture.block2().getSlot());
    final Checkpoint justifiedCheckpoint =
        fixture.block2().getState().getCurrentJustifiedCheckpoint();
    final Checkpoint finalizedCheckpoint = fixture.block2().getState().getFinalizedCheckpoint();

    final ForkChoiceState proposingForkChoiceState =
        fixture
            .strategy()
            .getForkChoiceState(
                Optional.of(proposingHead),
                currentEpoch,
                justifiedCheckpoint,
                finalizedCheckpoint,
                Optional.empty());

    assertThat(proposingForkChoiceState.headBlock())
        .isEqualTo(ForkChoiceNode.createFull(fixture.block1().getRoot()));
    assertThat(proposingForkChoiceState.headBlockSlot()).isEqualTo(fixture.block1().getSlot());
    assertThat(proposingForkChoiceState.headExecutionBlockHash())
        .isEqualTo(fullPayloadBlockData.getExecutionBlockHash());
  }

  @Test
  void getForkChoiceState_shouldUseGloasBidParentBlockHashForSafeAndFinalizedBlocks() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();
    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            Map.of(fixture.boundary().getRoot(), fixture.boundaryExecutionPayload()),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(fixture.boundaryBlockAndCheckpoints()));
    final Bytes32 boundaryBidParentBlockHash =
        fixture.boundaryBlockAndCheckpoints().getExecutionBlockHash().orElseThrow();
    final Bytes32 boundaryPayloadBlockHash =
        fixture
            .boundaryExecutionPayload()
            .executionPayload()
            .getMessage()
            .getPayload()
            .getBlockHash();
    final Checkpoint justifiedCheckpoint = fixture.finalizedCheckpoint();
    final ForkChoiceState forkChoiceState =
        fixture
            .strategy()
            .getForkChoiceState(
                Optional.empty(),
                fixture.spec().computeEpochAtSlot(fixture.child().getSlot()),
                justifiedCheckpoint,
                fixture.finalizedCheckpoint(),
                Optional.empty());

    assertThat(boundaryBidParentBlockHash).isNotEqualTo(boundaryPayloadBlockHash);
    assertThat(forkChoiceState.safeExecutionBlockHash()).isEqualTo(boundaryBidParentBlockHash);
    assertThat(forkChoiceState.finalizedExecutionBlockHash()).isEqualTo(boundaryBidParentBlockHash);
  }

  @Test
  void getForkChoiceState_shouldUseFastConfirmationConfirmedRootForSafeBlockHash() {
    final GloasBoundaryFixture fixture = createGloasBoundaryFixture();
    fixture
        .strategy()
        .applyUpdate(
            List.of(
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.boundary()),
                BlockAndCheckpoints.fromBlockAndState(fixture.spec(), fixture.child())),
            Map.of(fixture.boundary().getRoot(), fixture.boundaryExecutionPayload()),
            emptySet(),
            emptyMap(),
            fixture.finalizedCheckpoint(),
            Optional.of(fixture.boundaryBlockAndCheckpoints()));

    final Checkpoint justifiedCheckpoint = fixture.finalizedCheckpoint();
    final UInt64 currentEpoch = fixture.spec().computeEpochAtSlot(fixture.child().getSlot());

    // BASE-node execution hash is the get_safe_execution_block_hash source; boundary and child
    // carry distinct hashes (in Gloas each is the block's bid parent_block_hash).
    final Bytes32 boundarySafeHash =
        fixture
            .strategy()
            .getBlockData(fixture.boundary().getRoot())
            .orElseThrow()
            .getExecutionBlockHash();
    final Bytes32 childSafeHash =
        fixture
            .strategy()
            .getBlockData(fixture.child().getRoot())
            .orElseThrow()
            .getExecutionBlockHash();
    assertThat(childSafeHash).isNotEqualTo(boundarySafeHash);

    // Fast confirmation disabled (no confirmed root): safe hash comes from the justified block.
    assertThat(
            fixture
                .strategy()
                .getForkChoiceState(
                    Optional.empty(),
                    currentEpoch,
                    justifiedCheckpoint,
                    fixture.finalizedCheckpoint(),
                    Optional.empty())
                .safeExecutionBlockHash())
        .isEqualTo(boundarySafeHash);

    // Fast confirmation confirming the child: safe hash is get_safe_execution_block_hash of the
    // confirmed (child) block instead of the justified block.
    assertThat(
            fixture
                .strategy()
                .getForkChoiceState(
                    Optional.empty(),
                    currentEpoch,
                    justifiedCheckpoint,
                    fixture.finalizedCheckpoint(),
                    Optional.of(fixture.child().getRoot()))
                .safeExecutionBlockHash())
        .isEqualTo(childSafeHash);

    // If the supplied confirmed root is no longer in protoarray, use the justified block.
    assertThat(
            fixture
                .strategy()
                .getForkChoiceState(
                    Optional.empty(),
                    currentEpoch,
                    justifiedCheckpoint,
                    fixture.finalizedCheckpoint(),
                    Optional.of(Bytes32.random()))
                .safeExecutionBlockHash())
        .isEqualTo(boundarySafeHash);
  }

  private StorageSystem initStorageSystem() {
    return initStorageSystem(spec);
  }

  private StorageSystem initStorageSystem(final Spec spec) {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    return storageSystem;
  }

  private ForkChoiceStrategy getProtoArray(final StorageSystem storageSystem) {
    return storageSystem.recentChainData().getStore().getForkChoiceStrategy();
  }

  private InternalPayloadTraversalFixture createInternalPayloadTraversalFixture() {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);

    final ProtoArray protoArray = createProtoArray(block2.getState());
    addBlockToProtoArray(protoArray, genesis);
    addBlockToProtoArray(protoArray, block1);

    addProjectedNodeToProtoArray(
        protoArray,
        block1,
        ForkChoiceNode.createEmpty(block1.getRoot()),
        ForkChoiceNode.createBase(block1.getRoot()));
    addProjectedNodeToProtoArray(
        protoArray,
        block1,
        ForkChoiceNode.createFull(block1.getRoot()),
        ForkChoiceNode.createBase(block1.getRoot()),
        block1.getExecutionBlockNumber().orElse(UInt64.ONE),
        block1.getExecutionBlockHash().orElse(dataStructureUtil.randomBytes32()));

    addBlockToProtoArray(
        protoArray,
        block2,
        Optional.of(
            protoArray.getNodeIndex(ForkChoiceNode.createFull(block1.getRoot())).orElseThrow()));

    return new InternalPayloadTraversalFixture(
        ForkChoiceStrategy.initialize(spec, protoArray), genesis, block1, block2);
  }

  private ProtoArray createProtoArray(final BeaconState latestState) {
    return createProtoArray(spec, latestState);
  }

  private ProtoArray createProtoArray(final Spec spec, final BeaconState latestState) {
    return ProtoArray.builder()
        .spec(spec)
        .currentEpoch(ZERO)
        .finalizedCheckpoint(latestState.getFinalizedCheckpoint())
        .justifiedCheckpoint(latestState.getCurrentJustifiedCheckpoint())
        .build();
  }

  private void addBlockToProtoArray(
      final ProtoArray protoArray, final SignedBlockAndState blockAndState) {
    addBlockToProtoArray(spec, protoArray, blockAndState);
  }

  private void addBlockToProtoArray(
      final Spec spec, final ProtoArray protoArray, final SignedBlockAndState blockAndState) {
    final ForkChoiceNode nodeIdentity = ForkChoiceNode.createBase(blockAndState.getRoot());
    protoArray.addNode(
        nodeIdentity,
        blockAndState.getSlot(),
        blockAndState.getParentRoot(),
        protoArray.containsNode(ForkChoiceNode.createBase(blockAndState.getParentRoot()))
            ? Optional.of(ForkChoiceNode.createBase(blockAndState.getParentRoot()))
            : Optional.empty(),
        blockAndState.getStateRoot(),
        spec.calculateBlockCheckpoints(blockAndState.getState()),
        blockAndState.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
        blockAndState.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
        spec.isBlockProcessorOptimistic(blockAndState.getSlot()));
    updateBestChildAndDescendantOfParent(spec, protoArray, nodeIdentity);
  }

  private void addBlockToProtoArray(
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final Optional<Integer> parentIndex) {
    addBlockToProtoArray(spec, protoArray, blockAndState, parentIndex);
  }

  private void addBlockToProtoArray(
      final Spec spec,
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final Optional<Integer> parentIndex) {
    final ForkChoiceNode nodeIdentity = ForkChoiceNode.createBase(blockAndState.getRoot());
    protoArray.addNode(
        nodeIdentity,
        blockAndState.getSlot(),
        blockAndState.getParentRoot(),
        parentIndex.map(protoArray::getNodeByIndex).map(ProtoNode::getForkChoiceNode),
        blockAndState.getStateRoot(),
        spec.calculateBlockCheckpoints(blockAndState.getState()),
        blockAndState.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
        blockAndState.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
        spec.isBlockProcessorOptimistic(blockAndState.getSlot()));
    updateBestChildAndDescendantOfParent(spec, protoArray, nodeIdentity);
  }

  private GloasBoundaryFixture createGloasBoundaryFixture() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(gloasSpec.computeStartSlotAtEpoch(ONE));
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(ONE);
    final SignedBlockAndState boundary =
        chainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();
    final ExecutionPayloadUpdate boundaryExecutionPayload =
        new ExecutionPayloadUpdate(
            chainBuilder.getExecutionPayloadAtSlot(boundary.getSlot()).orElseThrow(), false);
    final UInt64 childSlot = boundary.getSlot().plus(1);
    final BeaconState childPreState = chainBuilder.getLatestBlockAndState().getState();
    final Bytes32 childPrevRandao =
        gloasSpec
            .atSlot(childSlot)
            .beaconStateAccessors()
            .getRandaoMix(
                childPreState,
                gloasSpec.atSlot(childSlot).beaconStateAccessors().getCurrentEpoch(childPreState));
    final SignedBlockAndState child =
        chainBuilder.generateBlockAtSlot(
            childSlot,
            BlockOptions.create()
                .setExecutionPayload(
                    gloasDataStructureUtil.randomExecutionPayload(
                        childSlot,
                        builder ->
                            builder
                                .parentHash(
                                    boundaryExecutionPayload
                                        .executionPayload()
                                        .getMessage()
                                        .getPayload()
                                        .getBlockHash())
                                .prevRandao(childPrevRandao))));
    final ProtoArray protoArray = createProtoArray(gloasSpec, genesis.getState());
    addBlockToProtoArray(gloasSpec, protoArray, genesis);
    final ForkChoiceStrategy strategy = ForkChoiceStrategy.initialize(gloasSpec, protoArray);
    final BlockAndCheckpoints boundaryBlockAndCheckpoints =
        BlockAndCheckpoints.fromBlockAndState(gloasSpec, boundary);
    return new GloasBoundaryFixture(
        gloasSpec,
        protoArray,
        strategy,
        genesis,
        boundary,
        child,
        finalizedCheckpoint,
        boundaryBlockAndCheckpoints,
        boundaryExecutionPayload);
  }

  private GloasPayloadDecisionFixture createGloasPayloadDecisionFixture() {
    return createGloasPayloadDecisionFixture(true, true);
  }

  private GloasPayloadDecisionFixture createGloasPayloadDecisionFixture(
      final boolean includeFullNode, final boolean fullyValidateFullNode) {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(ONE);
    final ProtoArray protoArray = createProtoArray(gloasSpec, block.getState());
    addBlockToProtoArray(gloasSpec, protoArray, genesis);
    addBlockToProtoArray(gloasSpec, protoArray, block);
    addProjectedNodeToProtoArray(
        gloasSpec,
        protoArray,
        block,
        ForkChoiceNode.createEmpty(block.getRoot()),
        ForkChoiceNode.createBase(block.getRoot()));
    if (includeFullNode) {
      addProjectedNodeToProtoArray(
          gloasSpec,
          protoArray,
          block,
          ForkChoiceNode.createFull(block.getRoot()),
          ForkChoiceNode.createBase(block.getRoot()),
          block.getExecutionBlockNumber().orElse(UInt64.ONE),
          block.getExecutionBlockHash().orElse(dataStructureUtil.randomBytes32()),
          true);
      if (fullyValidateFullNode) {
        protoArray.markNodeValid(ForkChoiceNode.createFull(block.getRoot()));
      }
    }

    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    return new GloasPayloadDecisionFixture(
        gloasSpec, ForkChoiceStrategy.initialize(gloasSpec, protoArray), store, block);
  }

  private boolean shouldBuildOnFull(
      final GloasPayloadDecisionFixture fixture, final ForkChoiceNode head) {
    return shouldBuildOnFull(fixture, fixture.block().getSlot().plus(ONE), head);
  }

  private boolean shouldBuildOnFull(
      final GloasPayloadDecisionFixture fixture, final UInt64 slot, final ForkChoiceNode head) {
    return fixture.strategy().shouldBuildOnFull(fixture.store(), slot, head);
  }

  private IntSet ptcPositions(final int count) {
    final IntSet positions = new IntOpenHashSet();
    for (int i = 0; i < count; i++) {
      positions.add(i);
    }
    return positions;
  }

  private ProtoNode boundaryBaseNode(final GloasBoundaryFixture fixture) {
    return fixture
        .protoArray()
        .getNode(ForkChoiceNode.createBase(fixture.boundary().getRoot()))
        .orElseThrow();
  }

  private ProtoNode childBaseNode(final GloasBoundaryFixture fixture) {
    return fixture
        .protoArray()
        .getNode(ForkChoiceNode.createBase(fixture.child().getRoot()))
        .orElseThrow();
  }

  private void assertExecutionGasLimit(
      final StorageSystem storageSystem,
      final SignedBlockAndState block,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit) {
    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getExecutionGasLimitForBlockRootAndHash(block.getRoot(), executionBlockHash))
        .contains(executionGasLimit);
  }

  private void assertProtoArrayParentIndicesAreValid(final ProtoArray protoArray) {
    final int nodeCount = protoArray.getTotalTrackedNodeCount();
    protoArray
        .getNodes()
        .forEach(
            node ->
                node.getParentIndex()
                    .ifPresent(parentIndex -> assertThat(parentIndex).isBetween(0, nodeCount - 1)));
  }

  private void addProjectedNodeToProtoArray(
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final ForkChoiceNode nodeIdentity,
      final ForkChoiceNode parentNodeIdentity) {
    addProjectedNodeToProtoArray(spec, protoArray, blockAndState, nodeIdentity, parentNodeIdentity);
  }

  private void addProjectedNodeToProtoArray(
      final Spec spec,
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final ForkChoiceNode nodeIdentity,
      final ForkChoiceNode parentNodeIdentity) {
    addProjectedNodeToProtoArray(
        spec,
        protoArray,
        blockAndState,
        nodeIdentity,
        parentNodeIdentity,
        blockAndState.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
        blockAndState.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH));
  }

  private void addProjectedNodeToProtoArray(
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final ForkChoiceNode nodeIdentity,
      final ForkChoiceNode parentNodeIdentity,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash) {
    addProjectedNodeToProtoArray(
        spec,
        protoArray,
        blockAndState,
        nodeIdentity,
        parentNodeIdentity,
        executionBlockNumber,
        executionBlockHash,
        spec.isBlockProcessorOptimistic(blockAndState.getSlot()));
  }

  private void addProjectedNodeToProtoArray(
      final Spec spec,
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final ForkChoiceNode nodeIdentity,
      final ForkChoiceNode parentNodeIdentity,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash) {
    addProjectedNodeToProtoArray(
        spec,
        protoArray,
        blockAndState,
        nodeIdentity,
        parentNodeIdentity,
        executionBlockNumber,
        executionBlockHash,
        spec.isBlockProcessorOptimistic(blockAndState.getSlot()));
  }

  private void addProjectedNodeToProtoArray(
      final Spec spec,
      final ProtoArray protoArray,
      final SignedBlockAndState blockAndState,
      final ForkChoiceNode nodeIdentity,
      final ForkChoiceNode parentNodeIdentity,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final boolean optimisticallyProcessed) {
    protoArray.addNode(
        nodeIdentity,
        blockAndState.getSlot(),
        blockAndState.getParentRoot(),
        Optional.of(parentNodeIdentity),
        blockAndState.getStateRoot(),
        spec.calculateBlockCheckpoints(blockAndState.getState()),
        executionBlockNumber,
        executionBlockHash,
        optimisticallyProcessed);
    updateBestChildAndDescendantOfParent(spec, protoArray, nodeIdentity);
  }

  private void updateBestChildAndDescendantOfParent(
      final Spec spec, final ProtoArray protoArray, final ForkChoiceNode nodeIdentity) {
    protoArray.updateBestChildAndDescendantOfParent(
        nodeIdentity,
        new HeadSelectionContext(
            new ForkChoiceModelFactory(spec),
            BlockNodeVariantsIndex.fromProtoArray(protoArray),
            UInt64.ZERO,
            Optional.empty()));
  }

  private record InternalPayloadTraversalFixture(
      ForkChoiceStrategy strategy,
      SignedBlockAndState genesis,
      SignedBlockAndState block1,
      SignedBlockAndState block2) {}

  private record GloasBoundaryFixture(
      Spec spec,
      ProtoArray protoArray,
      ForkChoiceStrategy strategy,
      SignedBlockAndState genesis,
      SignedBlockAndState boundary,
      SignedBlockAndState child,
      Checkpoint finalizedCheckpoint,
      BlockAndCheckpoints boundaryBlockAndCheckpoints,
      ExecutionPayloadUpdate boundaryExecutionPayload) {}

  private record GloasPayloadDecisionFixture(
      Spec spec, ForkChoiceStrategy strategy, ReadOnlyStore store, SignedBlockAndState block) {}
}

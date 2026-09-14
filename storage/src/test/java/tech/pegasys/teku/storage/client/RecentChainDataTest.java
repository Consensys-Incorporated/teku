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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.generator.ChainProperties;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.HeadEvent;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class RecentChainDataTest {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec =
      TestSpecFactory.createMinimalDeneb(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfig genesisSpecConfig = spec.getGenesisSpecConfig();
  private StorageSystem storageSystem;

  private ChainBuilder chainBuilder;
  private SignedBlockAndState genesis;
  private BeaconState genesisState;
  private BeaconBlock genesisBlock;

  private RecentChainData recentChainData;

  private void initPreGenesis() {
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
  }

  private void initPostGenesis() {
    initPreGenesis();

    generateGenesisWithoutIniting();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);
  }

  private void generateGenesisWithoutIniting() {
    genesis = chainBuilder.generateGenesis();
    genesisState = genesis.getState();
    genesisBlock = genesis.getBlock().getMessage();
  }

  @Test
  public void initialize_setupInitialState() {
    initPreGenesis();
    generateGenesisWithoutIniting();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(recentChainData.getGenesisTime()).isEqualTo(genesisState.getGenesisTime());
    assertThat(recentChainData.getGenesisTimeMillis())
        .isEqualTo(secondsToMillis(genesisState.getGenesisTime()));
    assertThat(recentChainData.getHeadSlot()).isEqualTo(GENESIS_SLOT);
    assertThat(recentChainData.getBestState()).isPresent();
    assertThat(recentChainData.getBestState().get()).isCompletedWithValue(genesisState);
    assertThat(recentChainData.getStore()).isNotNull();
  }

  @Test
  public void initializeFromGenesis_withTimeLessThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.valueOf(100));
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(genesisTime);
  }

  @Test
  public void initializeFromGenesis_withTimeGreaterThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    final UInt64 time = genesisTime.plus(100);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    recentChainData.initializeFromGenesis(genesis.getState(), time);
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(time);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeLessThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    recentChainData.initializeFromAnchorPoint(anchorPoint, UInt64.valueOf(100));
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(anchorBlockTime);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeLessThanAnchorBlockTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = genesisTime.plus(1);
    assertThat(time).isLessThan(anchorBlockTime);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(anchorBlockTime);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeGreaterThanAnchorBlockTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = anchorBlockTime.plus(100);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(time);
  }

  @Test
  void initializeFromAnchorPoint_withEmptySlotAtEndOfFinalizedEpoch() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    final List<SignedBlockAndState> chain = chainBuilder.generateBlocksUpToSlot(15);
    chainBuilder.generateBlockAtSlot(17);
    final SignedBlockAndState anchor = chain.getLast();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = anchorBlockTime.plus(100);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(time);
  }

  @Test
  void initializeFromAnchorPoint_withEmptySlotAtStartOEpoch() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    chainBuilder.generateBlocksUpToSlot(15);
    final SignedBlockAndState anchor = chainBuilder.generateBlockAtSlot(18);

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = anchorBlockTime.plus(100);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTimeSeconds()).isEqualTo(time);
  }

  @Test
  void initializeFromAnchorPoint_withTransitionedStateShouldRebuildForkChoiceAtBlockSlot()
      throws Exception {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    final UInt64 checkpointStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState anchorBlock =
        chainBuilder.generateBlockAtSlot(checkpointStartSlot.minus(2));
    final BeaconState anchorState = spec.processSlots(anchorBlock.getState(), checkpointStartSlot);
    final Checkpoint checkpoint =
        new Checkpoint(spec.computeNextEpochBoundary(anchorState.getSlot()), anchorBlock.getRoot());
    final AnchorPoint anchorPoint =
        AnchorPoint.create(spec, checkpoint, anchorState, Optional.of(anchorBlock.getBlock()));

    assertThat(anchorBlock.getSlot()).isLessThan(checkpointStartSlot.minus(1));
    assertThat(anchorState.getSlot()).isEqualTo(checkpointStartSlot);
    assertThat(anchorState.getSlot()).isNotEqualTo(anchorBlock.getSlot());

    recentChainData.initializeFromAnchorPoint(anchorPoint, UInt64.ZERO);

    assertThat(recentChainData.getSlotForBlockRoot(anchorBlock.getRoot()))
        .contains(anchorBlock.getSlot());
    assertThat(
            recentChainData.getBlockRootInEffectBySlot(
                anchorBlock.getSlot(), anchorBlock.getRoot()))
        .contains(anchorBlock.getRoot());
    assertThat(
            recentChainData.getBlockRootInEffectBySlot(
                anchorState.getSlot(), anchorBlock.getRoot()))
        .contains(anchorBlock.getRoot());
  }

  @Test
  void getGenesisData_shouldBeEmptyPreGenesis() {
    initPreGenesis();
    assertThat(recentChainData.getGenesisData()).isEmpty();
  }

  @Test
  public void updateHead_validUpdate() {
    initPostGenesis();

    // Ensure the current and previous target root blocks are different
    chainBuilder.generateBlockAtSlot(genesisSpecConfig.getSlotsPerEpoch() - 1);
    chainBuilder.generateBlockAtSlot(genesisSpecConfig.getSlotsPerEpoch() * 2L - 1);
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    chainBuilder
        .streamBlocksAndStates()
        .forEach(blockAndState -> saveBlock(recentChainData, blockAndState));

    recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(recentChainData.getChainHead().map(MinimalBeaconBlockSummary::getRoot))
        .contains(bestBlock.getRoot());
    assertThat(this.storageSystem.chainHeadChannel().getHeadEvents())
        .contains(
            new HeadEvent(
                bestBlock.getSlot(),
                bestBlock.getStateRoot(),
                bestBlock.getRoot(),
                true,
                true,
                spec.getBeaconStateUtil(bestBlock.getSlot())
                    .getPreviousDutyDependentRoot(bestBlock.getState()),
                spec.getBeaconStateUtil(bestBlock.getSlot())
                    .getCurrentDutyDependentRoot(bestBlock.getState())));
  }

  @Test
  public void updateHead_blockAndStateAreMissing() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();

    recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(recentChainData.getChainHead().map(MinimalBeaconBlockSummary::getRoot))
        .contains(genesis.getRoot());
  }

  @Test
  void retrieveStateInEffectAtSlot_returnEmptyWhenStoreNotSet() {
    initPreGenesis();
    final SafeFuture<Optional<BeaconState>> result =
        recentChainData.retrieveStateInEffectAtSlot(UInt64.ZERO);
    assertThatSafeFuture(result).isCompletedWithEmptyOptional();
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnGenesisStateWhenItIsTheBestState() {
    initPostGenesis();
    assertThat(recentChainData.retrieveStateInEffectAtSlot(genesis.getSlot()))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenSlotsAreEmpty() {
    initPostGenesis();
    // Request block for an empty slot immediately after genesis
    final UInt64 requestedSlot = genesisBlock.getSlot().plus(ONE);
    final UInt64 bestSlot = requestedSlot.plus(ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateHead(recentChainData, bestBlock);

    assertThat(recentChainData.retrieveStateInEffectAtSlot(requestedSlot))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenHeadSlotIsEmpty() {
    initPostGenesis();
    assertThat(recentChainData.retrieveStateInEffectAtSlot(ONE))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnHeadState() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = addNewBestBlock(recentChainData);
    assertThat(recentChainData.retrieveStateInEffectAtSlot(bestBlock.getSlot()))
        .isCompletedWithValue(Optional.of(bestBlock.getState()));
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() {
    initPostGenesis();
    final Checkpoint originalCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();

    // Add a new finalized checkpoint
    final SignedBlockAndState newBlock = advanceChain(recentChainData);
    final UInt64 finalizedEpoch = originalCheckpoint.getEpoch().plus(ONE);
    final Checkpoint newCheckpoint = new Checkpoint(finalizedEpoch, newBlock.getRoot());
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint, false);

    tx.commit().finishDebug(LOG);

    // Check that store was updated
    final Checkpoint currentCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    initPostGenesis();
    final Checkpoint originalCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTimeMillis(UInt64.valueOf(11000L));
    tx.commit().finishDebug(LOG);

    final Checkpoint currentCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  @Test
  public void updateHead_noReorgEventWhenBestBlockFirstSet() {
    initPreGenesis();
    generateGenesisWithoutIniting();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isZero();
  }

  @Test
  public void updateHead_noReorgEventWhenChainAdvances() {
    initPostGenesis();
    chainBuilder.generateBlocksUpToSlot(2);
    importBlocksAndStates(recentChainData, chainBuilder);

    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
  }

  @Test
  public void updateHead_calculateDependentRootCorrectly() {
    initPostGenesis();
    recentChainData.getStore().getForkChoiceStrategy().setPruneThreshold(0);
    storageSystem.chainUpdater().finalizeCurrentChain();
    final List<HeadEvent> headEvents = storageSystem.chainHeadChannel().getHeadEvents();
    assertThat(headEvents).isNotEmpty();
    headEvents.forEach(this::verifyDependentRoots);
    headEvents.clear();
    assertProtoArrayHasNoFinalizedBlocks();

    final SignedBlockAndState latestBlockAndState = storageSystem.chainUpdater().advanceChain();
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(headEvents).hasSize(1);
    verifyDependentRoots(headEvents.get(0));

    // Head reverts to the justified checkpoint (really is possible though quirky)
    final SignedBeaconBlock justifiedBlock =
        chainBuilder
            .getBlock(recentChainData.getJustifiedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    recentChainData.updateHead(justifiedBlock.getRoot(), justifiedBlock.getSlot());
    assertThat(headEvents).hasSize(2);
    verifyDependentRoots(headEvents.get(1));
  }

  /**
   * Checks that the protoarray was fully pruned and the block prior to the finalized block is not
   * available.
   */
  private void assertProtoArrayHasNoFinalizedBlocks() {
    final Bytes32 finalizedBlock = recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot();
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final Bytes32 finalizedParent =
        forkChoiceStrategy.blockParentRoot(finalizedBlock).orElseThrow();
    assertThat(finalizedParent.isZero()).isFalse();
    assertThat(forkChoiceStrategy.blockSlot(finalizedParent)).isEmpty();
  }

  private void verifyDependentRoots(final HeadEvent headEvent) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(headEvent.getSlot());
    final UInt64 previousEpoch = currentEpoch.minusMinZero(1);
    final UInt64 currentDependentRootSlot =
        spec.computeStartSlotAtEpoch(currentEpoch).minusMinZero(1);
    final UInt64 previousDependentRootSlot =
        spec.computeStartSlotAtEpoch(previousEpoch).minusMinZero(1);
    assertThat(headEvent.getCurrentDutyDependentRoot())
        .isEqualTo(chainBuilder.getBlockAtSlot(currentDependentRootSlot).getRoot());
    assertThat(headEvent.getPreviousDutyDependentRoot())
        .isEqualTo(chainBuilder.getBlockAtSlot(previousDependentRootSlot).getRoot());
  }

  @Test
  public void updateHead_forkChoiceHeadNeverGoesDown() {
    initPostGenesis();
    chainBuilder.generateBlocksUpToSlot(3);
    importBlocksAndStates(recentChainData, chainBuilder);

    final SignedBlockAndState block1 = chainBuilder.getBlockAndStateAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.getBlockAndStateAtSlot(2);
    final SignedBlockAndState block3 = chainBuilder.getBlockAndStateAtSlot(3);
    // Head updated to block1 followed by empty slots up to slot 11
    recentChainData.updateHead(block1.getRoot(), UInt64.valueOf(11));
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    // Head updated to block2 but should preserve empty slots up to slot 11
    recentChainData.updateHead(block2.getRoot(), block2.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    recentChainData.updateHead(block3.getRoot(), UInt64.valueOf(12));
    // We ignore the empty slots so no reorgs
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
  }

  @Test
  public void updateHead_noReorgEventWhenBlockFillsEmptyHeadSlot() {
    initPostGenesis();
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot1Block.getRoot(), UInt64.valueOf(2));
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isZero();

    final SignedBlockAndState slot2Block = chainBuilder.generateBlockAtSlot(2);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot2Block.getRoot(), slot2Block.getSlot());
    final List<ReorgEvent> reorgEvents = storageSystem.chainHeadChannel().getReorgEvents();

    assertThat(reorgEvents).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isEqualTo(0);
  }

  @Test
  public void updateHead_headDoesNotUpdateWhenAdvancingOverEmptySlot() {
    initPostGenesis();
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot());
    // Clear head updates from setup
    storageSystem.chainHeadChannel().getHeadEvents().clear();

    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot().plus(1));
    assertThat(storageSystem.chainHeadChannel().getHeadEvents()).isEmpty();
  }

  @Test
  public void updateHead_headUpdatesWhenOptimisticFlipsToValid() {
    initPostGenesis();
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot());
    assertThat(recentChainData.getChainHead().orElseThrow().isOptimistic()).isTrue();
    storageSystem.chainHeadChannel().getHeadEvents().clear();

    // Flip the proto-node from optimistic to validated.
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .onExecutionPayloadResult(slot1Block.getRoot(), PayloadStatus.VALID, false);

    // Same root, same slot — but isOptimistic / payloadStatus changed, so head must be re-emitted.
    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot());

    assertThat(storageSystem.chainHeadChannel().getHeadEvents()).hasSize(1);
    assertThat(recentChainData.getChainHead().orElseThrow().isOptimistic()).isFalse();
  }

  @Test
  public void updateHead_headUpdatesWhenSwitchingFromEmptyToFullNodeInGloas() {
    // Locally re-initialize with a Gloas spec; do not touch the class fields.
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final StorageSystem gloasStorage =
        InMemoryStorageSystemBuilder.create()
            .specProvider(gloasSpec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    final ChainBuilder gloasChainBuilder = gloasStorage.chainBuilder();
    final RecentChainData gloasRecentChainData = gloasStorage.recentChainData();
    final SignedBlockAndState gloasGenesis = gloasChainBuilder.generateGenesis();
    gloasRecentChainData.initializeFromGenesis(gloasGenesis.getState(), UInt64.ZERO);

    final SignedBlockAndState block = gloasChainBuilder.generateBlockAtSlot(1);
    final SignedExecutionPayloadEnvelope executionPayload =
        gloasChainBuilder.getExecutionPayloadAtSlot(block.getSlot()).orElseThrow();

    // Import block only — creates BASE (PENDING) + EMPTY proto-nodes; FULL is not present yet.
    StoreTransaction tx = gloasRecentChainData.startStoreTransaction();
    tx.putBlockAndState(block, gloasSpec.calculateBlockCheckpoints(block.getState()));
    tx.commit().join();

    // Set head on the EMPTY node.
    gloasRecentChainData.updateHead(ForkChoiceNode.createEmpty(block.getRoot()), block.getSlot());
    final Bytes32 emptyExecutionHash =
        gloasRecentChainData.getChainHead().orElseThrow().getExecutionBlockHash();
    gloasStorage.chainHeadChannel().getHeadEvents().clear();

    // Import the execution payload — creates the FULL proto-node with the real exec block hash.
    tx = gloasRecentChainData.startStoreTransaction();
    tx.putExecutionPayload(executionPayload, false);
    tx.commit().join();

    // Switch head to the FULL node identity (same root, different payloadStatus + execHash).
    gloasRecentChainData.updateHead(ForkChoiceNode.createFull(block.getRoot()), block.getSlot());

    assertThat(gloasStorage.chainHeadChannel().getHeadEvents()).hasSize(1);
    final ChainHead newHead = gloasRecentChainData.getChainHead().orElseThrow();
    assertThat(newHead.getPayloadStatus()).isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
    assertThat(newHead.getExecutionBlockHash()).isNotEqualTo(emptyExecutionHash);
  }

  @Test
  public void containsExecutionPayload_shouldReturnFalseWhenStoreUnavailable() {
    initPreGenesis();

    assertThat(recentChainData.containsExecutionPayload(Bytes32.ZERO)).isFalse();
  }

  @Test
  public void containsExecutionPayload_shouldReturnFalseWhenPayloadUnavailable() {
    initPostGenesis();

    assertThat(recentChainData.containsExecutionPayload(dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @Test
  public void containsExecutionPayload_shouldReturnTrueWhenPayloadAvailable() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final StorageSystem gloasStorage =
        InMemoryStorageSystemBuilder.create()
            .specProvider(gloasSpec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    final ChainBuilder gloasChainBuilder = gloasStorage.chainBuilder();
    final RecentChainData gloasRecentChainData = gloasStorage.recentChainData();
    final SignedBlockAndState gloasGenesis = gloasChainBuilder.generateGenesis();
    gloasRecentChainData.initializeFromGenesis(gloasGenesis.getState(), UInt64.ZERO);

    final SignedBlockAndState block = gloasChainBuilder.generateBlockAtSlot(1);
    final SignedExecutionPayloadEnvelope executionPayload =
        gloasChainBuilder.getExecutionPayloadAtSlot(block.getSlot()).orElseThrow();

    assertThat(gloasRecentChainData.containsExecutionPayload(block.getRoot())).isFalse();

    final StoreTransaction transaction = gloasRecentChainData.startStoreTransaction();
    transaction.putBlockAndState(block, gloasSpec.calculateBlockCheckpoints(block.getState()));
    transaction.putExecutionPayload(executionPayload, false);
    transaction.commit().join();

    assertThat(gloasRecentChainData.containsExecutionPayload(block.getRoot())).isTrue();
  }

  @Test
  public void updateHead_reorgEventWhenChainSwitchesToNewBlockAtSameSlot() {
    initPreGenesis();
    final ChainBuilder chainBuilder =
        ChainBuilder.create(spec, BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .toList();
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    recentChainData.updateHead(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    assertThat(storageSystem.chainHeadChannel().getReorgEvents())
        .containsExactly(
            new ReorgEvent(
                latestForkBlockAndState.getRoot(),
                latestForkBlockAndState.getSlot(),
                latestForkBlockAndState.getStateRoot(),
                latestBlockAndState.getRoot(),
                latestBlockAndState.getStateRoot(),
                ONE));
  }

  @Test
  public void updateHead_reorgEventWhenChainSwitchesToNewBlockAtLaterSlot() {
    initPreGenesis();
    final ChainBuilder chainBuilder =
        ChainBuilder.create(spec, BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .toList();
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));

    forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));

    // Fork extends a slot further
    final SignedBlockAndState latestForkBlockAndState = forkBuilder.generateBlockAtSlot(3);
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    recentChainData.updateHead(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    assertThat(storageSystem.chainHeadChannel().getReorgEvents())
        .containsExactly(
            new ReorgEvent(
                latestForkBlockAndState.getRoot(),
                latestForkBlockAndState.getSlot(),
                latestForkBlockAndState.getStateRoot(),
                latestBlockAndState.getRoot(),
                latestBlockAndState.getStateRoot(),
                ONE));
  }

  @Test
  public void getLatestFinalizedBlockSlot_genesis() {
    initPostGenesis();
    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(genesis.getSlot());
  }

  @Test
  public void getLatestFinalizedBlockSlot_postGenesisFinalizedBlockOutsideOfEpochBoundary() {
    initPostGenesis();
    final UInt64 epoch = ONE;
    final UInt64 epochBoundarySlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 finalizedBlockSlot = epochBoundarySlot.minus(ONE);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, finalizedBlock);

    // Start tx to update finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    // Initially finalized slot should match store
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
    // Update checkpoint and check finalized slot accessors
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()), false);
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(finalizedBlockSlot);
    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(genesis.getSlot());
    // Commit tx
    tx.commit().finishDebug(LOG);

    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(finalizedBlockSlot);
  }

  @Test
  public void retrieveBlockAndState_withBlockAndStateAvailable() {
    initPostGenesis();
    final SignedBlockAndState block = advanceChain(recentChainData);
    assertThat(recentChainData.getStore().retrieveBlockAndState(block.getRoot()))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  void detectsProposerEquivocationFromImportedForkChoiceBlocks() {
    initPostGenesis();
    advanceChain(recentChainData);
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState canonicalBlock = chainBuilder.generateNextBlock();
    final SignedBlockAndState conflictingBlock =
        forkBuilder.generateBlockAtSlot(
            canonicalBlock.getSlot(),
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.ONE, Bytes32.ZERO)));
    saveBlock(recentChainData, canonicalBlock);
    saveBlock(recentChainData, conflictingBlock);

    assertThat(conflictingBlock.getRoot()).isNotEqualTo(canonicalBlock.getRoot());
    assertThat(conflictingBlock.getProposerIndex()).isEqualTo(canonicalBlock.getProposerIndex());
    assertThat(
            recentChainData.isProposerEquivocation(
                canonicalBlock.getSlot(),
                canonicalBlock.getProposerIndex(),
                canonicalBlock.getRoot()))
        .isTrue();
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUnderlyingStore() {
    initPostGenesis();
    final SignedBlockAndState block = advanceChain(recentChainData);
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUpdates() {
    initPostGenesis();
    final SignedBlockAndState block = chainBuilder.generateNextBlock();

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block, spec.calculateBlockCheckpoints(block.getState()));

    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockRootBySlot_forOutOfRangeSlot() {
    initPostGenesis();
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots).plus(ONE);
    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, historicalBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootInEffectBySlot(targetSlot)).isEmpty();
  }

  @Test
  public void getBlockRootBySlot_forHistoricalSlotInRange() {
    initPostGenesis();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots);
    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, historicalBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootInEffectBySlot(targetSlot))
        .contains(historicalBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBestBlock() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootInEffectBySlot(bestBlock.getSlot()))
        .contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBlockPriorToBestBlock() {
    initPostGenesis();
    final SignedBlockAndState targetBlock = advanceBestBlock(recentChainData);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootInEffectBySlot(targetBlock.getSlot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forSlotAfterBestBlock() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    final UInt64 targetSlot = bestBlock.getSlot().plus(ONE);
    assertThat(recentChainData.getBlockRootInEffectBySlot(targetSlot))
        .contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_queryEntireChain() {
    initPostGenesis();
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());

    // Build a chain that spans multiple increments of SLOTS_PER_HISTORICAL_ROOT
    final int skipBlocks = 3;
    final UInt64 skipBlocksLong = UInt64.valueOf(skipBlocks);
    final UInt64 finalizedBlockSlot = UInt64.valueOf(10).plus(historicalRoots);
    final UInt64 finalizedEpoch =
        new ChainProperties(spec).computeBestEpochFinalizableAtSlot(finalizedBlockSlot);
    final UInt64 recentSlot = spec.computeStartSlotAtEpoch(finalizedEpoch).plus(ONE);
    final UInt64 chainHeight = historicalRoots.times(2).plus(recentSlot).plus(5);
    // Build historical blocks
    final SignedBlockAndState finalizedBlock;
    while (true) {
      if (chainBuilder.getLatestSlot().plus(skipBlocksLong).compareTo(finalizedBlockSlot) >= 0) {
        // Add our target finalized block
        finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
        saveBlock(recentChainData, finalizedBlock);
        break;
      }
      final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock(skipBlocks);
      saveBlock(recentChainData, nextBlock);
    }
    // Build recent blocks
    SignedBlockAndState bestBlock = null;
    UInt64 nextSlot = recentSlot;
    while (chainBuilder.getLatestSlot().compareTo(chainHeight) < 0) {
      bestBlock = chainBuilder.generateBlockAtSlot(nextSlot);
      saveBlock(recentChainData, bestBlock);
      nextSlot = nextSlot.plus(skipBlocks);
    }
    // Update best block and finalized state
    updateHead(recentChainData, bestBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);

    // Check slots that should be unavailable
    for (int i = 0; i < finalizedBlockSlot.intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      assertThat(recentChainData.getBlockRootInEffectBySlot(targetSlot)).isEmpty();
    }
    // Check slots that should be available
    for (int i = finalizedBlockSlot.intValue(); i <= bestBlock.getSlot().intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      final SignedBlockAndState expectedResult =
          chainBuilder.getLatestBlockAndStateAtSlot(targetSlot);
      final Optional<Bytes32> result = recentChainData.getBlockRootInEffectBySlot(targetSlot);
      assertThat(result)
          .withFailMessage(
              "Expected root at slot %s to be %s (%s) but was %s",
              targetSlot, expectedResult.getRoot(), expectedResult.getSlot(), result)
          .contains(expectedResult.getRoot());
    }
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forSlotAfterHeadRoot() {
    initPostGenesis();
    final SignedBlockAndState targetBlock = advanceBestBlock(recentChainData);
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(
            recentChainData.getBlockRootInEffectBySlot(bestBlock.getSlot(), targetBlock.getRoot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forUnknownHeadRoot() {
    initPostGenesis();
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootInEffectBySlot(bestBlock.getSlot(), headRoot)).isEmpty();
  }

  @Test
  public void isCloseToInSync_preGenesis() {
    initPreGenesis();
    assertThat(recentChainData.isCloseToInSync()).isFalse();
  }

  @Test
  public void isCloseToSync_belowBoundary() {
    initPostGenesis();
    final SpecConfig specConfig = spec.getGenesisSpecConfig();
    final int seconds =
        specConfig.getMaxSeedLookahead()
            * specConfig.getSlotsPerEpoch()
            * specConfig.getSecondsPerSlot();
    assertThat(recentChainData.isCloseToInSync(UInt64.valueOf(seconds - 1))).isTrue();
  }

  @Test
  public void isCloseToSync_atBoundary() {
    initPostGenesis();
    final SpecConfig specConfig = spec.getGenesisSpecConfig();
    final int seconds =
        specConfig.getMaxSeedLookahead()
            * specConfig.getSlotsPerEpoch()
            * specConfig.getSecondsPerSlot();
    assertThat(recentChainData.isCloseToInSync(UInt64.valueOf(seconds))).isTrue();
  }

  @Test
  public void isCloseToSync_aboveBoundary() {
    initPostGenesis();
    final SpecConfig specConfig = spec.getGenesisSpecConfig();
    final int seconds =
        specConfig.getMaxSeedLookahead()
            * specConfig.getSlotsPerEpoch()
            * specConfig.getSecondsPerSlot();
    assertThat(recentChainData.isCloseToInSync(UInt64.valueOf(seconds + 8))).isFalse();
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_withForkRoot() {
    initPostGenesis();
    // Build small chain
    for (int i = 0; i < 5; i++) {
      advanceChain(recentChainData);
    }

    // Split the chain
    final ChainBuilder fork = chainBuilder.fork();
    final UInt64 chainSplitSlot = chainBuilder.getLatestSlot();
    for (int i = 0; i < 5; i++) {
      final UInt64 canonicalBlockSlot = chainSplitSlot.plus(i * 2 + 2);
      final UInt64 forkSlot = chainSplitSlot.plus(i * 2 + 1);
      updateHead(recentChainData, chainBuilder.generateBlockAtSlot(canonicalBlockSlot));
      saveBlock(recentChainData, fork.generateBlockAtSlot(forkSlot));
    }

    final Bytes32 headRoot = fork.getLatestBlockAndState().getRoot();
    for (int i = 0; i < fork.getLatestSlot().intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      final SignedBlockAndState expectedBlock = fork.getLatestBlockAndStateAtSlot(targetSlot);
      if (targetSlot.compareTo(chainSplitSlot) > 0) {
        // Sanity check that fork differs from main chain
        assertThat(expectedBlock)
            .isNotEqualTo(chainBuilder.getLatestBlockAndStateAtSlot(targetSlot));
      }
      assertThat(recentChainData.getBlockRootInEffectBySlot(targetSlot, headRoot))
          .contains(expectedBlock.getRoot());
    }
  }

  @Test
  void getSlotForBlockRoot_shouldReturnEmptyForUnknownBlock() {
    initPostGenesis();

    assertThat(recentChainData.getSlotForBlockRoot(dataStructureUtil.randomBytes32())).isEmpty();
  }

  @Test
  void getSlotForBlockRoot_shouldReturnSlotForKnownBlock() {
    initPostGenesis();

    final SignedBeaconBlock block = storageSystem.chainUpdater().advanceChain().getBlock();
    assertThat(recentChainData.getSlotForBlockRoot(block.getRoot())).contains(block.getSlot());
  }

  @Test
  void retrieveBlobSidecars_shouldReturnBlobSidecarsWhenExists() {
    initPostGenesis();

    final SignedBlockAndState block =
        chainBuilder.generateBlockAtSlot(
            UInt64.valueOf(1), BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars = chainBuilder.getBlobSidecars(block.getRoot());
    assertThat(blobSidecars).isNotEmpty();
    storageSystem.chainUpdater().saveBlock(block, blobSidecars);

    final Optional<List<BlobSidecar>> maybeBlobSidecars =
        recentChainData.getBlobSidecars(block.getSlotAndBlockRoot());
    assertThat(maybeBlobSidecars).contains(blobSidecars);
  }

  @Test
  void retrieveBlobSidecars_shouldReturnEmptyBlobSidecarsWhenNotExists() {
    initPostGenesis();

    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(UInt64.valueOf(1));
    final List<BlobSidecar> blobSidecars = chainBuilder.getBlobSidecars(block.getRoot());
    storageSystem.chainUpdater().saveBlock(block, blobSidecars);
    assertThat(blobSidecars).isEmpty();

    final Optional<List<BlobSidecar>> maybeBlobSidecars =
        recentChainData.getBlobSidecars(block.getSlotAndBlockRoot());
    assertThat(maybeBlobSidecars).contains(blobSidecars);
  }

  @Test
  void saveBlock_shouldNotOverrideEarliestBlobSidecarWithGreaterValue() {
    initPostGenesis();

    final SignedBlockAndState block1 =
        chainBuilder.generateBlockAtSlot(
            UInt64.valueOf(1), BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 = chainBuilder.getBlobSidecars(block1.getRoot());
    storageSystem.chainUpdater().saveBlock(block1, Optional.of(blobSidecars1), Optional.of(ONE));
    // 0 from genesis
    assertThat(recentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(UInt64.valueOf(0)));
  }

  @Test
  public void commit_pruneParallelNewBlocks() {
    initPostGenesis();
    testCommitPruningOfParallelBlocks(true);
  }

  @Test
  public void commit_pruneParallelExistingBlocks() {
    initPostGenesis();
    testCommitPruningOfParallelBlocks(false);
  }

  @Test
  public void getAncestorsOnFork() {
    initPreGenesis();
    final ChainBuilder chainBuilder =
        ChainBuilder.create(spec, BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);

    chainBuilder.generateBlockAtSlot(1);

    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .toList();
    final ChainBuilder forkBuilder = chainBuilder.fork();

    final SignedBlockAndState firstBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(3));

    final SignedBlockAndState forkFirstBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(3), blockOptions.get(1));
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    assertThat(recentChainData.getAncestorsOnFork(UInt64.valueOf(1), latestBlockAndState.getRoot()))
        .containsOnly(
            Map.entry(UInt64.valueOf(2), firstBlockAndState.getRoot()),
            Map.entry(UInt64.valueOf(3), latestBlockAndState.getRoot()));

    assertThat(
            recentChainData.getAncestorsOnFork(
                UInt64.valueOf(1), latestForkBlockAndState.getRoot()))
        .containsOnly(
            Map.entry(UInt64.valueOf(2), forkFirstBlockAndState.getRoot()),
            Map.entry(UInt64.valueOf(3), latestForkBlockAndState.getRoot()));
  }

  @Test
  public void getAncestorsOnFork_unknownRoot() {
    initPreGenesis();
    final ChainBuilder chainBuilder =
        ChainBuilder.create(spec, BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(recentChainData.getAncestorsOnFork(UInt64.valueOf(1), Bytes32.ZERO)).isEmpty();
  }

  /**
   * Builds 2 parallel chains, one of which will get pruned when a block in the middle of the other
   * chain is finalized. Keep one chain in the finalizing transaction, the other chain is already
   * saved to the store.
   *
   * @param pruneNewBlocks Whether to keep the blocks to be pruned in the finalizing transaction, or
   *     keep the blocks to be kept in the finalizing transaction @
   */
  private void testCommitPruningOfParallelBlocks(final boolean pruneNewBlocks) {
    final UInt64 epoch2Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));

    // Create a fork by skipping the next slot on the fork chain
    ChainBuilder fork = chainBuilder.fork();
    // Generate the next 2 blocks on the primary chain
    final SignedBlockAndState firstCanonicalBlock = chainBuilder.generateNextBlock();
    saveBlock(recentChainData, firstCanonicalBlock);
    saveBlock(recentChainData, chainBuilder.generateNextBlock());
    // Skip a block and then generate the next block on the fork chain
    final SignedBlockAndState firstForkBlock = fork.generateNextBlock(1);
    saveBlock(recentChainData, firstForkBlock);

    // Build both the primary and fork chain past epoch1
    // Make sure both chains are at the same slot
    assertThat(chainBuilder.getLatestSlot()).isEqualTo(fork.getLatestSlot());
    while (chainBuilder.getLatestSlot().compareTo(epoch2Slot) < 0) {
      chainBuilder.generateNextBlock();
      fork.generateNextBlock();
    }

    // Save one chain to the store, setup the other chain to be saved in the finalizing transaction
    final List<SignedBlockAndState> newBlocks = new ArrayList<>();
    if (pruneNewBlocks) {
      // Save canonical blocks now, put fork blocks in the transaction
      chainBuilder
          .streamBlocksAndStates(firstCanonicalBlock.getSlot())
          .forEach(b -> saveBlock(recentChainData, b));
      fork.streamBlocksAndStates(firstForkBlock.getSlot()).forEach(newBlocks::add);
    } else {
      // Save fork blocks now, put canonical blocks in the transaction
      chainBuilder.streamBlocksAndStates(firstCanonicalBlock.getSlot()).forEach(newBlocks::add);
      fork.streamBlocksAndStates(firstForkBlock.getSlot())
          .forEach(b -> saveBlock(recentChainData, b));
    }

    // Add blocks and finalize epoch 1, so that blocks will be pruned
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(1);
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    newBlocks.forEach(
        blockAndState ->
            tx.putBlockAndState(
                blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    tx.commit().finishDebug(LOG);

    // Check that only recent, canonical blocks at or after the latest finalized block are left in
    // the store
    final List<SignedBlockAndState> expectedBlocks =
        chainBuilder.streamBlocksAndStates(finalizedCheckpoint.getEpochStartSlot(spec)).toList();
    final Set<Bytes32> blockRoots =
        expectedBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    // Collect blocks that should be pruned
    final Set<Bytes32> prunedBlocks =
        fork.streamBlocksAndStates(firstForkBlock.getSlot())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());

    // Check expected blocks
    assertThat(recentChainData.getStore().getOrderedBlockRoots())
        .containsExactlyInAnyOrderElementsOf(blockRoots);
    for (SignedBlockAndState expectedBlock : expectedBlocks) {
      assertThat(recentChainData.retrieveSignedBlockByRoot(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getBlock()));
      assertThat(recentChainData.retrieveBlockState(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getState()));
    }
    // Check pruned blocks
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThatSafeFuture(recentChainData.retrieveSignedBlockByRoot(prunedBlock))
          .isCompletedWithEmptyOptional();
      assertThat(recentChainData.retrieveBlockState(prunedBlock))
          .isCompletedWithValue(Optional.empty());
    }
  }

  @Test
  public void getForkAndForkDigestIsCorrectInFulu() {
    final Spec fuluSpec = TestSpecFactory.createMinimalWithFuluForkEpoch(ONE);

    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(fuluSpec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    genesis = chainBuilder.generateGenesis();
    final Bytes4 electraForkDigest =
        fuluSpec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), ZERO);

    final Bytes4 fuluForkDigest =
        fuluSpec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), ONE);

    genesisState = genesis.getState();
    genesisBlock = genesis.getBlock().getMessage();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(recentChainData.getForkDigest(ZERO)).isEqualTo(electraForkDigest);
    assertThat(recentChainData.getForkDigest(ONE)).isEqualTo(fuluForkDigest);
    assertThat(recentChainData.getNextForkDigest(ZERO)).contains(fuluForkDigest);
    assertThat(recentChainData.getNextForkDigest(ONE)).isEmpty();
  }

  @Test
  public void getForkAndForkDigestIsCorrectInFuluWithBpo() {
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            b ->
                b.electraForkEpoch(UInt64.valueOf(9))
                    .fuluForkEpoch(UInt64.valueOf(100))
                    .electraBuilder(eb -> eb.maxBlobsPerBlockElectra(9))
                    .fuluBuilder(
                        fb ->
                            fb.blobSchedule(
                                List.of(
                                    new BlobScheduleEntry(UInt64.valueOf(120), 100),
                                    new BlobScheduleEntry(UInt64.valueOf(150), 175),
                                    new BlobScheduleEntry(UInt64.valueOf(200), 200)))));

    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    genesis = chainBuilder.generateGenesis();
    final Bytes4 electraForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(9));

    final Bytes4 fuluForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(100));
    final Bytes4 fuluBpo1ForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(120));
    assertThat(fuluBpo1ForkDigest).isNotEqualTo(fuluForkDigest);
    final Bytes4 fuluBpo2ForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(150));
    final Bytes4 fuluBpo3ForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(200));

    genesisState = genesis.getState();
    genesisBlock = genesis.getBlock().getMessage();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(9))).isEqualTo(electraForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(9))).contains(fuluForkDigest);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(100))).isEqualTo(fuluForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(100))).contains(fuluBpo1ForkDigest);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(120))).isEqualTo(fuluBpo1ForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(120))).contains(fuluBpo2ForkDigest);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(150))).isEqualTo(fuluBpo2ForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(150))).contains(fuluBpo3ForkDigest);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(200))).isEqualTo(fuluBpo3ForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(200))).isEmpty();
  }

  @Test
  public void getForkAndForkDigestIsCorrectInFuluWithBpoSameEpoch() {
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            b ->
                b.fuluForkEpoch(UInt64.valueOf(100))
                    .fuluBuilder(
                        fb ->
                            fb.blobSchedule(
                                List.of(new BlobScheduleEntry(UInt64.valueOf(100), 100)))));

    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    genesis = chainBuilder.generateGenesis();

    final Bytes4 fuluBpo1ForkDigest =
        spec.computeForkDigest(genesis.getState().getGenesisValidatorsRoot(), UInt64.valueOf(100));

    genesisState = genesis.getState();
    genesisBlock = genesis.getBlock().getMessage();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(recentChainData.getForkDigest(UInt64.valueOf(100))).isEqualTo(fuluBpo1ForkDigest);
    assertThat(recentChainData.getNextForkDigest(UInt64.valueOf(100))).isEmpty();
  }

  private void importBlocksAndStates(
      final RecentChainData client, final ChainBuilder... chainBuilders) {
    final StoreTransaction transaction = client.startStoreTransaction();
    Stream.of(chainBuilders)
        .flatMap(ChainBuilder::streamBlocksAndStates)
        .forEach(
            blockAndState ->
                transaction.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    transaction.commit().join();
  }

  private SignedBlockAndState addNewBestBlock(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateHead(recentChainData, nextBlock);

    return nextBlock;
  }

  private void updateHead(
      final RecentChainData recentChainData, final SignedBlockAndState bestBlock) {
    saveBlock(recentChainData, bestBlock);

    this.recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
  }

  private SignedBlockAndState advanceBestBlock(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = advanceChain(recentChainData);
    updateHead(recentChainData, nextBlock);
    return nextBlock;
  }

  private void finalizeBlock(
      final RecentChainData recentChainData,
      final UInt64 epoch,
      final SignedBlockAndState finalizedBlock) {
    saveBlock(recentChainData, finalizedBlock);

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()), false);
    assertThat(tx.commit()).isCompleted();
  }

  private SignedBlockAndState advanceChain(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    saveBlock(recentChainData, nextBlock);
    return nextBlock;
  }

  private void saveBlock(final RecentChainData recentChainData, final SignedBlockAndState block) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block, spec.calculateBlockCheckpoints(block.getState()));
    tx.commit().finishDebug(LOG);
  }

  private void disableForkChoicePruneThreshold() {
    recentChainData.getUpdatableForkChoiceStrategy().orElseThrow().setPruneThreshold(0);
  }

  private long getReorgCountMetric(final StorageSystem storageSystem) {
    return storageSystem
        .getMetricsSystem()
        .getLabelledCounterValue(TekuMetricCategory.BEACON, "reorgs_total");
  }
}

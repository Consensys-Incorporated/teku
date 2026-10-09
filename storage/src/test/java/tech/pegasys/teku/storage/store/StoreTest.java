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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannelWithDelays;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class StoreTest extends AbstractStoreTest {
  @Test
  public void create_timeLessThanGenesisTime() {
    final UInt64 genesisTime = UInt64.valueOf(100);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);

    assertThatThrownBy(
            () ->
                Store.create(
                    SYNC_RUNNER,
                    new StubMetricsSystem(),
                    spec,
                    blockProviderFromChainBuilder(),
                    ExecutionPayloadProvider.NOOP,
                    BlindedExecutionPayloadProvider.NOOP,
                    StateAndBlockSummaryProvider.NOOP,
                    EarliestBlobSidecarSlotProvider.NOOP,
                    Optional.empty(),
                    genesisTime.minus(1),
                    genesisTime,
                    AnchorPoint.create(spec, genesisCheckpoint, genesis),
                    Optional.empty(),
                    genesisCheckpoint,
                    genesisCheckpoint,
                    Collections.emptyMap(),
                    Optional.empty(),
                    Collections.emptyMap(),
                    defaultStoreConfig,
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Time must be greater than or equal to genesisTime");
  }

  @Test
  public void retrieveSignedBlock_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final SignedBeaconBlock expectedBlock = blockAndState.getBlock();
          SafeFuture<Optional<SignedBeaconBlock>> result = store.retrieveSignedBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block %s", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void computeBalanceThresholds_setsHeadThreshold() {
    processChainHeadWithMockForkChoiceStrategy(
        (store, blockAndState) -> {
          store.computeBalanceThresholds(justifiedState(store));
          assertThat(store.getReorgThreshold()).isEqualTo(UInt64.valueOf("2400000000"));
        });
  }

  @Test
  public void computeBalanceThresholds_setsParentThreshold() {
    processChainHeadWithMockForkChoiceStrategy(
        (store, blockAndState) -> {
          store.computeBalanceThresholds(justifiedState(store));
          assertThat(store.getParentThreshold()).isEqualTo(UInt64.valueOf("19200000000"));
        });
  }

  @Test
  public void isFfgCompetitive_checkpointMatches() {
    final BlockCheckpoints headBlockCheckpoint = mock(BlockCheckpoints.class);
    final BlockCheckpoints parentBlockCheckpoint = mock(BlockCheckpoints.class);
    final Checkpoint checkpoint = new Checkpoint(UInt64.ZERO, Bytes32.random());
    when(headBlockCheckpoint.getUnrealizedJustifiedCheckpoint()).thenReturn(checkpoint);
    when(parentBlockCheckpoint.getUnrealizedJustifiedCheckpoint()).thenReturn(checkpoint);
    processChainHeadWithMockForkChoiceStrategy(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final Bytes32 parentRoot = blockAndState.getBlock().getParentRoot();
          setProtoNodeDataForBlock(blockAndState, headBlockCheckpoint, parentBlockCheckpoint);
          assertThat(store.isFfgCompetitive(root, parentRoot)).contains(true);
        });
  }

  @Test
  public void isFfgCompetitive_checkpointDifferent() {
    final BlockCheckpoints headBlockCheckpoint = mock(BlockCheckpoints.class);
    final BlockCheckpoints parentBlockCheckpoint = mock(BlockCheckpoints.class);
    final Checkpoint checkpoint = new Checkpoint(UInt64.ZERO, Bytes32.random());
    final Checkpoint checkpointParent = new Checkpoint(UInt64.ONE, Bytes32.random());
    when(headBlockCheckpoint.getUnrealizedJustifiedCheckpoint()).thenReturn(checkpoint);
    when(parentBlockCheckpoint.getUnrealizedJustifiedCheckpoint()).thenReturn(checkpointParent);
    processChainHeadWithMockForkChoiceStrategy(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final Bytes32 parentRoot = blockAndState.getBlock().getParentRoot();
          setProtoNodeDataForBlock(blockAndState, headBlockCheckpoint, parentBlockCheckpoint);
          assertThat(store.isFfgCompetitive(root, parentRoot)).contains(false);
        });
  }

  @Test
  public void isFfgCompetitive_missingProtoNodeEntries() {
    processChainHeadWithMockForkChoiceStrategy(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final Bytes32 parentRoot = blockAndState.getBlock().getParentRoot();
          assertThat(store.isFfgCompetitive(root, parentRoot)).isEmpty();
        });
  }

  @Test
  public void retrieveSignedBlock_withBlobs() {
    final UpdatableStore store = createGenesisStore();
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState signedBlockAndState =
        chainBuilder.generateBlockAtSlot(
            slot,
            ChainBuilder.BlockOptions.create()
                .setGenerateRandomBlobs(true)
                .setStoreBlobSidecars(true));

    addBlock(store, signedBlockAndState);

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState,
                    chainBuilder.getBlobSidecars(blockAndState.getBlock().getRoot()),
                    spec.calculateBlockCheckpoints(blockAndState.getState())));
    tx.commit().join();

    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(slot, signedBlockAndState.getBlock().getRoot());

    final Optional<List<BlobSidecar>> blobSidecarsFromStore =
        store.getBlobSidecarsIfAvailable(slotAndBlockRoot);

    assertThat(blobSidecarsFromStore)
        .hasValueSatisfying(blobSidecars -> assertThat(blobSidecars).isNotEmpty());

    final SafeFuture<Optional<SignedBeaconBlock>> signedBlock =
        store.retrieveSignedBlock(signedBlockAndState.getRoot());

    assertThat(signedBlock)
        .isCompletedWithValueMatching(Optional::isPresent, "Result must be present")
        .isCompletedWithValueMatching(
            signedBeaconBlock ->
                signedBeaconBlock
                    .orElseThrow()
                    .getSignedBeaconBlock()
                    .orElseThrow()
                    .equals(signedBlockAndState.getBlock()),
            " Block must match")
        .isCompletedWithValueMatching(
            signedBeaconBlock ->
                signedBeaconBlock
                    .orElseThrow()
                    .getBeaconBlock()
                    .orElseThrow()
                    .getBody()
                    .getOptionalBlobKzgCommitments()
                    .orElseThrow()
                    .asList()
                    .containsAll(
                        blobSidecarsFromStore.get().stream()
                            .map(blob -> new SszKZGCommitment(blob.getKZGCommitment()))
                            .toList()),
            "Blob sidecars must match");
  }

  @Test
  public void retrieveSignedBlock_withEmptyBlobs() {
    final UpdatableStore store = createGenesisStore();
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState signedBlockAndState =
        chainBuilder.generateBlockAtSlot(
            slot,
            ChainBuilder.BlockOptions.create()
                .setGenerateRandomBlobs(false)
                .setStoreBlobSidecars(false));

    addBlock(store, signedBlockAndState);

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState,
                    chainBuilder.getBlobSidecars(blockAndState.getBlock().getRoot()),
                    spec.calculateBlockCheckpoints(blockAndState.getState())));
    tx.commit().join();

    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(slot, signedBlockAndState.getBlock().getRoot());
    final Optional<List<BlobSidecar>> blobSidecarsFromStore =
        store.getBlobSidecarsIfAvailable(slotAndBlockRoot);

    assertThat(blobSidecarsFromStore)
        .hasValueSatisfying(blobSidecars -> assertThat(blobSidecars).isEmpty());

    final SafeFuture<Optional<SignedBeaconBlock>> signedBlock =
        store.retrieveSignedBlock(signedBlockAndState.getRoot());

    assertThat(signedBlock)
        .isCompletedWithValueMatching(Optional::isPresent, "Result must be present")
        .isCompletedWithValueMatching(
            signedBeaconBlock ->
                signedBeaconBlock
                    .orElseThrow()
                    .getSignedBeaconBlock()
                    .orElseThrow()
                    .equals(signedBlockAndState.getBlock()),
            " Block must match")
        .isCompletedWithValueMatching(
            signedBeaconBlockAndBlobsSidecar ->
                signedBeaconBlockAndBlobsSidecar
                    .orElseThrow()
                    .getBeaconBlock()
                    .orElseThrow()
                    .getBody()
                    .getOptionalBlobKzgCommitments()
                    .orElseThrow()
                    .isEmpty(),
            "Blob sidecars must be empty");
  }

  @Test
  public void retrieveSignedBlock_shouldReturnEmptyIfBlockNotPresent() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState =
        chainBuilder.generateBlockAtSlot(
            1, ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));

    final SafeFuture<Optional<SignedBeaconBlock>> signedBeaconBlock =
        store.retrieveSignedBlock(blockAndState.getRoot());

    assertThat(signedBeaconBlock)
        .isCompletedWithValueMatching(Optional::isEmpty, "Result must be empty");
  }

  @Test
  public void retrieveEarliestBlobSidecarSlot_shouldReturnUpdatedValue() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final UpdatableStore store =
        createGenesisStore(
            () ->
                SafeFuture.completedFuture(storageSystem.database().getEarliestBlobSidecarSlot()));

    assertThat(store.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(
            maybeEarliestBlobSidecarSlot ->
                maybeEarliestBlobSidecarSlot.isPresent()
                    && maybeEarliestBlobSidecarSlot.get().equals(UInt64.ZERO));

    storageSystem
        .chainUpdater()
        .advanceChainUntil(
            10,
            ChainBuilder.BlockOptions.create()
                .setGenerateRandomBlobs(true)
                .setStoreBlobSidecars(true));

    assertThat(store.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(
            maybeEarliestBlobSidecarSlot ->
                maybeEarliestBlobSidecarSlot.isPresent()
                    && maybeEarliestBlobSidecarSlot.get().equals(UInt64.ZERO));

    storageSystem
        .database()
        .pruneOldestBlobSidecars(UInt64.valueOf(5), 3, BlobSidecarsArchiver.NOOP);

    assertThat(store.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(
            maybeEarliestBlobSidecarSlot ->
                maybeEarliestBlobSidecarSlot.isPresent()
                    && maybeEarliestBlobSidecarSlot.get().equals(UInt64.valueOf(4)));
  }

  private void setProtoNodeDataForBlock(
      final SignedBlockAndState blockAndState,
      final BlockCheckpoints headCheckpoint,
      final BlockCheckpoints parentCheckpoint) {
    final Bytes32 root = blockAndState.getRoot();
    final Bytes32 parentRoot = blockAndState.getParentRoot();
    final ProtoNodeData protoNodeData =
        new ProtoNodeData(
            UInt64.ONE,
            root,
            blockAndState.getParentRoot(),
            blockAndState.getStateRoot(),
            UInt64.ZERO,
            Bytes32.random(),
            UInt64.ZERO,
            ProtoNodeValidationStatus.VALID,
            headCheckpoint,
            UInt64.ZERO,
            ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
    final ProtoNodeData parentNodeData =
        new ProtoNodeData(
            UInt64.ZERO,
            parentRoot,
            Bytes32.random(),
            blockAndState.getStateRoot(),
            UInt64.ZERO,
            Bytes32.random(),
            UInt64.ZERO,
            ProtoNodeValidationStatus.VALID,
            parentCheckpoint,
            UInt64.ZERO,
            ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
    when(dummyForkChoiceStrategy.getBlockData(root)).thenReturn(Optional.of(protoNodeData));
    when(dummyForkChoiceStrategy.getBlockData(parentRoot)).thenReturn(Optional.of(parentNodeData));
  }

  @Test
  public void epochStatesCacheMostRecentlyAddedStates() {
    final int cacheSize = 2;
    final StoreConfig pruningOptions =
        StoreConfig.builder()
            .checkpointStateCacheSize(cacheSize)
            .blockCacheSize(cacheSize)
            .stateCacheSize(cacheSize)
            .epochStateCacheSize(cacheSize)
            .build();

    final UpdatableStore store = createGenesisStore(pruningOptions);

    chainBuilder.generateBlocksUpToSlot(25);
    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates(0, 25)
        .forEach(
            blockAndState -> {
              tx.putBlockAndState(
                  blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
            });
    tx.commit().join();

    final Store s = (Store) store;
    assertThat(store.retrieveBlockState(stateAndBlockAtSlot(16, chainBuilder))).isCompleted();
    assertThat(store.retrieveBlockState(stateAndBlockAtSlot(24, chainBuilder))).isCompleted();
    assertThat(store.retrieveBlockState(stateAndBlockAtSlot(8, chainBuilder))).isCompleted();
    assertThat(
            s.getEpochStates().orElseThrow().values().stream()
                .map(StateAndBlockSummary::getSlot)
                .map(UInt64::intValue)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(24, 8);
  }

  private SlotAndBlockRoot stateAndBlockAtSlot(final int i, final ChainBuilder chainBuilder) {
    return new SlotAndBlockRoot(UInt64.valueOf(i), chainBuilder.getBlockAtSlot(i).getRoot());
  }

  @Test
  public void retrieveBlock_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final BeaconBlock expectedBlock = blockAndState.getBlock().getMessage();
          SafeFuture<Optional<BeaconBlock>> result = store.retrieveBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block %s", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveBlockAndState_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<SignedBlockAndState>> result = store.retrieveBlockAndState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block and state at %s", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState));
        });
  }

  @Test
  public void retrieveBlockState_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<BeaconState>> result = store.retrieveBlockState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("State at %s", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState.getState()));
        });
  }

  @Test
  public void retrieveBlockState_shouldUseStateOnlyAnchorOnlyForSlotTargetedRetrieval()
      throws Exception {
    chainBuilder.generateGenesis();
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState anchorBlock =
        chainBuilder.generateBlockAtSlot(checkpointSlot.minus(1));
    final BeaconState anchorState = spec.processSlots(anchorBlock.getState(), checkpointSlot);
    final AnchorPoint anchor = AnchorPoint.fromInitialState(spec, anchorState);
    final UpdatableStore store =
        StoreBuilder.create()
            .onDiskStoreData(StoreBuilder.forkChoiceStoreBuilder(spec, anchor, UInt64.ZERO))
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(BlockProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .earliestBlobSidecarSlotProvider(EarliestBlobSidecarSlotProvider.NOOP)
            .build();

    assertThat(store.retrieveBlockState(anchor.getRoot())).isCompletedWithValue(Optional.empty());
    assertThat(store.retrieveBlockState(new SlotAndBlockRoot(checkpointSlot, anchor.getRoot())))
        .isCompletedWithValue(Optional.of(anchorState));
    final UInt64 childSlot = checkpointSlot.plus(1);
    assertThat(store.retrieveBlockState(new SlotAndBlockRoot(childSlot, anchor.getRoot())))
        .isCompletedWithValue(Optional.of(spec.processSlots(anchorState, childSlot)));
    assertThatSafeFuture(
            store.retrieveBlockState(new SlotAndBlockRoot(anchorBlock.getSlot(), anchor.getRoot())))
        .isCompletedExceptionallyWith(InvalidCheckpointException.class);
    assertThat(store.retrieveBlockState(anchor.getRoot())).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void retrieveBlockState_shouldReuseGeneratedStateForSameSlotAndParentRoot()
      throws Exception {
    final Spec spec = spy(TestSpecFactory.createMinimalDeneb());
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);
    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(
                roots ->
                    SafeFuture.completedFuture(
                        roots.stream()
                            .map(chainBuilder::getBlock)
                            .flatMap(Optional::stream)
                            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, block -> block))))
            .earliestBlobSidecarSlotProvider(
                () -> SafeFuture.completedFuture(chainBuilder.getEarliestBlobSidecarSlot()))
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .anchor(Optional.empty())
            .genesisTime(genesis.getState().getGenesisTime())
            .time(genesis.getState().getGenesisTime())
            .latestFinalized(AnchorPoint.create(spec, genesisCheckpoint, genesis))
            .justifiedCheckpoint(genesisCheckpoint)
            .bestJustifiedCheckpoint(genesisCheckpoint)
            .blockInformation(
                Map.of(
                    genesis.getRoot(),
                    new StoredBlockMetadata(
                        genesis.getSlot(),
                        genesis.getRoot(),
                        genesis.getParentRoot(),
                        genesis.getStateRoot(),
                        genesis.getExecutionBlockNumber(),
                        genesis.getExecutionBlockHash(),
                        Optional.of(spec.calculateBlockCheckpoints(genesis.getState())))))
            .storeConfig(defaultStoreConfig)
            .votes(Collections.emptyMap())
            .latestCanonicalBlockRoot(Optional.empty())
            .build();
    final SignedBlockAndState parentBlock = chainBuilder.generateBlockAtSlot(UInt64.ONE);
    final StoreTransaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlockAndState(
        parentBlock, spec.calculateBlockCheckpoints(parentBlock.getState()));
    assertThat(transaction.commit()).isCompletedWithValue(null);
    clearInvocations(spec);

    final UInt64 productionSlot = parentBlock.getSlot().plus(2);
    final SlotAndBlockRoot productionStateKey =
        new SlotAndBlockRoot(productionSlot, parentBlock.getRoot());

    // First call mirrors block production generating state for the selected proposer parent.
    final Optional<BeaconState> productionState =
        safeJoin(store.retrieveBlockState(productionStateKey));
    // Second call mirrors block import requesting the same generated state by the same key.
    final Optional<BeaconState> importState =
        safeJoin(store.retrieveBlockState(productionStateKey));

    final BeaconState generatedProductionState = productionState.orElseThrow();
    final BeaconState generatedImportState = importState.orElseThrow();
    assertThat(generatedProductionState.getSlot()).isEqualTo(productionSlot);
    assertThat(generatedImportState.getSlot()).isEqualTo(productionSlot);
    assertThat(generatedImportState).isSameAs(generatedProductionState);
    verify(spec, times(1)).processSlots(parentBlock.getState(), productionSlot);
  }

  @Test
  public void retrieveCheckpointState_withLimitedCache() {
    processCheckpointsWithLimitedCache(
        (store, checkpointState) -> {
          SafeFuture<Optional<BeaconState>> result =
              store.retrieveCheckpointState(checkpointState.getCheckpoint());
          assertThat(result)
              .describedAs("Checkpoint state for checkpoint %s", checkpointState.getCheckpoint())
              .isCompletedWithValue(Optional.of(checkpointState.getState()));
        });
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits() {
    testApplyChangesWhenTransactionCommits(false);
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits_withInterleavedTx() {
    testApplyChangesWhenTransactionCommits(true);
  }

  @Test
  public void retrieveCheckpointState_shouldGenerateCheckpointStates() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, genesisBlockAndState.getRoot());

    final SafeFuture<Optional<BeaconState>> result = store.retrieveCheckpointState(checkpoint);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final BeaconState checkpointState = safeJoin(result).orElseThrow();
    assertThat(checkpointState.getSlot()).isEqualTo(checkpoint.getEpochStartSlot(spec));
    assertThat(checkpointState.getLatestBlockHeader().hashTreeRoot())
        .isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void retrieveCheckpointState_forGenesis() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ZERO, genesisBlockAndState.getRoot());

    final BeaconState baseState = genesisBlockAndState.getState();
    final SafeFuture<Optional<BeaconState>> result =
        store.retrieveCheckpointState(checkpoint, baseState);
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(baseState);
  }

  @Test
  public void retrieveCheckpointState_forEpochPastGenesis() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, genesisBlockAndState.getRoot());

    final BeaconState baseState = genesisBlockAndState.getState();
    final SafeFuture<Optional<BeaconState>> resultFuture =
        store.retrieveCheckpointState(checkpoint, baseState);
    assertThatSafeFuture(resultFuture).isCompletedWithNonEmptyOptional();
    final BeaconState result = safeJoin(resultFuture).orElseThrow();
    assertThat(result.getSlot()).isGreaterThan(baseState.getSlot());
    assertThat(result.getSlot()).isEqualTo(checkpoint.getEpochStartSlot(spec));
    assertThat(result.getLatestBlockHeader().hashTreeRoot()).isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void retrieveCheckpointState_invalidState() {
    final UpdatableStore store = createGenesisStore();

    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    final SignedBlockAndState futureBlockAndState = chainBuilder.generateBlockAtSlot(startSlot);

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureBlockAndState.getRoot());

    assertThatSafeFuture(store.retrieveCheckpointState(checkpoint, futureBlockAndState.getState()))
        .isCompletedExceptionallyWith(InvalidCheckpointException.class);
  }

  @Test
  public void retrieveFinalizedCheckpointAndState() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(UInt64.ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final SafeFuture<CheckpointState> result = store.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(safeJoin(result).getRoot()).isEqualTo(finalizedBlockAndState.getRoot());
    assertThat(safeJoin(result).getState()).isNotEqualTo(finalizedBlockAndState.getState());
    assertThat(safeJoin(result).getState().getSlot())
        .isEqualTo(finalizedBlockAndState.getSlot().plus(1));
  }

  @Test
  public void
      retrieveCheckpointState_shouldThrowInvalidCheckpointExceptionWhenEpochBeforeBlockRoot() {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    final Bytes32 futureRoot = chainBuilder.generateBlockAtSlot(startSlot).getRoot();

    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    tx.commit().join();

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureRoot);
    final SafeFuture<Optional<BeaconState>> result = store.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasCauseInstanceOf(InvalidCheckpointException.class);
  }

  @Test
  public void shouldKeepOnlyMostRecentBlocksInBlockCache() {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch = UInt64.valueOf(5);
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    chainBuilder.generateBlocksUpToSlot(startSlot);

    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    safeJoin(tx.commit());
    final List<SignedBlockAndState> last32 =
        chainBuilder
            .streamBlocksAndStates()
            .dropWhile(
                signedBlockAndState ->
                    signedBlockAndState
                        .getSlot()
                        .isLessThanOrEqualTo(
                            chainBuilder
                                .getLatestBlockAndState()
                                .getSlot()
                                .minus(defaultStoreConfig.getBlockCacheSize())))
            .toList();
    for (final SignedBlockAndState signedBlockAndState : last32) {
      assertThat(store.getBlockIfAvailable(signedBlockAndState.getRoot())).isPresent();
    }
  }

  private BeaconState justifiedState(final UpdatableStore store) {
    return safeJoin(store.retrieveCheckpointState(store.getJustifiedCheckpoint())).orElseThrow();
  }

  private void testApplyChangesWhenTransactionCommits(final boolean withInterleavedTransaction) {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch3 = UInt64.valueOf(4);
    final UInt64 epoch3Slot = spec.computeStartSlotAtEpoch(epoch3);
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);

    final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 initialTimeMillis = store.getTimeInMillis();
    final UInt64 genesisTime = store.getGenesisTime();

    final Checkpoint checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(1));
    final Checkpoint checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(2));
    final Checkpoint checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(3));

    // Start transaction
    final StubStorageUpdateChannelWithDelays updateChannel =
        new StubStorageUpdateChannelWithDelays();
    final StoreTransaction tx = store.startTransaction(updateChannel);
    // Add blocks
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    // Update checkpoints
    tx.setFinalizedCheckpoint(checkpoint1, false);
    tx.setJustifiedCheckpoint(checkpoint2);
    tx.setBestJustifiedCheckpoint(checkpoint3);
    // Update time
    UInt64 firstUpdateTimeMillis = initialTimeMillis.plus(1300);
    tx.setTimeMillis(firstUpdateTimeMillis);
    UInt64 updatedGenesisTime = genesisTime.plus(UInt64.ONE);
    tx.setGenesisTime(updatedGenesisTime);

    // Check that store is not yet updated
    // Check blocks
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(b -> assertThat(store.containsBlock(b.getRoot())).isFalse());
    // Check checkpoints
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(genesisCheckpoint);
    // Check time
    assertThat(store.getTimeSeconds()).isEqualTo(millisToSeconds(initialTimeMillis));
    assertThat(store.getTimeInMillis()).isEqualTo(initialTimeMillis);
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime);

    // Check that transaction is updated
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(
            b ->
                assertThat(tx.retrieveBlockAndState(b.getRoot()))
                    .isCompletedWithValue(Optional.of(b)));
    // Check checkpoints
    assertThat(tx.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(tx.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(tx.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Check time
    assertThat(tx.getTimeSeconds()).isEqualTo(millisToSeconds(firstUpdateTimeMillis));
    assertThat(tx.getTimeInMillis()).isEqualTo(firstUpdateTimeMillis);
    assertThat(tx.getGenesisTime()).isEqualTo(updatedGenesisTime);

    // Commit transaction
    final SafeFuture<Void> txResult = tx.commit();

    final UInt64 expectedTimeMillis;
    final SafeFuture<Void> txResult2;
    if (withInterleavedTransaction) {
      expectedTimeMillis = firstUpdateTimeMillis.plus(1500);
      StoreTransaction tx2 = store.startTransaction(updateChannel);
      tx2.setTimeMillis(expectedTimeMillis);
      txResult2 = tx2.commit();
    } else {
      expectedTimeMillis = firstUpdateTimeMillis;
      txResult2 = SafeFuture.COMPLETE;
    }

    // Complete transactions
    assertThat(updateChannel.getAsyncRunner().countDelayedActions()).isLessThanOrEqualTo(2);
    updateChannel.getAsyncRunner().executeUntilDone();
    assertThat(txResult).isCompleted();
    assertThat(txResult2).isCompleted();

    // Check store is updated
    chainBuilder
        .streamBlocksAndStates(checkpoint3.getEpochStartSlot(spec), chainBuilder.getLatestSlot())
        .forEach(
            b ->
                assertThat(store.retrieveBlockAndState(b.getRoot()))
                    .isCompletedWithValue(Optional.of(b)));
    // Check checkpoints
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Extra checks for finalized checkpoint
    final SafeFuture<CheckpointState> finalizedCheckpointState =
        store.retrieveFinalizedCheckpointAndState();
    assertThat(finalizedCheckpointState).isCompleted();
    assertThat(safeJoin(finalizedCheckpointState).getCheckpoint()).isEqualTo(checkpoint1);
    // Check time
    assertThat(store.getTimeInMillis()).isEqualTo(expectedTimeMillis);
    assertThat(store.getTimeSeconds()).isEqualTo(millisToSeconds(expectedTimeMillis));
    assertThat(store.getGenesisTime()).isEqualTo(updatedGenesisTime);

    // Check store was pruned as expected
    final List<Bytes32> expectedBlockRoots =
        chainBuilder
            .streamBlocksAndStates(checkpoint1.getEpochStartSlot(spec))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    assertThat(store.getOrderedBlockRoots()).containsExactlyElementsOf(expectedBlockRoots);
  }
}

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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult.EXECUTION_CLIENT_OFFLINE;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult.IMPORTED_ALL_BLOCKS;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult.IMPORT_FAILED;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncPreImportBlockChannel;
import tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.SyncProgress;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.StubBatchFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class BatchSyncTest {
  private static final UInt64 BATCH_SIZE = UInt64.valueOf(25);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final InlineEventThread eventThread = new InlineEventThread();

  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final SyncSource syncSource = mock(SyncSource.class);
  private final SyncPreImportBlockChannel syncPreImportBlockChannel =
      mock(SyncPreImportBlockChannel.class);
  private final BatchImporter batchImporter = mock(BatchImporter.class);
  private final StubBatchFactory batches = new StubBatchFactory(eventThread, true);

  private TargetChain targetChain =
      chainWith(
          new SlotAndBlockRoot(UInt64.valueOf(1000), dataStructureUtil.randomBytes32()),
          syncSource);
  private final MultipeerCommonAncestorFinder commonAncestor =
      mock(MultipeerCommonAncestorFinder.class);

  private final BatchSync sync =
      BatchSync.create(
          eventThread,
          asyncRunner,
          recentChainData,
          batchImporter,
          batches,
          BATCH_SIZE.intValue(),
          5,
          commonAncestor,
          timeProvider,
          syncPreImportBlockChannel);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    when(batchImporter.importBatch(any()))
        .thenAnswer(invocation -> batches.getImportResult(invocation.getArgument(0)));
    when(commonAncestor.findCommonAncestor(any()))
        .thenAnswer(
            invocation ->
                completedFuture(spec.computeStartSlotAtEpoch(recentChainData.getFinalizedEpoch())));
  }

  @Test
  void shouldStartSyncFromEmptyDatabase() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(5);

    // Should start from the slot after our finalized epoch
    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);
    final Batch batch4 = batches.get(4);
    assertThatBatch(batch0).hasFirstSlot(ONE);
    assertThatBatch(batch1).hasFirstSlot(batch0.getLastSlot().plus(1));
    assertThatBatch(batch2).hasFirstSlot(batch1.getLastSlot().plus(1));
    assertThatBatch(batch3).hasFirstSlot(batch2.getLastSlot().plus(1));
    assertThatBatch(batch4).hasFirstSlot(batch3.getLastSlot().plus(1));
    batches.forEach(
        batch ->
            assertThatBatch(batch).hasLastSlot(batch.getFirstSlot().plus(BATCH_SIZE).minus(1)));
  }

  @Test
  void shouldImportFirstBatchWhenSecondBatchFormsChain() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    // First two batches come back, each with a block that matches correctly
    final Batch batch1 = batches.get(0);
    batches.receiveBlocks(batch1, block5.getBlock());
    batches.receiveBlocks(batches.get(1), block26.getBlock());

    // Batch1 should now be complete and import
    assertThatBatch(batch1).isComplete();
    assertThatBatch(batch1).isConfirmed();
    assertBatchImported(batch1);
  }

  @Test
  void shouldCancelInProgressImportWhenAborted() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    final SafeFuture<SyncResult> syncResult = sync.syncToChain(targetChain);

    final Batch batch1 = batches.get(0);
    batches.receiveBlocks(batch1, block5.getBlock());
    batches.receiveBlocks(batches.get(1), block26.getBlock());
    final SafeFuture<BatchImportResult> importResult = batches.getImportResult(batch1);

    eventThread.execute(sync::abort);

    assertThat(syncResult).isCompletedWithValue(SyncResult.FAILED);
    assertThat(importResult).isCancelled();
  }

  @Test
  void shouldHandleImportCompletingExceptionallyAsFailedImport() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    final SafeFuture<SyncResult> syncResult = sync.syncToChain(targetChain);

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, block5.getBlock());
    batches.receiveBlocks(batch1, block26.getBlock());
    final SafeFuture<BatchImportResult> importResult = batches.getImportResult(batch0);

    importResult.completeExceptionally(new RuntimeException("Import blew up"));

    // Treated as a failed import: batches forming a chain with it are invalid and get re-requested
    batches.assertMarkedInvalid(batch0);
    batches.assertMarkedInvalid(batch1);
    assertThat(syncResult).isNotDone();

    // And the importing state was released so the re-requested batch can be imported again
    batches.receiveBlocks(batch0, block5.getBlock());
    batches.receiveBlocks(batch1, block26.getBlock());
    verify(batchImporter, times(2)).importBatch(batches.getEventThreadOnlyBatch(batch0));
  }

  @Test
  void shouldNotImportPreviousBatchWhenBoundaryParentExecutionPayloadIsMissing() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(1000);
    final Spec gloasSpec =
        TestSpecFactory.createMinimalGloas(builder -> builder.gloasForkEpoch(gloasForkEpoch));
    final StorageSystem gloasStorageSystem =
        InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE, gloasSpec);
    final ChainBuilder gloasChainBuilder = gloasStorageSystem.chainBuilder();
    final RecentChainData gloasRecentChainData = gloasStorageSystem.recentChainData();
    gloasStorageSystem.chainUpdater().initializeGenesis();
    final StubBatchFactory gloasBatches =
        new StubBatchFactory(eventThread, gloasSpec, BlobSidecarManager.NOOP, true);
    final BatchImporter gloasBatchImporter = mock(BatchImporter.class);
    when(gloasBatchImporter.importBatch(any()))
        .thenAnswer(invocation -> gloasBatches.getImportResult(invocation.getArgument(0)));
    final MultipeerCommonAncestorFinder gloasCommonAncestor =
        mock(MultipeerCommonAncestorFinder.class);

    final UInt64 commonAncestorSlot = gloasSpec.computeStartSlotAtEpoch(gloasForkEpoch).minus(1);
    final UInt64 parentSlot = commonAncestorSlot.plus(BATCH_SIZE);
    final UInt64 childSlot = parentSlot.plus(1);

    for (UInt64 slot = commonAncestorSlot.plus(1);
        slot.isLessThanOrEqualTo(childSlot);
        slot = slot.plus(1)) {
      gloasChainBuilder.generateBlockAtSlot(slot);
    }

    final SignedBeaconBlock childBlock = gloasChainBuilder.getBlockAtSlot(childSlot);
    final TargetChain gloasTargetChain =
        chainWith(new SlotAndBlockRoot(childSlot, childBlock.getRoot()), syncSource);
    when(gloasCommonAncestor.findCommonAncestor(any()))
        .thenReturn(completedFuture(commonAncestorSlot));

    final BatchSync gloasSync =
        BatchSync.create(
            eventThread,
            asyncRunner,
            gloasRecentChainData,
            gloasBatchImporter,
            gloasBatches,
            BATCH_SIZE.intValue(),
            5,
            gloasCommonAncestor,
            timeProvider,
            syncPreImportBlockChannel);

    assertThat(gloasSync.syncToChain(gloasTargetChain)).isNotDone();

    final Batch batch0 = gloasBatches.get(0);
    final Batch batch1 = gloasBatches.get(1);

    gloasBatches.receiveBlocks(
        batch0,
        gloasChainBuilder
            .streamBlocksAndStates(commonAncestorSlot.plus(1), parentSlot)
            .map(SignedBlockAndState::getBlock)
            .toArray(SignedBeaconBlock[]::new));
    gloasBatches.receiveExecutionPayloads(
        batch0,
        gloasChainBuilder
            .streamExecutionPayloads(commonAncestorSlot.plus(1), parentSlot)
            .filter(executionPayload -> !executionPayload.getSlot().equals(parentSlot))
            .toArray(SignedExecutionPayloadEnvelope[]::new));

    gloasBatches.receiveBlocks(batch1, childBlock);
    gloasBatches.receiveExecutionPayloads(
        batch1, gloasChainBuilder.getExecutionPayloadAtSlot(childSlot).orElseThrow());

    gloasBatches.assertMarkedInvalid(batch0);
    verify(gloasBatchImporter, never()).importBatch(gloasBatches.getEventThreadOnlyBatch(batch0));
  }

  @Test
  void shouldMarkEmptyBatchesAsCompleteAndConfirmedWhenLaterBatchMatchesChainStart() {
    final SignedBeaconBlock block = chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    // First batch is empty
    batches.receiveBlocks(batch0);
    // Second batch contains the first block
    batches.receiveBlocks(batch1, block);

    assertThatBatch(batch0).isConfirmedAsEmpty();
  }

  @Test
  void shouldResumeSyncFromCommonAncestorAfterRestart() {
    storageSystem.chainUpdater().finalizeEpoch(ONE);
    final UInt64 commonAncestorSlot = UInt64.valueOf(50);
    when(commonAncestor.findCommonAncestor(targetChain))
        .thenReturn(completedFuture(commonAncestorSlot));
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThatBatch(batches.get(0)).hasFirstSlot(commonAncestorSlot.plus(1));
  }

  @Test
  void shouldFailSyncWhenTargetChainHasNoPeersAndThereAreNoOutstandingRequests() {
    final TargetChains targetChains = new TargetChains(mock(SettableLabelledGauge.class), "target");
    targetChains.onPeerStatusUpdated(syncSource, targetChain.getChainHead());
    targetChain = targetChains.streamChains().findFirst().orElseThrow();

    // Start the sync
    final SafeFuture<SyncResult> result = sync.syncToChain(targetChain);

    // Then the last peer is moved off that chain but we keep waiting for pending requests
    targetChains.onPeerDisconnected(syncSource);
    assertThat(result).isNotDone();
    final int originalBatchCount = batches.size();

    // Next time the sync progresses, it aborts because there are no more peers.
    batches.receiveBlocks(batches.get(0));
    assertThat(result).isCompletedWithValue(SyncResult.FAILED);
    assertThat(batches).hasSize(originalBatchCount); // No more batches created
  }

  @Test
  void shouldNotRequestBlocksPastTargetChainHead() {
    final UInt64 headSlot = BATCH_SIZE.times(3).minus(5);
    targetChain =
        chainWith(new SlotAndBlockRoot(headSlot, dataStructureUtil.randomBytes32()), syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(3);
    assertThatBatch(batches.get(2)).hasLastSlot(headSlot);
  }

  @Test
  void shouldConfirmAndImportBatchWhenItEndsWithTargetChainHeadBlock() {
    final UInt64 headSlot = BATCH_SIZE.times(2).minus(5);
    final SignedBeaconBlock block3 = chainBuilder.generateBlockAtSlot(3).getBlock();
    final SignedBeaconBlock headBlock = chainBuilder.generateBlockAtSlot(headSlot).getBlock();
    targetChain = chainWith(new SlotAndBlockRoot(headSlot, headBlock.getRoot()), syncSource);
    final SafeFuture<SyncResult> result = sync.syncToChain(targetChain);
    assertThat(result).isNotDone();

    assertThat(batches).hasSize(2);
    final Batch batch1 = batches.get(0);
    final Batch batch2 = batches.get(1);

    batches.receiveBlocks(batch1, block3);
    batches.receiveBlocks(batch2, headBlock);
    assertThat(result).isNotDone();

    // Both batches should be imported
    assertBatchImported(batch1);

    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    assertThat(result).isNotDone();

    assertBatchImported(batch2);
    batches.getImportResult(batch2).complete(IMPORTED_ALL_BLOCKS);
    assertThat(result).isCompletedWithValue(SyncResult.COMPLETE);
  }

  @Test
  void shouldMarkBatchInvalidWhenSlotIsTargetHeadSlotAndRootDoesNotMatch() {
    final UInt64 lastSlot = UInt64.valueOf(2);
    targetChain =
        chainWith(new SlotAndBlockRoot(lastSlot, dataStructureUtil.randomBytes32()), syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    assertThatBatch(batch0).hasLastSlot(lastSlot);

    // We get a block in the last slot which doesn't match the target root
    // (it does match the starting point though)
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(lastSlot).getBlock());

    // So the batch must be invalid
    batches.assertMarkedInvalid(batch0);
  }

  @Test
  void shouldContestBatchesWhenLastBlockDoesNotMatchTargetAndHasOnlyEmptyBatchesAfterIt() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock());
    batches.receiveBlocks(batch1);

    // We now have all the blocks but something doesn't match up so either the last block is wrong
    // or the following empty batch shouldn't have been empty.
    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);
  }

  @Test
  void shouldNotContestBatchesWhenAnIncompleteBatchIsFollowedByEmptyBatchesAtEndOfChain() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getFirstSlot()).getBlock());
    batches.receiveBlocks(batch1);

    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
  }

  @Test
  void shouldContestAllBatchesWhenEndSlotIsReachedWithNoBlocksReceived() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(2);
    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(batch0);
    batches.receiveBlocks(batch1);

    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);
  }

  @Test
  void shouldRejectFirstBatchIfItDoesNotBuildOnKnownBlock() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch firstBatch = batches.get(0);
    batches.receiveBlocks(firstBatch, dataStructureUtil.randomSignedBeaconBlock(1));

    batches.assertMarkedInvalid(firstBatch);
  }

  @Test
  void shouldNotImportBatchUntilConfirmed() {
    final SignedBeaconBlock lastBlockOfFirstBatch =
        chainBuilder.generateBlockAtSlot(BATCH_SIZE).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch firstBatch = batches.get(0);
    batches.receiveBlocks(firstBatch, lastBlockOfFirstBatch);

    assertThatBatch(firstBatch).isComplete();
    assertThatBatch(firstBatch).isNotConfirmed();

    // Not imported yet because only the start has been matched
    assertNoBatchesImported();
  }

  @Test
  void shouldConfirmLaterBatchWhenPreviousAndNextBatchFormChain() {
    chainBuilder.generateBlockAtSlot(1);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch3, chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch2).isConfirmed();

    assertNoBatchesImported();
  }

  @Test
  void shouldImportPreviouslyConfirmedBatchesWhenEarlierBatchConfirmed() {
    final SignedBeaconBlock block1 = chainBuilder.generateBlockAtSlot(1).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch3, chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch0).isNotConfirmed();
    assertThatBatch(batch1).isNotConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();

    assertNoBatchesImported();

    // Then we get the request for batch0 back
    batches.receiveBlocks(batch0, block1);

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();
    assertBatchImported(batch0);
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch1);
    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch2);
  }

  @Test
  void shouldNotMarkBatchAsContestedWhenNextBatchIsEmpty() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();

    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch1);

    // Can't confirm either batch yet because the next block is still unknown but also not contested
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch0).isNotConfirmed();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch1).isNotConfirmed();
  }

  @Test
  void shouldMarkBatchAsContestedWhenNextBatchDoesNotLineUp() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    final SignedBeaconBlock batch1Block =
        chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock();

    // Receive blocks that don't line up
    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch1, dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.plus(1)));

    assertNoBatchesImported();
    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);

    // Both batches now request the same range from a different peer
    batches.receiveBlocks(batch0, batch0Block); // Batch 0 is unchanged
    batches.receiveBlocks(batch1, batch1Block); // Batch 1 now gives us valid data

    assertThatBatch(batch0).isConfirmed();
  }

  @Test
  void shouldNotMarkBatchesAsContestedWhenBlocksDoNotLineUpBecauseOfIncompleteBatchesBetween() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    final SignedBeaconBlock batch1Block =
        chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock();
    final SignedBeaconBlock batch2Block =
        chainBuilder.generateBlockAtSlot(batch2.getLastSlot()).getBlock();

    // Receive blocks from batch 0 and 2 first which won't line up because batch1 is still missing
    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch2, batch2Block);

    assertNoBatchesImported();
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch2).isNotContested();

    // Then when batch 1 arrives, everything lines up.
    batches.receiveBlocks(batch1, batch1Block);

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertThatBatch(batch2).hasConfirmedFirstBlock();
  }

  @Test
  void shouldNotResetOnChainSwitchWhenBlocksDoNotLineUpBecauseOfIncompleteBatches() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    // Generate a block between batch 0 and 5 that we won't have
    chainBuilder.generateBlockAtSlot(batch2.getLastSlot());

    // Receive block from batch 0 so we have a block from the first chain to compare to
    batches.receiveBlocks(batch0, batch0Block);
    // Batch 1 is empty to trigger requesting a new batch from the new chain
    batches.receiveBlocks(batch1);

    // Get the first batch from the new chain
    final Batch batch5 = batches.get(5);
    assertThat(batch5.getTargetChain()).isEqualTo(targetChain);
    final SignedBeaconBlock batch5Block =
        chainBuilder.generateBlockAtSlot(batch5.getLastSlot()).getBlock();

    // Receive first blocks from new chain which won't line up because batches are still incomplete
    batches.receiveBlocks(batch5, batch5Block);

    assertNoBatchesImported();
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch5).isNotContested();

    // Should still be optimistically assuming the chains join up
    assertBatchActive(batch0);
    assertBatchActive(batch1);
    assertBatchActive(batch5);
  }

  @Test
  void shouldLimitTheNumberOfBatchesWithBlocksPendingImport() {
    // Avoid the queue of blocks to import getting too long
    // but allow any number of empty batches since we can only confirm blocks, not empty batches
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(5);

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getLastSlot()).getBlock());

    // Don't create more batches even though some are complete because we haven't imported any
    assertThat(batches).hasSize(5);

    // But finding an empty batch allows us to request another one
    batches.receiveBlocks(batch3);
    assertThat(batches).hasSize(6);

    // And when the first batch completes importing, we can request another one
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertThat(batches).hasSize(7);
  }

  @Test
  void shouldMarkAllBatchesInChainAsInvalidWhenBlockFailsToImport() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Receive a sequence of blocks that all form a chain
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(batch1, chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(BATCH_SIZE.times(2).plus(1)).getBlock());
    // Batch3 is on a different chain
    batches.receiveBlocks(
        batch3, dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.times(3).plus(1)));

    // But then it turns out that a block in batch1 was invalid
    batches.getImportResult(batch0).complete(IMPORT_FAILED);

    // So batches 0, 1 and 2 are all invalid because they form a chain.
    batches.assertMarkedInvalid(batch0);
    batches.assertMarkedInvalid(batch1);
    batches.assertMarkedInvalid(batch2);

    // Batch 3 is still unknown because it didn't line up with the others
    batches.assertNotMarkedInvalid(batch3);
    assertThatBatch(batch3).isNotContested();

    // The batches are still active because they haven't been successfully imported
    assertBatchActive(batch0);
  }

  @Test
  void shouldHandleBatchWithNoSyncSourceMarkedCompleteBecauseOfLaterBatch() {
    final SafeFuture<SyncResult> syncFuture = sync.syncToChain(targetChain);
    assertThat(syncFuture).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);
    final Batch batch4 = batches.get(4);

    // Found an old common ancestor so we already have blocks up to the start of batch 2
    final SignedBlockAndState bestBlock =
        storageSystem.chainUpdater().advanceChainUntil(batch4.getFirstSlot().longValue());
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    // We receive a block from in batch4 which is a child of an existing block
    // but it's not the common ancestor sync started from
    final SignedBeaconBlock batch4Block = chainBuilder.getBlockAtSlot(batch4.getFirstSlot());
    assertThat(recentChainData.containsBlock(batch4Block.getParentRoot())).isTrue();
    batches.receiveBlocks(batch4, batch4Block);

    // None of the batches should be complete
    assertThatBatch(batch0).isNotComplete();
    assertThatBatch(batch1).isNotComplete();
    assertThatBatch(batch2).isNotComplete();
    assertThatBatch(batch3).isNotComplete();
    assertThatBatch(batch4).isNotComplete();
  }

  @Test
  void shouldConfirmFirstBlockOfFirstBatchWhenParentIsBeforeCommonAncestorSlot() {
    // The common ancestor slot may be an empty slot if the finalized checkpoint was used
    final SignedBeaconBlock firstBlock = chainBuilder.generateBlockAtSlot(5).getBlock();

    assertThat(recentChainData.getSlotForBlockRoot(firstBlock.getParentRoot())).contains(ZERO);

    when(commonAncestor.findCommonAncestor(targetChain))
        .thenReturn(SafeFuture.completedFuture(ONE));

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    batches.receiveBlocks(batch0, firstBlock);

    assertThatBatch(batch0).hasConfirmedFirstBlock();
  }

  @Test
  void shouldRemoveBatchFromActiveSetWhenImportCompletesSuccessfully() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchImported(batch0);

    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);

    assertBatchNotActive(batch0);
  }

  @Test
  void shouldSwitchChains() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch0 = batches.get(0);
    final Batch batch4 = batches.get(4);

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);

    // It should optimistically assume the new chain is an extension of the old one and just keep
    // adding batches of block to the end
    batches.receiveBlocks(batch0);

    assertThat(batches).hasSize(6);
    final Batch batch5 = batches.get(5);
    assertThatBatch(batch5).hasFirstSlot(batch4.getLastSlot().plus(1));
    assertThat(secondSyncResult).isNotDone();
  }

  @Test
  void shouldRestartSyncFromCommonAncestorWhenNewChainShorterThanCurrentBatches() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch0 = batches.get(0);
    final Batch batch4 = batches.get(4);
    batches.clearBatchList();

    targetChain =
        chainWith(
            new SlotAndBlockRoot(batch4.getLastSlot().minus(2), dataStructureUtil.randomBytes32()),
            syncSource);
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);

    // There's no way the new chain extends the previous one so it should start from scratch
    assertThat(batches).hasSize(5);
    assertThat(batches.get(0)).isNotEqualTo(batch0);
    assertThat(secondSyncResult).isNotDone();
  }

  @Test
  void shouldFailSyncWhenFindingNewCommonAncestorFailsAfterSwitchingChains() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch4 = batches.get(4);

    targetChain =
        chainWith(
            new SlotAndBlockRoot(batch4.getLastSlot().minus(2), dataStructureUtil.randomBytes32()),
            syncSource);
    when(commonAncestor.findCommonAncestor(targetChain))
        .thenReturn(
            SafeFuture.failedFuture(new RuntimeException("Failed to find new common ancestor")));
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);
    assertThat(secondSyncResult).isCompletedExceptionally();
  }

  @Test
  void shouldRestartSyncFromCommonAncestorWhenBatchFromNewChainDoesNotLineUp() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch0 = batches.get(0);
    final Batch batch4 = batches.get(4);

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);

    // It should optimistically assume the new chain is an extension of the old one and just keep
    // adding batches of block to the end
    batches.receiveBlocks(batch0);

    assertThat(batches).hasSize(6);
    final Batch batch5 = batches.get(5);
    assertThatBatch(batch5).hasFirstSlot(batch4.getLastSlot().plus(1));

    // We get the last block we requested from the original chain
    batches.receiveBlocks(batch4, dataStructureUtil.randomSignedBeaconBlock(batch4.getLastSlot()));

    // The sync is going to recreate all the early batches so clear out list to make it easier
    // to keep track
    final List<Batch> originalBatches = batches.clearBatchList();

    // Setup an expected common ancestor to start syncing the new chain from
    final SafeFuture<UInt64> commonAncestorFuture = new SafeFuture<>();
    when(commonAncestor.findCommonAncestor(targetChain)).thenReturn(commonAncestorFuture);

    // And then get the first block of the new chain which doesn't line up
    // So we now know the new chain doesn't extend the old one
    batches.receiveBlocks(batch5, dataStructureUtil.randomSignedBeaconBlock(batch5.getFirstSlot()));

    // We should not apply any penalties because peers didn't claim it was the same chain
    originalBatches.forEach(
        batch -> {
          assertThatBatch(batch).isNotContested();
          batches.assertNotMarkedInvalid(batch);
        });

    // Should try to find the common ancestor with the new chain and not create any batches yet
    verify(commonAncestor).findCommonAncestor(targetChain);
    assertThat(batches).hasSize(0);

    // Then the common ancestor is found
    final UInt64 commonAncestorSlot = UInt64.valueOf(70);
    commonAncestorFuture.complete(commonAncestorSlot);

    // Should have recreated the batches from finalized epoch again
    assertThat(batches).hasSize(5);
    assertThatBatch(batches.get(0)).hasFirstSlot(commonAncestorSlot.plus(1));
    assertThat(secondSyncResult).isNotDone();
  }

  @Test
  void shouldImportNextConfirmedBatchWhenFirstBatchImportCompletes() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertBatchImported(batch0);

    // Batch 1 doesn't start importing until batch 0 completes
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch1);
  }

  @Test
  void shouldDelaySwitchingToNewChainUntilCurrentImportCompletes() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchImported(batch0);

    final Batch batch4 = batches.get(4);

    // Switch to a new chain
    targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot(), syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    // And return blocks so the new chain doesn't match up.
    batches.receiveBlocks(
        batch4, chainBuilder.generateBlockAtSlot(batch4.getLastSlot()).getBlock());
    final Batch batch5 = batches.get(5);
    batches.receiveBlocks(batch5, dataStructureUtil.randomSignedBeaconBlock(batch5.getFirstSlot()));

    assertBatchNotActive(batch0);

    // All batches should have been dropped and none started until the import completes
    batches.forEach(this::assertBatchNotActive);
    batches.clearBatchList();

    final SignedBlockAndState finalizedBlock = storageSystem.chainUpdater().finalizeEpoch(1);
    batches.getImportResult(batch0).complete(IMPORT_FAILED);

    // Now we should start downloading from the latest finalized checkpoint
    assertThat(batches.get(0).getFirstSlot()).isEqualTo(finalizedBlock.getSlot());
  }

  @Test
  void shouldSwitchToNewChainWhenDelayedImportCompletesExceptionally() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchImported(batch0);

    final Batch batch4 = batches.get(4);

    // Switch to a new chain while batch0 is still importing
    targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot(), syncSource);
    final SafeFuture<SyncResult> newSyncResult = sync.syncToChain(targetChain);
    assertThat(newSyncResult).isNotDone();

    // And return blocks so the new chain doesn't match up.
    batches.receiveBlocks(
        batch4, chainBuilder.generateBlockAtSlot(batch4.getLastSlot()).getBlock());
    final Batch batch5 = batches.get(5);
    batches.receiveBlocks(batch5, dataStructureUtil.randomSignedBeaconBlock(batch5.getFirstSlot()));

    // All batches should have been dropped and none started until the import completes
    batches.forEach(this::assertBatchNotActive);
    batches.clearBatchList();

    final SignedBlockAndState finalizedBlock = storageSystem.chainUpdater().finalizeEpoch(1);
    batches.getImportResult(batch0).completeExceptionally(new RuntimeException("Import blew up"));

    // The failure belongs to the chain we already left, so the new sync continues downloading
    // from the latest finalized checkpoint instead of failing or stalling
    assertThat(newSyncResult).isNotDone();
    assertThat(batches.get(0).getFirstSlot()).isEqualTo(finalizedBlock.getSlot());
  }

  @Test
  void shouldProgressWhenThereAreManyEmptyBatchesInARow() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final int initialBatchCount = batches.size();
    // All the requested batches are empty
    for (int i = 0; i < initialBatchCount; i++) {
      batches.receiveBlocks(batches.get(i));
    }

    // But because they're all empty, it should request more batches
    assertThat(batches).hasSizeGreaterThan(initialBatchCount);

    // And then we get one back with a valid block
    final Batch laterBatch = batches.get(initialBatchCount);
    batches.receiveBlocks(
        laterBatch, chainBuilder.generateBlockAtSlot(laterBatch.getFirstSlot()).getBlock());

    // But nothing gets imported yet because it isn't confirmed.
    verifyNoInteractions(batchImporter);
    assertBatchActive(batches.get(0));

    // Finally it's confirmed
    final Batch confirmingBatch = batches.get(initialBatchCount + 1);
    batches.receiveBlocks(
        confirmingBatch,
        chainBuilder.generateBlockAtSlot(confirmingBatch.getFirstSlot()).getBlock());

    // So all the batches get imported, but because there's no point importing empty batches
    // only laterBatch is passed to the BatchImporter
    assertBatchImported(laterBatch);

    // And when it completes it and all the earlier empty batches are dropped
    batches.getImportResult(laterBatch).complete(IMPORTED_ALL_BLOCKS);
    for (int i = 0; i <= initialBatchCount; i++) {
      assertBatchNotActive(batches.get(i));
    }
  }

  @Test
  void shouldRecordTimeWhenFirstSyncStarts() {
    timeProvider.advanceTimeBySeconds(100);
    assertThat(sync.syncToChain(targetChain)).isNotDone();
    eventThread.execute(
        (Runnable)
            () ->
                assertThat(sync.getLastImportTimerStartPointSeconds())
                    .isEqualTo(timeProvider.getTimeInSeconds()));
  }

  @Test
  void shouldRecordTimeWhenBatchBeginsImporting() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    timeProvider.advanceTimeBySeconds(100);

    // Trigger import of first batch
    final Batch batch1 = batches.get(0);
    batches.receiveBlocks(batch1, block5.getBlock());
    batches.receiveBlocks(batches.get(1), block26.getBlock());
    assertBatchImported(batch1);

    eventThread.execute(
        () -> {
          assertThat(sync.getImportingBatch()).contains(batches.getEventThreadOnlyBatch(batch1));
          assertThat(sync.getLastImportTimerStartPointSeconds())
              .isEqualTo(timeProvider.getTimeInSeconds());
        });
  }

  @Test
  void shouldPauseImportingWhenImportingServiceOffline() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);

    // Receive a sequence of blocks that all form a chain
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());

    // But then it turns out that a batch0 is not processed because importing is offline
    batches.getImportResult(batch0).complete(EXECUTION_CLIENT_OFFLINE);

    // This shouldn't make batches invalid
    batches.assertNotMarkedInvalid(batch0);
    batches.assertNotMarkedInvalid(batch1);
    batches.assertNotMarkedInvalid(batch2);

    // Check that later we still have no changes since importing is still offline
    timeProvider.advanceTimeBySeconds(100);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    assertBatchActive(batch0);
    assertBatchActive(batch1);

    // But then it turns out that an importing goes back online
    batches.resetImportResult(batch0);
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    timeProvider.advanceTimeBySeconds(10);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    assertBatchNotActive(batch0);
    // Because there were several retries for batch0
    verify(batchImporter, atLeastOnce()).importBatch(batches.getEventThreadOnlyBatch(batch0));
    assertBatchImported(batch1);
    verify(batchImporter).importBatch(batches.getEventThreadOnlyBatch(batch1));
    assertBatchNotActive(batch1);
  }

  @Test
  void shouldGetSyncProgress() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    SyncProgress syncProgress = getSyncProgress();

    // downloading
    assertThat(syncProgress.batches()).isEqualTo(5);
    assertThat(syncProgress.downloadingSlots()).isEqualTo(125);
    assertThat(syncProgress.downloadingBatches()).isEqualTo(5);
    assertThat(syncProgress.readySlots()).isZero();
    assertThat(syncProgress.readyBatches()).isZero();
    assertThat(syncProgress.importing()).isFalse();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);
    final Batch batch4 = batches.get(4);

    // complete two batches which form a chain
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock());

    syncProgress = getSyncProgress();

    // partially ready batches
    assertThat(syncProgress.batches()).isEqualTo(5);
    assertThat(syncProgress.downloadingSlots()).isEqualTo(75);
    assertThat(syncProgress.downloadingBatches()).isEqualTo(3);
    assertThat(syncProgress.readySlots()).isEqualTo(50);
    assertThat(syncProgress.readyBatches()).isEqualTo(2);
    assertThat(syncProgress.importing()).isTrue();

    // complete other batches
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch3, chainBuilder.generateBlockAtSlot(batch3.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch4, chainBuilder.generateBlockAtSlot(batch4.getLastSlot()).getBlock());

    syncProgress = getSyncProgress();

    // all batches are ready
    assertThat(syncProgress.batches()).isEqualTo(5);
    assertThat(syncProgress.downloadingSlots()).isEqualTo(0);
    assertThat(syncProgress.downloadingBatches()).isEqualTo(0);
    assertThat(syncProgress.readySlots()).isEqualTo(125);
    assertThat(syncProgress.readyBatches()).isEqualTo(5);
    assertThat(syncProgress.importing()).isTrue();

    // import batches
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    batches.getImportResult(batch2).complete(IMPORTED_ALL_BLOCKS);
    batches.getImportResult(batch3).complete(IMPORTED_ALL_BLOCKS);

    asyncRunner.executeDueActionsRepeatedly();

    syncProgress = getSyncProgress();

    // on to the next set of batches
    assertThat(syncProgress.batches()).isEqualTo(5);
    assertThat(syncProgress.fromSlot()).isEqualTo(UInt64.valueOf(101));
    assertThat(syncProgress.toSlot()).isEqualTo(UInt64.valueOf(225));
    assertThat(syncProgress.downloadingSlots()).isEqualTo(100);
    assertThat(syncProgress.downloadingBatches()).isEqualTo(4);
    assertThat(syncProgress.readySlots()).isEqualTo(25);
    assertThat(syncProgress.readyBatches()).isEqualTo(1);
    assertThat(syncProgress.importing()).isFalse();
  }

  private void assertBatchNotActive(final Batch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(
        (Runnable)
            () -> assertThat(sync.isActiveBatch(batches.getEventThreadOnlyBatch(batch))).isFalse());
  }

  private void assertBatchActive(final Batch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(
        (Runnable)
            () -> assertThat(sync.isActiveBatch(batches.getEventThreadOnlyBatch(batch))).isTrue());
  }

  private void assertBatchImported(final Batch batch) {
    verify(batchImporter).importBatch(batches.getEventThreadOnlyBatch(batch));
    verifyNoMoreInteractions(batchImporter);
  }

  private void assertNoBatchesImported() {
    verifyNoInteractions(batchImporter);
  }

  private SyncProgress getSyncProgress() {
    final Optional<SyncProgress> syncProgress = SafeFutureAssert.safeJoin(sync.getSyncProgress());
    assertThat(syncProgress).isPresent();
    return syncProgress.get();
  }
}

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.StubSyncSource;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;

class BatchImporterTest {
  private final UInt64 gloasForkEpoch = UInt64.valueOf(100);
  private final Spec spec =
      TestSpecFactory.createMinimalGloas(builder -> builder.gloasForkEpoch(gloasForkEpoch));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockImporter blockImporter = mock(BlockImporter.class);
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Batch batch = mock(Batch.class);
  final SyncSource syncSource = mock(SyncSource.class);

  private final BatchImporter importer =
      new BatchImporter(
          blockImporter, blockBlobSidecarsTrackersPool, executionPayloadManager, asyncRunner);

  @BeforeEach
  public void setup() {
    when(batch.getSource()).thenReturn(Optional.of(syncSource));
    when(batch.getBlobSidecarsByBlockRoot()).thenReturn(Map.of());
    when(batch.getExecutionPayloadsByBlockRoot()).thenReturn(Map.of());
  }

  @Test
  void shouldImportBlocksInOrder() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult3 = new SafeFuture<>();
    final List<SignedBeaconBlock> blocks = new ArrayList<>(List.of(block1, block2, block3));
    when(batch.getBlocks()).thenReturn(blocks);
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);
    when(blockImporter.importBlock(block3)).thenReturn(importResult3);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blockBlobSidecarsTrackersPool);

    // We should have copied the data to avoid accessing it from other threads
    verify(batch).getBlocks();
    verify(batch).getBlobSidecarsByBlockRoot();
    verify(batch).getExecutionPayloadsByBlockRoot();
    verify(batch).getSource();

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();
    blockImportedSuccessfully(block2, importResult2);
    assertThat(result).isNotDone();
    blockImportedSuccessfully(block3, importResult3);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);

    // And check we didn't touch the batch from a different thread
    verifyNoMoreInteractions(batch);
  }

  @Test
  void shouldCancelActiveBlockImport() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block1));
    assertThat(result.cancel(true)).isTrue();

    assertThat(result).isCancelled();
    assertThat(importResult1).isCancelled();
    verify(blockImporter, never()).importBlock(block2);
  }

  @Test
  void shouldImportBlobSidecarsAndBlocksInOrder() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    final List<BlobSidecar> blobSidecars1 = dataStructureUtil.randomBlobSidecarsForBlock(block1);
    final List<BlobSidecar> blobSidecars2 = dataStructureUtil.randomBlobSidecarsForBlock(block2);

    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();

    final List<SignedBeaconBlock> blocks = new ArrayList<>(List.of(block1, block2));
    final Map<Bytes32, List<BlobSidecar>> blobSidecars =
        Map.of(block1.getRoot(), blobSidecars1, block2.getRoot(), blobSidecars2);

    when(batch.getBlocks()).thenReturn(blocks);
    when(batch.getBlobSidecarsByBlockRoot()).thenReturn(blobSidecars);

    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blockBlobSidecarsTrackersPool);

    // We should have copied the data to avoid accessing it from other threads
    verify(batch).getBlocks();
    verify(batch).getBlobSidecarsByBlockRoot();
    verify(batch).getExecutionPayloadsByBlockRoot();
    verify(batch).getSource();

    asyncRunner.executeQueuedActions();

    blobSidecarsImportedSuccessfully(block1, blobSidecars1);
    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();
    blobSidecarsImportedSuccessfully(block2, blobSidecars2);
    blockImportedSuccessfully(block2, importResult2);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);

    // And check we didn't touch the batch from a different thread
    verifyNoMoreInteractions(batch);
  }

  @Test
  void shouldStopImportingAfterFailure() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult3 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2, block3));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);
    when(blockImporter.importBlock(block3)).thenReturn(importResult3);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    importResult2.complete(
        BlockImportResult.failedStateTransition(new Exception("Naughty block!")));
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  void shouldDisconnectPeersForWeakSubjectivityViolation() {
    when(syncSource.disconnectCleanly(any())).thenReturn(SafeFuture.COMPLETE);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    // Import bad block
    importResult2.complete(BlockImportResult.FAILED_WEAK_SUBJECTIVITY_CHECKS);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
    verify(batch).getSource();
    verify(syncSource).disconnectCleanly(DisconnectReason.REMOTE_FAULT);

    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  void shouldNotDisconnectPeersWhenServiceOffline() {
    when(syncSource.disconnectCleanly(any())).thenReturn(SafeFuture.COMPLETE);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    // Import failed due to execution being offline
    importResult2.complete(BlockImportResult.failedExecutionPayloadExecution(new Error()));
    assertThat(result).isCompletedWithValue(BatchImportResult.EXECUTION_CLIENT_OFFLINE);
    verify(batch).getSource();
    verify(syncSource, never()).disconnectCleanly(any());

    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  void shouldImportBlocksAndExecutionPayloadsInOrder() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(1));
    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));

    final SignedExecutionPayloadEnvelope executionPayload1 =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block1);
    final SignedExecutionPayloadEnvelope executionPayload2 =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block2);

    final SafeFuture<BlockImportResult> blockImportResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> blockImportResult2 = new SafeFuture<>();

    final SafeFuture<ExecutionPayloadImportResult> executionPayloadImportResult1 =
        new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> executionPayloadImportResult2 =
        new SafeFuture<>();

    final List<SignedBeaconBlock> blocks = new ArrayList<>(List.of(block1, block2));
    final Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloads =
        Map.of(block1.getRoot(), executionPayload1, block2.getRoot(), executionPayload2);

    when(batch.getBlocks()).thenReturn(blocks);
    when(batch.getExecutionPayloadsByBlockRoot()).thenReturn(executionPayloads);

    when(blockImporter.importBlock(block1)).thenReturn(blockImportResult1);
    when(blockImporter.importBlock(block2)).thenReturn(blockImportResult2);
    when(executionPayloadManager.importExecutionPayload(executionPayload1, false))
        .thenReturn(executionPayloadImportResult1);
    when(executionPayloadManager.importExecutionPayload(executionPayload2, false))
        .thenReturn(executionPayloadImportResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blockBlobSidecarsTrackersPool);
    verifyNoInteractions(executionPayloadManager);

    // We should have copied the data to avoid accessing it from other threads
    verify(batch).getBlocks();
    verify(batch).getBlobSidecarsByBlockRoot();
    verify(batch).getExecutionPayloadsByBlockRoot();
    verify(batch).getSource();

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, blockImportResult1);
    executionPayloadImportedSuccessfully(executionPayload1, executionPayloadImportResult1);
    assertThat(result).isNotDone();

    blockImportedSuccessfully(block2, blockImportResult2);
    executionPayloadImportedSuccessfully(executionPayload2, executionPayloadImportResult2);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);

    // And check we didn't touch the batch from a different thread
    verifyNoMoreInteractions(batch);
  }

  @Test
  void shouldImportBlobSidecarsAndExecutionPayloadForSameBlock() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(1));
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block);

    final SafeFuture<BlockImportResult> blockImportResult = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> executionPayloadImportResult =
        new SafeFuture<>();

    when(batch.getBlocks()).thenReturn(List.of(block));
    when(batch.getBlobSidecarsByBlockRoot()).thenReturn(Map.of(block.getRoot(), blobSidecars));
    when(batch.getExecutionPayloadsByBlockRoot())
        .thenReturn(Map.of(block.getRoot(), executionPayload));

    when(blockImporter.importBlock(block)).thenReturn(blockImportResult);
    when(executionPayloadManager.importExecutionPayload(executionPayload, false))
        .thenReturn(executionPayloadImportResult);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blockBlobSidecarsTrackersPool);
    verifyNoInteractions(executionPayloadManager);

    asyncRunner.executeQueuedActions();

    blobSidecarsImportedSuccessfully(block, blobSidecars);
    blockImportedSuccessfully(block, blockImportResult);
    executionPayloadImportedSuccessfully(executionPayload, executionPayloadImportResult);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);
  }

  @Test
  void shouldRecoverMissingParentExecutionPayloadByRootAndRetryImport() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<Optional<SignedExecutionPayloadEnvelope>> parentFetch = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> parentImport = new SafeFuture<>();
    final SafeFuture<BlockImportResult> retryImport = new SafeFuture<>();

    when(blockImporter.importBlock(block)).thenReturn(firstImport).thenReturn(retryImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(parentFetch);
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(parentImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    // First import fails because the parent's execution payload envelope was not delivered
    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    // The parent's execution payload envelope is fetched by root and imported
    verify(syncSource).requestExecutionPayloadEnvelopeByRoot(block.getParentRoot());
    parentFetch.complete(Optional.of(parentExecutionPayload));
    ignoreFuture(
        verify(executionPayloadManager).importExecutionPayload(parentExecutionPayload, false));
    parentImport.complete(ExecutionPayloadImportResult.successful(parentExecutionPayload));

    // The block import is retried and now succeeds
    ignoreFuture(verify(blockImporter, times(2)).importBlock(block));
    retryImport.complete(BlockImportResult.successful(block));

    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);
  }

  @Test
  void shouldFailBatchWhenMissingParentExecutionPayloadCannotBeRecovered() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));

    // StubSyncSource returns Optional.empty() for by-root requests, simulating a parent execution
    // payload envelope that cannot be recovered
    final StubSyncSource stubSyncSource = new StubSyncSource();
    when(batch.getSource()).thenReturn(Optional.of(stubSyncSource));
    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    // No envelope available: the block is not retried and the batch import fails
    stubSyncSource.assertRequestedExecutionPayloadEnvelopeByRoot(block.getParentRoot());
    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
  }

  @Test
  void shouldNotRetryBlockWhenRecoveredParentExecutionPayloadFailsToImport() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> parentImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentExecutionPayload)));
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(parentImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    // Envelope is fetched but fails to import: the block must NOT be re-imported
    ignoreFuture(
        verify(executionPayloadManager).importExecutionPayload(parentExecutionPayload, false));
    parentImport.complete(
        ExecutionPayloadImportResult.failedVerification(new IllegalStateException("bad payload")));

    // Didn't retry block import because execution payload import failed
    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
  }

  @Test
  void shouldForwardRecoveredParentExecutionPayloadExecutionFailure() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());
    final RuntimeException executionFailure = new RuntimeException("execution failed");

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> parentImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentExecutionPayload)));
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(parentImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    ignoreFuture(
        verify(executionPayloadManager).importExecutionPayload(parentExecutionPayload, false));
    parentImport.complete(ExecutionPayloadImportResult.failedExecution(executionFailure));

    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    assertThat(result).isCompletedWithValue(BatchImportResult.EXECUTION_CLIENT_OFFLINE);
  }

  @Test
  void shouldForwardRecoveredParentExecutionPayloadDataUnavailableFailure() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());
    final RuntimeException dataUnavailableFailure = new RuntimeException("data unavailable");

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> parentImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentExecutionPayload)));
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(parentImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    ignoreFuture(
        verify(executionPayloadManager).importExecutionPayload(parentExecutionPayload, false));
    parentImport.complete(
        ExecutionPayloadImportResult.failedDataAvailabilityCheckNotAvailable(
            Optional.of(dataUnavailableFailure)));

    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    assertThat(result).isCompletedWithValue(BatchImportResult.DATA_NOT_AVAILABLE);
  }

  @Test
  void shouldFailBatchWhenParentExecutionPayloadRequestFails() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<Optional<SignedExecutionPayloadEnvelope>> parentFetch = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(parentFetch);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    // The request for the parent's envelope fails: the original failure is reported and the block
    // is not retried
    verify(syncSource).requestExecutionPayloadEnvelopeByRoot(block.getParentRoot());
    parentFetch.completeExceptionally(new RuntimeException("no response from peer"));

    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    verifyNoInteractions(executionPayloadManager);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
  }

  @Test
  void shouldPropagateCancellationOfParentExecutionPayloadRequest() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<Optional<SignedExecutionPayloadEnvelope>> parentFetch = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(parentFetch);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    verify(syncSource).requestExecutionPayloadEnvelopeByRoot(block.getParentRoot());
    // The batch import was cancelled while the parent's envelope was being fetched
    parentFetch.completeExceptionally(new CancellationException("Batch import cancelled"));

    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    verifyNoInteractions(executionPayloadManager);
    assertThatSafeFuture(result).isCompletedExceptionallyWith(CancellationException.class);
  }

  @Test
  void shouldNotMaskFailureOfRecoveredParentExecutionPayloadImport() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());
    final RuntimeException importError = new RuntimeException("payload import blew up");

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<ExecutionPayloadImportResult> parentImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentExecutionPayload)));
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(parentImport);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    ignoreFuture(
        verify(executionPayloadManager).importExecutionPayload(parentExecutionPayload, false));
    parentImport.completeExceptionally(importError);

    // The failure must not be reported as the original block import failure
    ignoreFuture(verify(blockImporter, times(1)).importBlock(block));
    assertThatSafeFuture(result).isCompletedExceptionallyWith(importError);
  }

  @Test
  void shouldNotMaskFailureOfRetriedBlockImport() {
    final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(gloasFirstSlot.plus(2));
    final SignedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(gloasFirstSlot.plus(1).longValue());
    final RuntimeException retryError = new RuntimeException("block import blew up");

    when(batch.getBlocks()).thenReturn(List.of(block));

    final SafeFuture<BlockImportResult> firstImport = new SafeFuture<>();
    final SafeFuture<BlockImportResult> retryImport = new SafeFuture<>();
    when(blockImporter.importBlock(block)).thenReturn(firstImport).thenReturn(retryImport);
    when(syncSource.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentExecutionPayload)));
    when(executionPayloadManager.importExecutionPayload(parentExecutionPayload, false))
        .thenReturn(
            SafeFuture.completedFuture(
                ExecutionPayloadImportResult.successful(parentExecutionPayload)));

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(blockImporter).importBlock(block));
    firstImport.complete(BlockImportResult.FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD);

    ignoreFuture(verify(blockImporter, times(2)).importBlock(block));
    retryImport.completeExceptionally(retryError);

    // The retried import failure must not be swallowed into the original failure
    assertThatSafeFuture(result).isCompletedExceptionallyWith(retryError);
  }

  private void blobSidecarsImportedSuccessfully(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    verify(blockBlobSidecarsTrackersPool).onCompletedBlockAndBlobSidecars(block, blobSidecars);
    verifyNoMoreInteractions(blockBlobSidecarsTrackersPool);
  }

  private void blockImportedSuccessfully(
      final SignedBeaconBlock block, final SafeFuture<BlockImportResult> importResult) {
    ignoreFuture(verify(blockImporter).importBlock(block));
    verifyNoMoreInteractions(blockImporter);
    importResult.complete(BlockImportResult.successful(block));
  }

  private void executionPayloadImportedSuccessfully(
      final SignedExecutionPayloadEnvelope executionPayload,
      final SafeFuture<ExecutionPayloadImportResult> importResult) {
    ignoreFuture(verify(executionPayloadManager).importExecutionPayload(executionPayload, false));
    verifyNoMoreInteractions(executionPayloadManager);
    importResult.complete(ExecutionPayloadImportResult.successful(executionPayload));
  }
}

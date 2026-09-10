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

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;

public class BatchImporter {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockImporter blockImporter;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ExecutionPayloadManager executionPayloadManager;
  private final AsyncRunner asyncRunner;

  public BatchImporter(
      final BlockImporter blockImporter,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ExecutionPayloadManager executionPayloadManager,
      final AsyncRunner asyncRunner) {
    this.blockImporter = blockImporter;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.executionPayloadManager = executionPayloadManager;
    this.asyncRunner = asyncRunner;
  }

  /**
   * Import the blocks and blob sidecars (if any) in the specified batch.
   *
   * <p>Guaranteed to return immediately and perform the import on worker threads.
   *
   * @param batch the batch to import
   * @return a future reporting the result of the import
   */
  public SafeFuture<BatchImportResult> importBatch(final Batch batch) {
    // Copy the data from batch as we're going to use them from off the event thread.
    final CancellableBatchImportFuture result = new CancellableBatchImportFuture();
    final BatchImportContext context =
        new BatchImportContext(
            batch,
            new ArrayList<>(batch.getBlocks()),
            Map.copyOf(batch.getBlobSidecarsByBlockRoot()),
            Map.copyOf(batch.getExecutionPayloadsByBlockRoot()),
            batch.getSource(),
            result);

    checkState(!context.blocks().isEmpty(), "Batch has no blocks to import");
    asyncRunner
        .runAsync(() -> importBlocks(context))
        .finish(result::complete, result::completeExceptionally);
    return result;
  }

  private SafeFuture<BatchImportResult> importBlocks(final BatchImportContext context) {
    if (context.result().isCancelled()) {
      return cancelledBatchImport();
    }
    SafeFuture<SingleImportResult> importResult =
        importBlock(context, context.blocks().getFirst(), 1);
    for (int i = 1; i < context.blocks().size(); i++) {
      final SignedBeaconBlock block = context.blocks().get(i);
      final int blockNumber = i + 1;
      importResult =
          importResult.thenCompose(
              previousResult -> importNextBlock(context, block, blockNumber, previousResult));
    }
    return importResult.thenApply(
        lastImportResult -> toBatchImportResult(context.batch(), lastImportResult));
  }

  private SafeFuture<SingleImportResult> importNextBlock(
      final BatchImportContext context,
      final SignedBeaconBlock block,
      final int blockNumber,
      final SingleImportResult previousResult) {
    if (context.result().isCancelled()) {
      return cancelledBatchImport();
    }
    if (previousResult.isSuccessful()) {
      return importBlock(context, block, blockNumber);
    }
    return SafeFuture.completedFuture(previousResult);
  }

  private <T> SafeFuture<T> cancelledBatchImport() {
    return SafeFuture.failedFuture(new CancellationException("Batch import cancelled"));
  }

  private BatchImportResult toBatchImportResult(
      final Batch batch, final SingleImportResult lastImportResult) {
    if (lastImportResult.isSuccessful()) {
      return BatchImportResult.IMPORTED_ALL_BLOCKS;
    } else if (lastImportResult.failedPayloadExecution()) {
      return BatchImportResult.EXECUTION_CLIENT_OFFLINE;
    } else if (lastImportResult.dataNotAvailable) {
      return BatchImportResult.DATA_NOT_AVAILABLE;
    }
    LOG.debug(
        "Failed to import batch {}: {}",
        batch,
        lastImportResult.failureReason(),
        lastImportResult.failureCause().orElse(null));
    return BatchImportResult.IMPORT_FAILED;
  }

  private SafeFuture<SingleImportResult> importBlock(
      final BatchImportContext context, final SignedBeaconBlock block, final int blockNumber) {
    LOG.debug(
        "Importing block {} of {} during syncing for slot {} and root {}",
        blockNumber,
        context.blocks().size(),
        block.getSlot(),
        block.getRoot());
    final Bytes32 blockRoot = block.getRoot();
    final Optional<SignedExecutionPayloadEnvelope> executionPayload =
        Optional.ofNullable(context.executionPayloadsByBlockRoot().get(blockRoot));
    if (context.blobSidecarsByBlockRoot().containsKey(blockRoot)) {
      final List<BlobSidecar> blobSidecars = context.blobSidecarsByBlockRoot().get(blockRoot);
      LOG.trace(
          "Sending {} blob sidecars to the pool during syncing for block with root {}",
          blobSidecars.size(),
          blockRoot);
      // Add blob sidecars to the pool in order for them to be available when the block is being
      // imported
      blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, blobSidecars);
    }

    return importBlockWithParentExecutionPayloadRecovery(
            block, context.source().orElseThrow(), context.result())
        .thenCompose(
            blockImportResult ->
                handleBlockImportResult(
                    block,
                    executionPayload,
                    context.source.orElseThrow(),
                    context.result(),
                    blockImportResult));
  }

  private SafeFuture<SingleImportResult> handleBlockImportResult(
      final SignedBeaconBlock block,
      final Optional<SignedExecutionPayloadEnvelope> executionPayload,
      final SyncSource source,
      final CancellableBatchImportFuture batchImportFuture,
      final BlockImportResult blockImportResult) {
    if (blockImportResult.getFailureReason()
        == BlockImportResult.FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS) {
      LOG.warn(
          "Disconnecting source ({}) for sending block that failed weak subjectivity checks: {}",
          source,
          blockImportResult);
      source.disconnectCleanly(DisconnectReason.REMOTE_FAULT).finishWarn(LOG);
    }
    if (executionPayload.isEmpty() || !blockImportResult.isSuccessful()) {
      return SafeFuture.completedFuture(toSingleImportResult(blockImportResult));
    }
    LOG.trace(
        "Importing execution payload during syncing for slot {} and block root {}",
        block.getSlot(),
        block.getRoot());
    return trackActiveTask(
            executionPayloadManager.importExecutionPayload(executionPayload.get(), false),
            batchImportFuture)
        .thenApply(this::toSingleImportResult);
  }

  private SingleImportResult toSingleImportResult(final BlockImportResult result) {
    return new SingleImportResult(
        result.isSuccessful(),
        result.hasFailedExecutingExecutionPayload(),
        result.isDataNotAvailable(),
        Optional.ofNullable(result.getFailureReason()).map(Enum::name).orElse(null),
        result.getFailureCause());
  }

  private SingleImportResult toSingleImportResult(final ExecutionPayloadImportResult result) {
    return new SingleImportResult(
        result.isSuccessful(),
        result.hasFailedExecution(),
        result.isDataNotAvailable(),
        Optional.ofNullable(result.getFailureReason()).map(Enum::name).orElse(null),
        result.getFailureCause());
  }

  private <T> SafeFuture<T> trackActiveTask(
      final SafeFuture<T> task, final CancellableBatchImportFuture batchImportFuture) {
    batchImportFuture.setActiveTask(task);
    return task;
  }

  /**
   * Imports the block, lazily recovering from a missing parent execution payload during sync.
   *
   * <p>When the block fails with {@code UNKNOWN_PARENT_EXECUTION_PAYLOAD} the parent block's
   * execution payload envelope has not been delivered (for example it was the last block of a
   * previous batch, whose envelope is allowed to be deferred). The parent's slot is unknown - the
   * next batch may be built on top of empty slots - so the envelope is fetched by root, imported,
   * and the block import is retried once. If the envelope cannot be fetched or imported, the
   * original failure is returned unless the recovered envelope import fails execution or reports
   * data unavailable, in which case that failure is forwarded to the batch result. Failures of the
   * envelope import itself or of the retried block import are not swallowed - they complete the
   * batch import exceptionally, as does a cancellation of the batch while the envelope is being
   * fetched.
   */
  private SafeFuture<BlockImportResult> importBlockWithParentExecutionPayloadRecovery(
      final SignedBeaconBlock block,
      final SyncSource source,
      final CancellableBatchImportFuture batchImportFuture) {
    return trackActiveTask(blockImporter.importBlock(block), batchImportFuture)
        .thenCompose(
            result ->
                recoverParentExecutionPayloadIfRequired(block, source, batchImportFuture, result));
  }

  private SafeFuture<BlockImportResult> recoverParentExecutionPayloadIfRequired(
      final SignedBeaconBlock block,
      final SyncSource source,
      final CancellableBatchImportFuture batchImportFuture,
      final BlockImportResult result) {
    if (result.getFailureReason()
        != BlockImportResult.FailureReason.UNKNOWN_PARENT_EXECUTION_PAYLOAD) {
      return SafeFuture.completedFuture(result);
    }
    LOG.debug(
        "Recovering missing parent execution payload by root {} for block at slot {}",
        block.getParentRoot(),
        block.getSlot());
    return recoverParentExecutionPayloadByRoot(block, source, result, batchImportFuture);
  }

  private SafeFuture<BlockImportResult> recoverParentExecutionPayloadByRoot(
      final SignedBeaconBlock block,
      final SyncSource source,
      final BlockImportResult result,
      final CancellableBatchImportFuture batchImportFuture) {
    return trackActiveTask(
            source.requestExecutionPayloadEnvelopeByRoot(block.getParentRoot()), batchImportFuture)
        .exceptionallyCompose(error -> handleParentExecutionPayloadRequestError(block, error))
        .thenCompose(
            maybeExecutionPayload ->
                handleParentExecutionPayloadResponse(
                    block, result, batchImportFuture, maybeExecutionPayload));
  }

  private SafeFuture<BlockImportResult> handleParentExecutionPayloadResponse(
      final SignedBeaconBlock block,
      final BlockImportResult result,
      final CancellableBatchImportFuture batchImportFuture,
      final Optional<SignedExecutionPayloadEnvelope> maybeExecutionPayload) {
    if (maybeExecutionPayload.isPresent()) {
      return importRecoveredParentExecutionPayload(
          block, maybeExecutionPayload.get(), result, batchImportFuture);
    }
    LOG.debug(
        "Failed to recover parent execution payload by root {} for block at slot {}: no envelope returned",
        block.getParentRoot(),
        block.getSlot());
    // recovery is only attempted for UNKNOWN_PARENT_EXECUTION_PAYLOAD, so that is the only failure
    // reason this can report back to the batch
    return SafeFuture.completedFuture(result);
  }

  private SafeFuture<Optional<SignedExecutionPayloadEnvelope>>
      handleParentExecutionPayloadRequestError(
          final SignedBeaconBlock block, final Throwable error) {
    if (ExceptionUtil.hasCause(error, CancellationException.class)) {
      // the batch import was cancelled, propagate it rather than reporting an import failure
      return SafeFuture.failedFuture(error);
    }
    LOG.debug(
        "Failed to recover parent execution payload by root {} for block at slot {}",
        block.getParentRoot(),
        block.getSlot(),
        error);
    return SafeFuture.completedFuture(Optional.empty());
  }

  private SafeFuture<BlockImportResult> importRecoveredParentExecutionPayload(
      final SignedBeaconBlock block,
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final BlockImportResult originalResult,
      final CancellableBatchImportFuture batchImportFuture) {
    return trackActiveTask(
            executionPayloadManager.importExecutionPayload(signedExecutionPayloadEnvelope, false),
            batchImportFuture)
        .thenCompose(
            executionPayloadImportResult ->
                handleRecoveredParentExecutionPayloadImport(
                    block, originalResult, batchImportFuture, executionPayloadImportResult));
  }

  private SafeFuture<BlockImportResult> handleRecoveredParentExecutionPayloadImport(
      final SignedBeaconBlock block,
      final BlockImportResult originalResult,
      final CancellableBatchImportFuture batchImportFuture,
      final ExecutionPayloadImportResult executionPayloadImportResult) {
    if (executionPayloadImportResult.isSuccessful()) {
      return trackActiveTask(blockImporter.importBlock(block), batchImportFuture);
    }
    if (executionPayloadImportResult.hasFailedExecution()) {
      LOG.debug(
          "Failed to import recovered parent execution payload by root {} for block at slot {}: {}",
          block.getParentRoot(),
          block.getSlot(),
          executionPayloadImportResult.toLogString(),
          executionPayloadImportResult.getFailureCause().orElse(null));
      return SafeFuture.completedFuture(
          BlockImportResult.failedExecutionPayloadExecution(
              executionPayloadImportResult.getFailureCause().orElseThrow()));
    }
    if (executionPayloadImportResult.isDataNotAvailable()) {
      LOG.debug(
          "Recovered parent execution payload by root {} for block at slot {} is data unavailable: {}",
          block.getParentRoot(),
          block.getSlot(),
          executionPayloadImportResult.toLogString(),
          executionPayloadImportResult.getFailureCause().orElse(null));
      return SafeFuture.completedFuture(
          BlockImportResult.failedDataAvailabilityCheckNotAvailable(
              executionPayloadImportResult.getFailureCause()));
    }
    LOG.debug(
        "Failed to import recovered parent execution payload by root {} for block at slot {}: {}",
        block.getParentRoot(),
        block.getSlot(),
        executionPayloadImportResult.toLogString(),
        executionPayloadImportResult.getFailureCause().orElse(null));
    return SafeFuture.completedFuture(originalResult);
  }

  private record BatchImportContext(
      Batch batch,
      List<SignedBeaconBlock> blocks,
      Map<Bytes32, List<BlobSidecar>> blobSidecarsByBlockRoot,
      Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloadsByBlockRoot,
      Optional<SyncSource> source,
      CancellableBatchImportFuture result) {}

  private static class CancellableBatchImportFuture extends SafeFuture<BatchImportResult> {
    private final AtomicReference<SafeFuture<?>> activeTask = new AtomicReference<>();

    private void setActiveTask(final SafeFuture<?> task) {
      activeTask.set(task);
      if (isCancelled()) {
        task.cancel(true);
      }
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      final boolean cancelled = super.cancel(mayInterruptIfRunning);
      if (cancelled) {
        final SafeFuture<?> task = activeTask.get();
        if (task != null) {
          task.cancel(mayInterruptIfRunning);
        }
      }
      return cancelled;
    }
  }

  public record SingleImportResult(
      boolean isSuccessful,
      boolean failedPayloadExecution,
      boolean dataNotAvailable,
      String failureReason,
      Optional<Throwable> failureCause) {}

  public enum BatchImportResult {
    IMPORTED_ALL_BLOCKS,
    IMPORT_FAILED,
    EXECUTION_CLIENT_OFFLINE,
    DATA_NOT_AVAILABLE;

    public boolean isFailure() {
      return this == IMPORT_FAILED;
    }
  }
}

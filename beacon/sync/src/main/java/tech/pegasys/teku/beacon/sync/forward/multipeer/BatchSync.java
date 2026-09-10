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
import static java.util.stream.Collectors.joining;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.batches.SyncSourceBatch.isParentBlockFull;
import static tech.pegasys.teku.infrastructure.async.ExceptionHandlers.exceptionHandlingConsumer;
import static tech.pegasys.teku.infrastructure.async.ExceptionHandlers.exceptionHandlingRunnable;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.events.SyncPreImportBlockChannel;
import tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

/** Manages the sync process to reach a finalized chain. */
public class BatchSync implements Sync {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration PAUSE_ON_SERVICE_OFFLINE_OR_DAS_CHECK = Duration.ofSeconds(5);

  private final EventThread eventThread;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BatchImporter batchImporter;
  private final BatchDataRequester batchDataRequester;
  private final MultipeerCommonAncestorFinder commonAncestorFinder;
  private final TimeProvider timeProvider;
  private final SyncPreImportBlockChannel syncPreImportBlockChannel;

  private final BatchChain activeBatches;

  private Optional<Batch> importingBatch = Optional.empty();
  private Optional<SafeFuture<BatchImportResult>> importingBatchFuture = Optional.empty();
  private boolean switchingBranches = false;

  private SafeFuture<UInt64> commonAncestorSlot;

  private TargetChain targetChain;
  private SafeFuture<SyncResult> syncResult = SafeFuture.completedFuture(SyncResult.COMPLETE);

  /**
   * Records the epoch seconds that the last import started or if no import has started for the
   * current sync, the time the sync started. This is used by {@link SyncStallDetector} to check if
   * the sync is making progress.
   */
  private UInt64 lastImportTimerStartPointSeconds;

  private boolean scheduledProgressSync = false;

  private BatchSync(
      final EventThread eventThread,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BatchChain activeBatches,
      final BatchImporter batchImporter,
      final BatchDataRequester batchDataRequester,
      final MultipeerCommonAncestorFinder commonAncestorFinder,
      final TimeProvider timeProvider,
      final SyncPreImportBlockChannel syncPreImportBlockChannel) {
    this.eventThread = eventThread;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.activeBatches = activeBatches;
    this.batchImporter = batchImporter;
    this.batchDataRequester = batchDataRequester;
    this.commonAncestorFinder = commonAncestorFinder;
    this.timeProvider = timeProvider;
    this.lastImportTimerStartPointSeconds = timeProvider.getTimeInSeconds();
    this.syncPreImportBlockChannel = syncPreImportBlockChannel;
  }

  public static BatchSync create(
      final EventThread eventThread,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BatchImporter batchImporter,
      final BatchFactory batchFactory,
      final int batchSize,
      final int maxPendingBatches,
      final MultipeerCommonAncestorFinder commonAncestorFinder,
      final TimeProvider timeProvider,
      final SyncPreImportBlockChannel syncPreImportBlockChannel) {
    final BatchChain activeBatches = new BatchChain();
    final BatchDataRequester batchDataRequester =
        new BatchDataRequester(
            eventThread, activeBatches, batchFactory, UInt64.valueOf(batchSize), maxPendingBatches);
    return new BatchSync(
        eventThread,
        asyncRunner,
        recentChainData,
        activeBatches,
        batchImporter,
        batchDataRequester,
        commonAncestorFinder,
        timeProvider,
        syncPreImportBlockChannel);
  }

  /**
   * Begin a sync to the specified target chain. If a sync was previously in progress to a different
   * chain, the sync will switch to this new chain.
   *
   * @param targetChain the finalized chain that is the target to sync to
   * @return a future which completes when the sync finishes
   */
  @Override
  public SafeFuture<SyncResult> syncToChain(final TargetChain targetChain) {
    final SafeFuture<SyncResult> result = new SafeFuture<>();
    eventThread.execute(
        exceptionHandlingRunnable(() -> switchSyncTarget(targetChain, result), result));
    return result;
  }

  @Override
  public SafeFuture<Optional<SyncProgress>> getSyncProgress() {
    if (syncResult.isDone()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final SafeFuture<Optional<SyncProgress>> result = new SafeFuture<>();
    eventThread.execute(
        exceptionHandlingRunnable(
            () -> {
              final BatchInfoAccumulator reducedBatchesInfo =
                  activeBatches.stream()
                      .map(BatchInfoAccumulator::fromBatch)
                      .reduce(BatchInfoAccumulator::accumulate)
                      .orElse(BatchInfoAccumulator.EMPTY);

              result.complete(
                  Optional.of(reducedBatchesInfo.toSyncProgress(importingBatch, targetChain)));
            },
            result));
    return result;
  }

  private void switchSyncTarget(
      final TargetChain targetChain, final SafeFuture<SyncResult> syncResult) {
    LOG.debug("Switching to sync target {}", targetChain.getChainHead());
    eventThread.checkOnEventThread();

    final boolean syncRestartRequired = newChainRequiresSyncRestart(targetChain);

    // Cancel the existing sync
    this.syncResult.complete(SyncResult.TARGET_CHANGED);
    this.targetChain = targetChain;
    this.syncResult = syncResult;
    if (syncRestartRequired) {
      // If we already know the new chain doesn't extend the in-progress sync, reset back to the
      // common ancestor.  Otherwise optimistically assume that the new chain will just carry on
      // after the last batch we've already requested from the old chain.
      restartSyncWithNewChain();

      // Reset the import timer as we may need more time to get imports going again.
      this.lastImportTimerStartPointSeconds = timeProvider.getTimeInSeconds();
    }
    progressSync();
  }

  private boolean newChainRequiresSyncRestart(final TargetChain targetChain) {
    if (this.syncResult.isDone()) {
      // Can't extend the current sync because it has already completed or failed.
      return true;
    }
    final UInt64 lastBatchEnd = activeBatches.last().map(Batch::getLastSlot).orElse(UInt64.ZERO);
    // New target might extend the current target chain if the new target is a longer chain
    // otherwise we know immediately we have to start back at the common ancestor.
    return targetChain.getChainHead().getSlot().isLessThanOrEqualTo(lastBatchEnd);
  }

  private void findCommonAncestorSlot() {
    final SafeFuture<UInt64> commonAncestor = commonAncestorFinder.findCommonAncestor(targetChain);
    this.commonAncestorSlot = commonAncestor;
    commonAncestor.thenRunAsync(this::progressSync, eventThread).propagateExceptionTo(syncResult);
  }

  private void onBatchReceivedBlocks(final Batch batch) {
    eventThread.checkOnEventThread();
    if (!isActiveBatch(batch)) {
      LOG.debug("Ignoring update from batch {} as it is no longer useful", batch);
      progressSync();
      return;
    }

    syncPreImportBlockChannel.onNewPreImportBlocks(List.copyOf(batch.getBlocks()));

    activeBatches
        .previousNonEmptyBatch(batch)
        .ifPresentOrElse(
            previousBatch -> checkBatchesFormChain(previousBatch, batch),
            () -> {
              // There are no previous blocks awaiting import, check if the batch builds on our
              // chain
              batch
                  .getFirstBlock()
                  .ifPresent(firstBlock -> checkBatchMatchesStartingPoint(batch, firstBlock));
            });

    activeBatches
        .nextNonEmptyBatch(batch)
        .ifPresentOrElse(
            followingBatch -> checkBatchesFormChain(batch, followingBatch),
            () -> checkAgainstTargetHead(batch));

    progressSync();
  }

  private void checkAgainstTargetHead(final Batch batch) {
    if (batchEndsChain(batch)) {
      batch.markComplete();
      batch.markLastBlockConfirmed();
    } else if (batch.isComplete()
        && batch.getLastSlot().equals(targetChain.getChainHead().getSlot())) {
      // We reached the target slot, but not the root that was claimed
      if (batch.isEmpty()) {
        // We didn't get any blocks - maybe we should have, or maybe a previous batch was wrong
        // Contest all batches back to the last non-empty batch
        final NavigableSet<Batch> batchesFromLastBlock =
            activeBatches
                .previousNonEmptyBatch(batch)
                .map(activeBatches::batchesAfterInclusive)
                .orElseGet(() -> activeBatches.batchesBeforeInclusive(batch));
        // If any are incomplete, we might yet get the blocks we need
        // Otherwise at least one of them is wrong so contest them all
        if (batchesFromLastBlock.stream().allMatch(Batch::isComplete)) {
          LOG.debug(
              "Contesting {} batches because blocks do not lead to target chain head",
              batchesFromLastBlock.size());
          markBatchesAsContested(batchesFromLastBlock);
        }
      } else {
        // We got blocks but they didn't lead to the target so must be invalid
        LOG.debug(
            "Marking batch {} as invalid because returned blocks do not match the sync target",
            batch);
        batch.markAsInvalid();
      }
    }
  }

  private Boolean batchEndsChain(final Batch batch) {
    return batch
        .getLastBlock()
        .map(lastBlock -> lastBlock.getRoot().equals(targetChain.getChainHead().getBlockRoot()))
        .orElse(false);
  }

  private void checkBatchMatchesStartingPoint(
      final Batch batch, final SignedBeaconBlock firstBlock) {
    final NavigableSet<Batch> previousBatches = activeBatches.batchesBeforeExclusive(batch);
    if (isChildOfStartingPoint(firstBlock)) {
      LOG.debug(
          "Marking {} batches complete because block at slot {} is child of starting point",
          previousBatches.size(),
          firstBlock.getSlot());
      // We found where this chain connects to ours so all prior empty batches are
      // complete and our first block is valid.
      batch.markFirstBlockConfirmed();
      previousBatches.forEach(this::confirmEmptyBatch);
    } else if (previousBatches.isEmpty()) {
      // There are no previous batches but this doesn't match our chain so must be invalid
      LOG.debug(
          "Marking batch {} as invalid because there are no previous batches and it does not connect to our chain",
          batch);
      batch.markAsInvalid();
    } else if (previousBatches.stream().allMatch(Batch::isComplete)) {
      // All the previous batches claim to be empty but we don't match up.
      final NavigableSet<Batch> contestedBatches = activeBatches.batchesBeforeInclusive(batch);
      LOG.debug(
          "Contesting {} batches because first block does not match sync start point",
          contestedBatches.size());
      markBatchesAsContested(contestedBatches);
    }
  }

  /**
   * Returns true if firstBlock is the first block in the chain we're trying to download.
   *
   * <p>Specifically, it's parent block must exist in our Store and be at or before the common
   * ancestor slot. If we accept the child of any block we have, we may incorrectly mark batches
   * between the common ancestor and this block as empty, then later contest that when we received
   * blocks from those batches.
   *
   * @param firstBlock the block to check if it's the start of the chain to sync
   * @return true if and only if the block is a suitable start of the chain to sync
   */
  private boolean isChildOfStartingPoint(final SignedBeaconBlock firstBlock) {
    return recentChainData
        .getSlotForBlockRoot(firstBlock.getParentRoot())
        // Parent might be before the common ancestor slot if common ancestor slot is empty.
        // Typically that's when we use the finalized checkpoint as the common ancestor
        .map(parentSlot -> commonAncestorSlot.join().isGreaterThanOrEqualTo(parentSlot))
        .orElse(false);
  }

  private void checkBatchesFormChain(final Batch firstBatch, final Batch secondBatch) {
    if (batchesFormChain(firstBatch, secondBatch)) {
      if (isRequiredBoundaryExecutionPayloadMissing(firstBatch, secondBatch)) {
        LOG.debug(
            "Marking batch {} as invalid because the boundary block requires an execution payload envelope that was missing",
            firstBatch);
        firstBatch.markAsInvalid();
        return;
      }
      markBatchesAsFormingChain(
          firstBatch, secondBatch, activeBatches.batchesBetweenExclusive(firstBatch, secondBatch));
      return;
    }
    if (!firstBatch.isComplete()
        || secondBatch.isEmpty()
        || !activeBatches.batchesBetweenExclusive(firstBatch, secondBatch).stream()
            .allMatch(Batch::isComplete)) {
      // Not a conflict if the first batch or any batch between the two batches may be missing
      // blocks or if the second batch is empty (the chain should be formed by a later batch)
      return;
    }
    if (!firstBatch.getTargetChain().equals(secondBatch.getTargetChain())) {
      // We switched chains but they didn't actually match up. Stop downloading batches.
      // When the current import is complete, restart from the common ancestor with the new chain
      // We don't start downloading while an import is in progress, because it may update our
      // finalized checkpoint which may lead to unknown parent failures because we're importing
      // blocks from the new chain that are now before the finalized checkpoint
      LOG.debug(
          "New chain did not extend previous chain. {} and {} did not form chain",
          firstBatch,
          secondBatch);
      restartSyncWithNewChain();
      return;
    }

    final NavigableSet<Batch> contestedBatches =
        activeBatches.batchesBetweenInclusive(firstBatch, secondBatch);
    LOG.debug(
        "Marking {} batches as contested because {} and {} do not form a chain",
        contestedBatches.size(),
        firstBatch,
        secondBatch);
    markBatchesAsContested(contestedBatches);
    // Otherwise there must be a block in firstBatch we haven't received yet
  }

  private void restartSyncWithNewChain() {
    activeBatches.removeAll();
    if (importingBatch.isPresent()) {
      switchingBranches = true;
    } else {
      // Nothing importing, can start downloading new chain immediately.
      findCommonAncestorSlot();
    }
  }

  private void startNextImport() {
    if (importingBatch.isPresent()) {
      return;
    }
    activeBatches
        .firstImportableBatch()
        .ifPresent(
            batch -> {
              lastImportTimerStartPointSeconds = timeProvider.getTimeInSeconds();
              importingBatch = Optional.of(batch);
              final SafeFuture<BatchImportResult> importFuture = batchImporter.importBatch(batch);
              importingBatchFuture = Optional.of(importFuture);
              importFuture
                  .handleAsync(
                      (result, error) -> {
                        if (error != null) {
                          onImportFailed(batch, error);
                        } else {
                          onImportComplete(result, batch);
                        }
                        return null;
                      },
                      eventThread)
                  .propagateExceptionTo(syncResult);
            });
  }

  private void markBatchesAsFormingChain(
      final Batch firstBatch,
      final Batch secondBatch,
      final Collection<Batch> confirmedEmptyBatches) {
    firstBatch.markComplete();
    firstBatch.markLastBlockConfirmed();
    secondBatch.markFirstBlockConfirmed();
    confirmedEmptyBatches.forEach(this::confirmEmptyBatch);
  }

  private void confirmEmptyBatch(final Batch emptyBatch) {
    emptyBatch.markComplete();
    emptyBatch.markFirstBlockConfirmed();
    emptyBatch.markLastBlockConfirmed();
  }

  private void markBatchesAsContested(final NavigableSet<Batch> contestedBatches) {
    contestedBatches.forEach(Batch::markAsContested);
  }

  /**
   * Handles an import which completed exceptionally, for example because it failed unexpectedly.
   * The batch didn't import, so it is handled as any other failed import - importing state has to
   * be released either way, otherwise the sync would wait forever for an import that already
   * finished.
   */
  private void onImportFailed(final Batch importedBatch, final Throwable error) {
    eventThread.checkOnEventThread();
    if (!isCurrentlyImportingBatch(importedBatch)) {
      // already released, e.g. the sync was aborted which cancelled the import
      return;
    }
    // an import should never fail exceptionally, so make it visible rather than only retrying
    LOG.warn("Import of batch {} failed unexpectedly", importedBatch, error);
    onImportComplete(BatchImportResult.IMPORT_FAILED, importedBatch);
  }

  private void onImportComplete(
      final BatchImporter.BatchImportResult result, final Batch importedBatch) {
    eventThread.checkOnEventThread();
    checkState(
        isCurrentlyImportingBatch(importedBatch),
        "Received import complete for batch that shouldn't have been importing");
    importingBatch = Optional.empty();
    importingBatchFuture = Optional.empty();
    if (switchingBranches) {
      // We switched to a different chain while this was importing. Can't infer anything about other
      // batches from this result but should still penalise the peer that sent it to us.
      switchingBranches = false;
      findCommonAncestorSlot();
      if (result.isFailure()) {
        importedBatch.markAsInvalid();
      }
      progressSync();
      return;
    }

    if (result.isFailure()) {
      // Mark all batches that form a chain with this one as invalid
      for (Batch batch : activeBatches.batchesAfterInclusive(importedBatch)) {
        if (!batch.isFirstBlockConfirmed()) {
          break;
        }
        LOG.debug("Marking batch {} as invalid because it extends from an invalid block", batch);
        batch.markAsInvalid();
      }
    } else if (result == BatchImportResult.EXECUTION_CLIENT_OFFLINE
        || result == BatchImportResult.DATA_NOT_AVAILABLE) {
      if (!scheduledProgressSync) {
        LOG.warn(
            "Unable to import blocks ({} - {}): {}",
            importedBatch.getFirstSlot(),
            importedBatch.getLastSlot(),
            result);
        asyncRunner
            .runAfterDelay(
                () ->
                    eventThread.execute(
                        () -> {
                          scheduledProgressSync = false;
                          progressSync();
                        }),
                PAUSE_ON_SERVICE_OFFLINE_OR_DAS_CHECK)
            .finishStackTrace();
        scheduledProgressSync = true;
      }
      return;
    } else {
      // Everything prior to this batch must already exist on our chain so we can drop them all
      activeBatches.removeUpToIncluding(importedBatch);
      commonAncestorSlot = SafeFuture.completedFuture(importedBatch.getLastSlot());
    }
    progressSync();
    if (activeBatches.isEmpty()) {
      LOG.trace("Marking sync to {} as complete", targetChain);
      syncResult.complete(SyncResult.COMPLETE);
    }
  }

  private Boolean isCurrentlyImportingBatch(final Batch importedBatch) {
    return importingBatch
        .map(currentImportingBatch -> currentImportingBatch.equals(importedBatch))
        .orElse(false);
  }

  private Boolean batchesFormChain(final Batch previousBatch, final Batch secondBatch) {
    if (previousBatch.getLastBlock().isEmpty() || secondBatch.getFirstBlock().isEmpty()) {
      return false;
    }
    final SignedBeaconBlock lastBlock = previousBatch.getLastBlock().get();
    final SignedBeaconBlock firstBlock = secondBatch.getFirstBlock().get();
    return blocksForChain(lastBlock, firstBlock);
  }

  private Boolean blocksForChain(
      final SignedBeaconBlock lastBlock, final SignedBeaconBlock firstBlock) {
    return lastBlock.getRoot().equals(firstBlock.getParentRoot());
  }

  private boolean isRequiredBoundaryExecutionPayloadMissing(
      final Batch firstBatch, final Batch secondBatch) {
    return firstBatch
        .getLastBlock()
        .flatMap(
            lastBlock ->
                secondBatch
                    .getFirstBlock()
                    .filter(
                        firstBlock ->
                            isParentBlockFull(lastBlock, firstBlock)
                                && !firstBatch
                                    .getExecutionPayloadsByBlockRoot()
                                    .containsKey(lastBlock.getRoot())))
        .isPresent();
  }

  private void progressSync() {
    if (syncResult.isDone()) {
      return;
    }
    if (targetChain.getPeers().isEmpty()) {
      activeBatches.removeAll();
      LOG.debug(
          "Unable to sync to target chain {} because it has no remaining peers",
          () -> targetChain.getChainHead().toLogString());
      syncResult.complete(SyncResult.FAILED);
      return;
    }
    if (switchingBranches) {
      // Waiting for last import to complete to switch branches so don't start new tasks
      checkState(
          importingBatch.isPresent(), "Waiting for import to complete but no import in progress");
      LOG.debug("Not adding new batches on new chain while waiting for import to complete");
      return;
    }
    startNextImport();
    fillRetrievingQueue();
  }

  private void fillRetrievingQueue() {
    if (commonAncestorSlot.isCompletedNormally()) {
      batchDataRequester.fillRetrievingQueue(
          targetChain,
          commonAncestorSlot.join(),
          exceptionHandlingConsumer(this::onBatchReceivedBlocks, syncResult));
    }
  }

  @VisibleForTesting
  boolean isActiveBatch(final Batch batch) {
    return activeBatches.contains(batch);
  }

  public UInt64 getLastImportTimerStartPointSeconds() {
    eventThread.checkOnEventThread();
    return lastImportTimerStartPointSeconds;
  }

  Optional<Batch> getImportingBatch() {
    eventThread.checkOnEventThread();
    return importingBatch;
  }

  public void abort() {
    eventThread.checkOnEventThread();
    LOG.warn("Aborting sync {}", this::describeState);
    final Optional<SafeFuture<BatchImportResult>> importToCancel = importingBatchFuture;
    importingBatch = Optional.empty();
    importingBatchFuture = Optional.empty();
    activeBatches.removeAll();
    switchingBranches = false;
    commonAncestorSlot = null;
    targetChain = null;
    syncResult.complete(SyncResult.FAILED);
    importToCancel.ifPresent(importFuture -> importFuture.cancel(true));
  }

  private String describeState() {
    return String.format(
        "Target: %s Switching branches: %s%n"
            + "Importing batch: %s%n"
            + "Common ancestor slot: %s%n"
            + "Active batches: %s%n"
            + "Peers on chain: %s",
        targetChain.getChainHead().toLogString(),
        switchingBranches,
        importingBatch.map(this::formatBatch).orElse("<none>"),
        commonAncestorSlot.isCompletedNormally() ? commonAncestorSlot.join() : commonAncestorSlot,
        activeBatches.stream().map(this::formatBatch).collect(joining("\n  ", "\n  ", "")),
        targetChain.getPeers().stream().map(Object::toString).collect(joining("\n  ", "\n  ", "")));
  }

  private String formatBatch(final Batch batch) {
    return String.format(
        "{Batch from %s to %s, %d blocks from %s (%s) to %s, confirmed %s, complete %s, source %s}",
        batch.getFirstSlot(),
        batch.getLastSlot(),
        batch.getBlocks().size(),
        batch.getFirstBlock().map(MinimalBeaconBlockSummary::toLogString).orElse("<none>"),
        batch.isFirstBlockConfirmed() ? "confirmed" : "unconfirmed",
        batch.getLastBlock().map(MinimalBeaconBlockSummary::toLogString).orElse("<none>"),
        batch.isConfirmed(),
        batch.isComplete(),
        batch.getSource());
  }

  private record BatchInfoAccumulator(
      UInt64 fromSlot,
      UInt64 toSlot,
      int batches,
      int downloadingSlots,
      int downloadingBatches,
      int readySlots,
      int readyBatches) {

    static final BatchInfoAccumulator EMPTY =
        new BatchInfoAccumulator(UInt64.ZERO, UInt64.ZERO, 0, 0, 0, 0, 0);

    static BatchInfoAccumulator fromBatch(final Batch batch) {
      if (batch.isAwaitingBlocks()) {
        return new BatchInfoAccumulator(
            batch.getFirstSlot(), batch.getLastSlot(), 1, batch.getCount().intValue(), 1, 0, 0);
      }
      if (batch.isComplete()) {
        return new BatchInfoAccumulator(
            batch.getFirstSlot(), batch.getLastSlot(), 1, 0, 0, batch.getCount().intValue(), 1);
      }
      return new BatchInfoAccumulator(batch.getFirstSlot(), batch.getLastSlot(), 0, 0, 0, 0, 0);
    }

    static BatchInfoAccumulator accumulate(
        final BatchInfoAccumulator a, final BatchInfoAccumulator b) {
      return new BatchInfoAccumulator(
          a.fromSlot.min(b.fromSlot),
          a.toSlot.max(b.toSlot),
          a.batches + b.batches,
          a.downloadingSlots + b.downloadingSlots,
          a.downloadingBatches + b.downloadingBatches,
          a.readySlots + b.readySlots,
          a.readyBatches + b.readyBatches);
    }

    SyncProgress toSyncProgress(
        final Optional<Batch> importingBatch, final TargetChain targetChain) {
      return new SyncProgress(
          fromSlot,
          toSlot,
          batches,
          downloadingSlots,
          downloadingBatches,
          readySlots,
          readyBatches,
          importingBatch.isPresent(),
          targetChain.getChainHead(),
          targetChain.getPeerCount());
    }
  }
}

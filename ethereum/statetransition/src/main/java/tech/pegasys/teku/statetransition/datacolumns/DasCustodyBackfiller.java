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

package tech.pegasys.teku.statetransition.datacolumns;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DasCustodyBackfiller extends Service
    implements CustodyGroupCountChannel, FinalizedCheckpointChannel {
  public static final int HEADS_BATCH_SIZE_IN_SLOTS = 10;

  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Duration backfillCheckInterval;
  private final DataColumnSidecarCustody dataColumnSidecarCustody;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final DataColumnSidecarRetriever retriever;
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  private final Spec spec;

  private Optional<Cancellable> scheduledBackfiller = Optional.empty();

  private final Supplier<SafeFuture<Optional<UInt64>>> earliestAvailableCustodySlotProvider;
  private final Function<UInt64, SafeFuture<Void>> earliestAvailableCustodySlotWriter;

  private final int batchSizeInSlots;

  private final AtomicBoolean backfilling = new AtomicBoolean(false);
  private final AtomicReference<Checkpoint> lastFinalizedCheckpoint = new AtomicReference<>();

  private volatile int currentSyncCustodyGroupCount;
  private volatile boolean requiresResyncDueToCustodyGroupCountChange;
  private volatile boolean isFirstRoundAfterStartup = true;

  private volatile boolean cancelled = false;
  private volatile boolean inSync = false;

  private final Map<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> pendingRequests =
      new ConcurrentHashMap<>();

  public DasCustodyBackfiller(
      final CombinedChainDataClient combinedChainDataClient,
      final Duration backfillCheckInterval,
      final DataColumnSidecarCustody dataColumnSidecarCustody,
      final CustodyGroupCountManager custodyGroupCountManager,
      final DataColumnSidecarRetriever retriever,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final AsyncRunner asyncRunner,
      final Supplier<SafeFuture<Optional<UInt64>>> earliestAvailableCustodySlotProvider,
      final Function<UInt64, SafeFuture<Void>> earliestAvailableCustodySlotWriter,
      final int batchSizeInSlots,
      final Spec spec) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.asyncRunner = asyncRunner;
    this.backfillCheckInterval = backfillCheckInterval;
    this.dataColumnSidecarCustody = dataColumnSidecarCustody;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.currentSyncCustodyGroupCount = custodyGroupCountManager.getCustodyGroupCount();
    this.batchSizeInSlots = batchSizeInSlots;
    this.retriever = retriever;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.earliestAvailableCustodySlotProvider = earliestAvailableCustodySlotProvider;
    this.earliestAvailableCustodySlotWriter = earliestAvailableCustodySlotWriter;
    this.spec = spec;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    if (scheduledBackfiller.map(Cancellable::isCancelled).orElse(true)) {
      retrieveSyncedCustodyGroupCount()
          .thenAccept(
              syncedCount -> {
                requiresResyncDueToCustodyGroupCountChange =
                    syncedCount < currentSyncCustodyGroupCount;
                if (requiresResyncDueToCustodyGroupCountChange) {
                  LOG.info(
                      "Custody resync required: synced {} groups from DB, but {} required. "
                          + "Backfill will restart from current slot.",
                      syncedCount,
                      currentSyncCustodyGroupCount);
                }
              })
          .always(this::scheduleBackfiller);
    }
    return SafeFuture.COMPLETE;
  }

  private synchronized void scheduleBackfiller() {
    if (cancelled) {
      return;
    }
    scheduledBackfiller =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::runBackfillCycle,
                Duration.ZERO,
                backfillCheckInterval,
                error -> LOG.error("Failed to run data column backfill", error)));
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    cancelled = true;
    scheduledBackfiller.ifPresent(Cancellable::cancel);
    pendingRequests.values().forEach(f -> f.cancel(true));
    pendingRequests.clear();
    return SafeFuture.COMPLETE;
  }

  public synchronized void onNodeSyncStateChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  @VisibleForTesting
  public void setFirstRoundAfterStartup(final boolean firstRoundAfterStartup) {
    this.isFirstRoundAfterStartup = firstRoundAfterStartup;
  }

  @Override
  public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {
    if (custodyGroupCount > currentSyncCustodyGroupCount) {
      LOG.info(
          "Custody group count increased from {} to {}, "
              + "backfill will restart from current slot.",
          currentSyncCustodyGroupCount,
          custodyGroupCount);
      currentSyncCustodyGroupCount = custodyGroupCount;
      requiresResyncDueToCustodyGroupCountChange = true;
    }
  }

  /**
   * We want to cancel pending requests related to blocks that did not become canonical on
   * finalization
   */
  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    if (pendingRequests.isEmpty()) {
      lastFinalizedCheckpoint.set(checkpoint);
      return;
    }
    final Spec spec = combinedChainDataClient.getRecentChainData().getSpec();

    final UInt64 fromSlot =
        Optional.ofNullable(lastFinalizedCheckpoint.getAndSet(checkpoint))
            .map(c -> c.getEpochStartSlot(spec))
            .orElse(UInt64.ZERO);
    final UInt64 toSlot = checkpoint.getEpochStartSlot(spec);

    final Map<Bytes32, List<SafeFuture<DataColumnSidecar>>> requestsByBlockRoot = new HashMap<>();

    pendingRequests.forEach(
        (columnId, rpcRequestFuture) -> {
          final UInt64 slot = columnId.slot();

          if (slot.isGreaterThan(fromSlot) && slot.isLessThanOrEqualTo(toSlot)) {

            // let's check only requests falling into the range

            requestsByBlockRoot
                .computeIfAbsent(columnId.blockRoot(), __ -> new ArrayList<>())
                .add(rpcRequestFuture);
          }
        });

    requestsByBlockRoot.forEach(
        (blockRoot, pendingRequestsForBlock) ->
            combinedChainDataClient
                .getFinalizedSlotByBlockRoot(blockRoot)
                .thenAccept(
                    finalizedSlot -> {
                      // If the slot is empty, this block root is NOT in the finalized chain.
                      if (finalizedSlot.isEmpty()) {
                        LOG.debug(
                            "Cancelling {} pending data column request for finalized non canonical block {}",
                            pendingRequestsForBlock.size(),
                            blockRoot);
                        pendingRequestsForBlock.forEach(f -> f.cancel(true));
                      }
                    })
                .finishStackTrace());
  }

  private void runBackfillCycle() {
    if (!backfilling.compareAndSet(false, true)) {
      LOG.debug(
          "DasCustodyBackfiller: Backfilling is in progress. Pending requests: {}",
          pendingRequests.size());
      return;
    }

    if (!inSync && !isFirstRoundAfterStartup) {
      backfilling.set(false);
      LOG.debug("DasCustodyBackfiller: syncing, skipping backfilling");
      return;
    }

    attemptNextBackfillStep()
        .handleComposed(
            (shouldRerunImmediately, error) -> {
              backfilling.set(false);

              if (error != null) {
                LOG.error("DasCustodyBackfiller: Error during data column backfill", error);
                return SafeFuture.COMPLETE;
              }

              if (shouldRerunImmediately) {
                return asyncRunner.runAsync(this::runBackfillCycle);
              }

              return SafeFuture.COMPLETE;
            })
        .finishStackTrace();
  }

  /**
   * Returns TRUE if the batch was processed successfully and we made progress, indicating we should
   * try the next batch immediately. Returns FALSE if we should wait for the scheduled interval.
   */
  private SafeFuture<Boolean> attemptNextBackfillStep() {
    final Optional<UInt64> minCustodyPeriodSlot =
        minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
            combinedChainDataClient.getCurrentSlot());

    if (minCustodyPeriodSlot.isEmpty()) {
      LOG.debug("DasCustodyBackfiller: Fulu not yet enabled");
      return SafeFuture.completedFuture(false);
    }

    if (requiresResyncDueToCustodyGroupCountChange) {
      requiresResyncDueToCustodyGroupCountChange = false;
      isFirstRoundAfterStartup = false; // we don't need to do the first round
      return earliestAvailableCustodySlotWriter
          .apply(combinedChainDataClient.getCurrentSlot())
          .thenApply(__ -> true);
    }

    if (isFirstRoundAfterStartup) {
      return prepareAndExecuteHeadsBatch(minCustodyPeriodSlot.get());
    }

    return earliestAvailableCustodySlotProvider
        .get()
        .thenCompose(
            earliestAvailableCustodySlot ->
                this.prepareAndExecuteBatch(
                    earliestAvailableCustodySlot, minCustodyPeriodSlot.get()));
  }

  /**
   * The first batch after startup is a special batch will download missing custody for our latest
   * slots (chain heads). This is required because we write blocks and columns not in the same
   * transaction, so it is possible that the node shuts down after a block is written on disk but
   * before all custody columns has been written. The assumption is that this can normally occur
   * only for very few columns on the chain head, so we don't try to recheck the whole period. After
   * this first batch is concluded, we continue backfilling from earliestAvailableCustodySlot db
   * var.
   */
  private SafeFuture<Boolean> prepareAndExecuteHeadsBatch(final UInt64 minCustodyPeriodSlot) {
    return getLatestChainHeadSlot()
        .map(
            latestSlot ->
                prepareBatch(
                        calculateEarliestSlotForBatch(
                            latestSlot, minCustodyPeriodSlot, HEADS_BATCH_SIZE_IN_SLOTS),
                        latestSlot,
                        minCustodyPeriodSlot,
                        true)
                    .thenCompose(this::executeBatch)
                    .thenPeek(__ -> isFirstRoundAfterStartup = false))
        .orElse(SafeFuture.completedFuture(false));
  }

  private Optional<UInt64> getLatestChainHeadSlot() {
    return combinedChainDataClient.getRecentChainData().getChainHeads().stream()
        .map(ProtoNodeData::getSlot)
        .max(UInt64::compareTo);
  }

  /**
   * Returns TRUE if the batch was processed successfully and we made progress, indicating we should
   * try the next batch immediately. Returns FALSE if we should wait for the scheduled interval.
   */
  private SafeFuture<Boolean> prepareAndExecuteBatch(
      final Optional<UInt64> earliestAvailableCustodySlot, final UInt64 minCustodyPeriodSlot) {
    if (earliestAvailableCustodySlot.isEmpty()) {
      // is the db variable is not initialized
      // we are most likely coming from a checkpoint sync with a fresh DB
      // let's choose the most recent slot head +1, so that we make sure we cover the head in the
      // first batch
      final Optional<UInt64> mostRecentHeadSlotPlus1 =
          getLatestChainHeadSlot().map(UInt64::increment);

      if (mostRecentHeadSlotPlus1.isEmpty()) {
        return SafeFuture.completedFuture(false);
      }

      return earliestAvailableCustodySlotWriter
          .apply(mostRecentHeadSlotPlus1.get())
          .thenApply(__ -> false);
    }

    LOG.debug(
        "DasCustodyBackfiller: earliestAvailableCustodySlot {}", earliestAvailableCustodySlot);

    if (earliestAvailableCustodySlot.get().isLessThanOrEqualTo(minCustodyPeriodSlot)) {
      // All required custody is available, backfill is not needed, move
      // earliestAvailableCustodySlot forward and return
      return earliestAvailableCustodySlotWriter.apply(minCustodyPeriodSlot).thenApply(__ -> false);
    }

    var latestSlotInBatch = earliestAvailableCustodySlot.get().minusMinZero(1);
    var earliestSlotInBatch =
        calculateEarliestSlotForBatch(latestSlotInBatch, minCustodyPeriodSlot, batchSizeInSlots);

    return prepareBatch(earliestSlotInBatch, latestSlotInBatch, minCustodyPeriodSlot, false)
        .thenCompose(this::executeBatch);
  }

  private UInt64 calculateEarliestSlotForBatch(
      final UInt64 earliestSlot, final UInt64 minCustodyPeriodSlot, final int batchSizeInSlots) {
    return earliestSlot.minusMinZero(batchSizeInSlots - 1).max(minCustodyPeriodSlot);
  }

  private SafeFuture<BatchData> prepareBatch(
      final UInt64 fromSlot,
      final UInt64 toSlot,
      final UInt64 minCustodyPeriodSlot,
      final boolean isHeadsCustodyCheckBatch) {
    return combinedChainDataClient
        .getCustodyDataColumnIdentifiers(fromSlot, toSlot)
        .thenCombine(
            retrieveBlocksWithBlobsInRange(fromSlot, toSlot),
            (dataColumnSlotAndIdentifiers, slotAndBlockRootWithBlobsPresences) ->
                new BatchData(
                    fromSlot,
                    toSlot,
                    minCustodyPeriodSlot,
                    custodyGroupCountManager.getCustodyColumnIndices(),
                    custodyGroupCountManager.getCustodyGroupCount(),
                    Set.copyOf(dataColumnSlotAndIdentifiers),
                    slotAndBlockRootWithBlobsPresences,
                    isHeadsCustodyCheckBatch));
  }

  private SafeFuture<Boolean> executeBatch(final BatchData batchData) {
    LOG.debug("DasCustodyBackfiller: Executing batch {}", batchData);

    final Set<DataColumnSlotAndIdentifier> missingColumnsToRequest =
        batchData.calculateMissingColumnIndexes();

    final List<SafeFuture<Void>> rpcFutures = new ArrayList<>();
    for (DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
      rpcFutures.add(requestColumnSidecar(missingColumn));
    }

    // We wait for the DB variable update AND all RPCs to finish.
    return SafeFuture.allOf(rpcFutures.toArray(SafeFuture[]::new))
        .thenCompose(__ -> calculateAndUpdateEarliestAvailableCustodySlot(batchData))
        .thenPeek(
            earliestAvailableCustodySlotUpdated -> {
              LOG.debug("DasCustodyBackfiller: Batch completed: {}", batchData);
              if (!earliestAvailableCustodySlotUpdated) {
                LOG.debug(
                    "DasCustodyBackfiller: No progress, waiting for blocks to be backfilled. Waiting next round.");
              }
            });
  }

  /**
   * Moves the cursor backwards based on batch contents. Returns TRUE if cursor was updated, or if
   * it is a isHeadsCustodyCheckBatch batch Returns FALSE if no update (empty slots gap).
   */
  private SafeFuture<Boolean> calculateAndUpdateEarliestAvailableCustodySlot(
      final BatchData batchData) {
    if (batchData.isHeadsCustodyCheckBatch) {
      return SafeFuture.completedFuture(true);
    }

    var oldestExistingBlockInBatch = batchData.oldestExistingBlockInBatch();

    if (oldestExistingBlockInBatch.isPresent()) {
      // Batch has blocks, just move earliestAvailableCustodySlot back to last existing block in the
      // batch.
      // We take the oldest existing block in batch because blocks in batch range can be missing due
      // to block backfilling
      // still working.
      final UInt64 nextSlot = oldestExistingBlockInBatch.get();
      return updateEarliestAvailableCustodySlot(nextSlot, batchData);
    } else {
      // No blocks in batch, lookup most recent block from the oldest slot of the batch
      return combinedChainDataClient
          .getBlockInEffectAtSlot(batchData.fromSlot().minusMinZero(1))
          .thenCompose(
              maybeBlockInEffect ->
                  maybeBlockInEffect
                      .map(
                          block ->
                              updateEarliestAvailableCustodySlot(
                                  block.getSlot().increment().max(batchData.minCustodyPeriodSlot),
                                  batchData))

                      // there is no block in effect prior to our batch, still backfilling blocks
                      // so return false to wait for the next round
                      .orElseGet(() -> SafeFuture.completedFuture(false)));
    }
  }

  private SafeFuture<Boolean> updateEarliestAvailableCustodySlot(
      final UInt64 slot, final BatchData batchData) {
    return earliestAvailableCustodySlotWriter
        .apply(slot)
        .thenApply(
            __ -> {
              if (slot.isLessThanOrEqualTo(batchData.minCustodyPeriodSlot)) {
                LOG.debug("DasCustodyBackfiller: Column custody backfill completed successfully.");
                return false;
              }

              return true;
            });
  }

  private SafeFuture<Void> requestColumnSidecar(final DataColumnSlotAndIdentifier colId) {
    // we can ignore concurrency issues here because we guarantee to have only one batch in flight
    // for each round
    // and we cannot have duplicated columnIds in data structures
    if (pendingRequests.containsKey(colId)) {
      return SafeFuture.COMPLETE;
    }

    LOG.debug("DasCustodyBackfiller: Retrieving missing column {} to custody", colId);

    final SafeFuture<DataColumnSidecar> req = retriever.retrieve(colId);
    pendingRequests.put(colId, req);

    return req.thenPeek(
            __ -> LOG.trace("DasCustodyBackfiller: Data column sidecar {} retrieved.", colId))
        .thenCompose(
            sidecar ->
                dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC))
        .thenPeek(__ -> LOG.trace("DasCustodyBackfiller: Data column sidecar {} stored.", colId))
        .ignoreCancelException()
        .catchAndRethrow(
            err ->
                LOG.debug(
                    "DasCustodyBackfiller: Error retrieving or storing missing column {}",
                    colId,
                    err))
        .alwaysRun(() -> pendingRequests.remove(colId));
  }

  private SafeFuture<Integer> retrieveSyncedCustodyGroupCount() {
    return earliestAvailableCustodySlotProvider
        .get()
        .thenCompose(
            maybeSlot ->
                maybeSlot
                    .map(
                        slot ->
                            // First we do scan query because it's not guaranteed we have sidecars
                            // in earliestAvailableCustodySlot
                            combinedChainDataClient
                                .getDataColumnIdentifiers(slot, UInt64.MAX_VALUE, UInt64.ONE)
                                .thenCompose(
                                    identifiersScan -> {
                                      if (identifiersScan.isEmpty()) {
                                        return SafeFuture.completedFuture(0);
                                      } else {
                                        final UInt64 slotWithSidecars =
                                            identifiersScan.getFirst().slot();

                                        return getSyncedCustodyGroupCountForSlot(slotWithSidecars);
                                      }
                                    }))
                    .orElseGet(() -> SafeFuture.completedFuture(0)));
  }

  private SafeFuture<Integer> getSyncedCustodyGroupCountForSlot(final UInt64 slot) {
    return combinedChainDataClient
        .getCustodyDataColumnIdentifiers(slot)
        .thenApply(
            identifiers -> {
              final Set<UInt64> availableIndices =
                  identifiers.stream()
                      .map(DataColumnSlotAndIdentifier::columnIndex)
                      .collect(Collectors.toSet());
              final Set<UInt64> requiredIndices =
                  custodyGroupCountManager.getCustodyColumnIndices();

              final int syncedColumnCount =
                  (int) requiredIndices.stream().filter(availableIndices::contains).count();
              final MiscHelpersFulu miscHelpersFulu =
                  MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());

              return syncedColumnCount / miscHelpersFulu.getCustodyColumnsPerGroup();
            });
  }

  private SafeFuture<List<SlotAndBlockRootWithBlobsPresence>> retrieveBlocksWithBlobsInRange(
      final UInt64 fromSlot, final UInt64 toSlot) {
    return AsyncStream.createUnsafe(UInt64.rangeClosed(fromSlot, toSlot).iterator())
        .flatMap(
            slot -> {
              if (combinedChainDataClient.isFinalized(slot)) {
                return AsyncStream.create(
                        combinedChainDataClient.getFinalizedBlockAtSlotExact(slot))
                    .map(block -> SlotAndBlockRootWithBlobsPresence.fromBlockAtSlot(block, slot));
              }
              return AsyncStream.createUnsafe(
                      combinedChainDataClient
                          .getRecentChainData()
                          .getAllBlockRootsAtSlot(slot)
                          .iterator())
                  .mapAsync(
                      blockRoot ->
                          combinedChainDataClient
                              .getRecentChainData()
                              .retrieveSignedBlockByRoot(blockRoot))
                  .map(block -> SlotAndBlockRootWithBlobsPresence.fromBlockAtSlot(block, slot));
            })
        .toList();
  }

  private record BatchData(
      UInt64 fromSlot,
      UInt64 toSlot,
      UInt64 minCustodyPeriodSlot,
      Set<UInt64> requiredColumnsInCustody,
      int custodyGroupCount,
      Set<DataColumnSlotAndIdentifier> columnsInCustody,
      List<SlotAndBlockRootWithBlobsPresence> blocksInfo,
      boolean isHeadsCustodyCheckBatch) {

    // suppress needed due to errorprone issue on record custom constructor
    @SuppressWarnings("MethodInputParametersMustBeFinal")
    BatchData {
      checkArgument(
          fromSlot.isGreaterThanOrEqualTo(minCustodyPeriodSlot),
          "fromSlot older than minCustodyPeriodSlot");
    }

    Optional<UInt64> oldestExistingBlockInBatch() {
      return blocksInfo.stream()
          .filter(s -> s.blockRoot.isPresent())
          .map(SlotAndBlockRootWithBlobsPresence::slot)
          .min(UInt64::compareTo);
    }

    Set<DataColumnSlotAndIdentifier> calculateMissingColumnIndexes() {
      return blocksInfo.stream()
          .filter(SlotAndBlockRootWithBlobsPresence::hasBlobs)
          .flatMap(
              blockWithBlobInfo ->
                  requiredColumnsInCustody.stream()
                      .map(
                          index ->
                              new DataColumnSlotAndIdentifier(
                                  blockWithBlobInfo.slot,
                                  blockWithBlobInfo.blockRoot.orElseThrow(),
                                  index)))
          .filter(
              requiredDataColumnSlotAndIdentifier ->
                  !columnsInCustody.contains(requiredDataColumnSlotAndIdentifier))
          .collect(Collectors.toSet());
    }
  }

  private record SlotAndBlockRootWithBlobsPresence(
      UInt64 slot, Optional<Bytes32> blockRoot, boolean hasBlobs) {
    static SlotAndBlockRootWithBlobsPresence fromBlockAtSlot(
        final Optional<SignedBeaconBlock> block, final UInt64 slot) {
      if (block.isEmpty()) {
        return new SlotAndBlockRootWithBlobsPresence(slot, Optional.empty(), false);
      }
      checkArgument(block.get().getSlot().equals(slot), "Inconsistent block slot");
      return new SlotAndBlockRootWithBlobsPresence(
          slot,
          block.map(SignedBeaconBlock::getRoot),
          block
              .get()
              .getMessage()
              .getBody()
              .getOptionalBlobKzgCommitments()
              .map(commitments -> !commitments.isEmpty())
              .orElse(false));
    }
  }
}

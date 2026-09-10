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

package tech.pegasys.teku.statetransition.datacolumns;

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasicImpl implements DasSamplerBasic, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final int maxRecentlySampledBlocks;
  private final Map<Bytes32, DataColumnSamplingTracker> recentlySampledColumnsByRoot;

  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final RPCFetchDelayProvider rpcFetchDelayProvider;
  private final boolean halfColumnsSamplingCompletionEnabled;
  private final BlockImportChannel blockImportChannel;

  public DasSamplerBasicImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final CurrentSlotProvider currentSlotProvider,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final CustodyGroupCountManager custodyGroupCountManager,
      final RecentChainData recentChainData,
      final boolean halfColumnsSamplingCompletionEnabled,
      final MetricsSystem metricsSystem,
      final int maxRecentlySampledBlocks,
      final BlockImportChannel blockImportChannel) {
    this.currentSlotProvider = currentSlotProvider;
    this.rpcFetchDelayProvider = rpcFetchDelayProvider;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.custody = custody;
    this.retriever = retriever;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.recentChainData = recentChainData;
    this.halfColumnsSamplingCompletionEnabled = halfColumnsSamplingCompletionEnabled;
    this.maxRecentlySampledBlocks = maxRecentlySampledBlocks;
    this.recentlySampledColumnsByRoot = new ConcurrentHashMap<>(maxRecentlySampledBlocks);
    this.blockImportChannel = blockImportChannel;
    metricsSystem.createGauge(
        BEACON,
        "das_recently_sampled_blocks_size",
        "DAS recently sampled blocks size",
        recentlySampledColumnsByRoot::size);
  }

  @VisibleForTesting
  Map<Bytes32, DataColumnSamplingTracker> getRecentlySampledColumnsByRoot() {
    return recentlySampledColumnsByRoot;
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  @Override
  public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return Optional.ofNullable(recentlySampledColumnsByRoot.get(blockRoot))
        .flatMap(DataColumnSamplingTracker::getBlock);
  }

  @Override
  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 blockRoot) {
    return Optional.ofNullable(recentlySampledColumnsByRoot.get(blockRoot))
        .map(DataColumnSamplingTracker::slot);
  }

  /**
   * When syncing or backfilling always make sure to call this method with known DataColumn *before*
   * calling {@link DasSamplerBasic#checkDataAvailability(UInt64, Bytes32)} so that RPC fetch won't
   * be executed on those columns.
   */
  @Override
  public void onNewValidatedDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId, final RemoteOrigin remoteOrigin) {
    LOG.debug("Sampler received data column {} - origin: {}", columnId, remoteOrigin);

    if (isDataAvailabilityAlreadySatisfied(columnId.slot(), columnId.blockRoot())) {
      return;
    }

    getOrCreateTracker(columnId.slot(), columnId.blockRoot()).add(columnId, remoteOrigin);
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      final UInt64 slot, final Bytes32 blockRoot) {
    final DataColumnSamplingTracker tracker = getOrCreateTracker(slot, blockRoot);

    if (tracker.completionFuture().isDone()) {
      return tracker.completionFuture();
    }

    if (tracker.rpcFetchInProgress().compareAndSet(false, true)) {
      fetchMissingColumnsViaRPC(slot, blockRoot, tracker);
    }

    return tracker.completionFuture();
  }

  private void onFirstSeen(
      final UInt64 slot, final Bytes32 blockRoot, final DataColumnSamplingTracker tracker) {
    final Duration delay = rpcFetchDelayProvider.calculate(slot);
    if (delay.isZero()) {
      // in case of immediate RPC fetch, let's postpone the actual fetch when checkDataAvailability
      // is called.
      // this is needed because 0 delay means we are syncing\backfilling this slot, so we want to
      // wait eventual known columns to be added via onAlreadyKnownDataColumn before fetching.
      return;
    }
    tracker.rpcFetchInProgress().set(true);
    asyncRunner
        .getDelayedFuture(delay)
        .always(() -> fetchMissingColumnsViaRPC(slot, blockRoot, tracker));
  }

  private void fetchMissingColumnsViaRPC(
      final UInt64 slot, final Bytes32 blockRoot, final DataColumnSamplingTracker tracker) {
    // Delayed fetch callbacks can outlive the tracker they were scheduled for.
    // Only the currently installed tracker may issue RPC requests.
    if (isStaledTracker(blockRoot, tracker)) {
      tracker.rpcFetchInProgress().set(false);
      LOG.debug(
          "checkDataAvailability(): skipping stale RPC fetch for slot {} root {}", slot, blockRoot);
      return;
    }

    final List<DataColumnSlotAndIdentifier> missingColumns = tracker.getMissingColumnIdentifiers();
    LOG.debug(
        "checkDataAvailability(): missing columns for slot {} root {}: {}",
        slot,
        blockRoot,
        missingColumns.size());

    SafeFuture.collectAll(
            missingColumns.stream().map(id -> retrieveColumnWithSamplingAndCustody(id, tracker)))
        .thenAccept(
            retrievedColumns -> {
              if (retrievedColumns.size() == missingColumns.size()) {
                LOG.debug(
                    "checkDataAvailability(): retrieved remaining {} (of {}) columns via Req/Resp for block {} ({})",
                    retrievedColumns.size(),
                    tracker.samplingRequirement().size(),
                    slot,
                    blockRoot);
              } else {
                throw new IllegalStateException(
                    String.format(
                        "Retrieved only(%d) out of %d missing columns for slot %s (%s) with %d required columns",
                        retrievedColumns.size(),
                        missingColumns.size(),
                        slot,
                        blockRoot,
                        tracker.samplingRequirement().size()));
              }
            })
        // let's reset the fetched flag so that this tracker can reissue RPC requests on DA check
        // retry
        .alwaysRun(() -> tracker.rpcFetchInProgress().set(false))
        .finish(
            throwable -> {
              if (ExceptionUtil.hasCause(throwable, CancellationException.class)) {
                final String error = throwable.getMessage();
                LOG.debug(
                    "CancellationException in checkDataAvailability: {}",
                    () -> error == null ? "<no message>" : error);

              } else {
                LOG.error("data availability check failed", throwable);
              }
            });
  }

  @SuppressWarnings({"ReferenceEquality", "ReferenceComparison"})
  private boolean isStaledTracker(
      final Bytes32 blockRoot, final DataColumnSamplingTracker tracker) {
    return recentlySampledColumnsByRoot.get(blockRoot) != tracker;
  }

  private DataColumnSamplingTracker getOrCreateTracker(final UInt64 slot, final Bytes32 blockRoot) {
    final DataColumnSamplingTracker existing = recentlySampledColumnsByRoot.get(blockRoot);
    if (existing != null) {
      return existing;
    }
    final DataColumnSamplingTracker dataColumnSamplingTracker =
        DataColumnSamplingTracker.create(
            slot,
            blockRoot,
            custodyGroupCountManager,
            halfColumnsSamplingCompletionEnabled
                ? Optional.of(
                    SpecConfigFulu.required(spec.atSlot(slot).getConfig()).getNumberOfColumns() / 2)
                : Optional.empty());
    final DataColumnSamplingTracker trackerInserted =
        recentlySampledColumnsByRoot.computeIfAbsent(blockRoot, __ -> dataColumnSamplingTracker);
    if (isStaledTracker(blockRoot, trackerInserted)) {
      return recentlySampledColumnsByRoot.get(blockRoot);
    }
    makeRoomForNewTracker();
    onFirstSeen(slot, blockRoot, dataColumnSamplingTracker);

    return trackerInserted;
  }

  private void makeRoomForNewTracker() {
    final int currentSize = recentlySampledColumnsByRoot.size();
    if (currentSize < maxRecentlySampledBlocks) {
      return;
    }
    // First pass: evict trackers no longer needed for import, oldest-created first. Best-effort:
    // CHM size/iteration are weakly consistent and concurrent writers may shift counts while we
    // work.
    final int softExcess = currentSize - maxRecentlySampledBlocks + 1;
    recentlySampledColumnsByRoot.entrySet().stream()
        .filter(e -> isSafeToEvict(e.getValue()))
        .sorted(Comparator.comparingLong(e -> e.getValue().createdAtNanos()))
        .limit(softExcess)
        .forEach(e -> recentlySampledColumnsByRoot.remove(e.getKey(), e.getValue()));
    // Hard cap: if we're still at 4x the limit after safe eviction, force-evict the oldest
    // remaining trackers to prevent unbounded growth.
    final int hardLimit = maxRecentlySampledBlocks * 4;
    final int afterSoft = recentlySampledColumnsByRoot.size();
    if (afterSoft < hardLimit) {
      return;
    }
    final int hardExcess = afterSoft - hardLimit;
    recentlySampledColumnsByRoot.entrySet().stream()
        .sorted(Comparator.comparingLong(e -> e.getValue().createdAtNanos()))
        .limit(hardExcess)
        .forEach(
            e -> {
              if (!recentlySampledColumnsByRoot.remove(e.getKey(), e.getValue())) {
                return;
              }
              final DataColumnSamplingTracker tracker = e.getValue();
              if (!tracker.completionFuture().isDone()) {
                LOG.warn(
                    "Force-evicting incomplete DAS tracker for slot {} root {} (hard cap reached)",
                    tracker.slot(),
                    tracker.blockRoot());
                tracker
                    .completionFuture()
                    .completeExceptionally(new RuntimeException("DAS sampling expired (hard cap)"));
              }
            });
  }

  private boolean isSafeToEvict(final DataColumnSamplingTracker tracker) {
    return tracker.completionFuture().isCompletedExceptionally()
        || (tracker.completionFuture().isDone()
            && isDataAvailabilityAlreadySatisfied(tracker.slot(), tracker.blockRoot()));
  }

  private SafeFuture<DataColumnSidecar> retrieveColumnWithSamplingAndCustody(
      final DataColumnSlotAndIdentifier id, final DataColumnSamplingTracker tracker) {
    return retriever
        .retrieve(id)
        .thenPeek(
            sidecar -> {
              if (tracker.add(id, RemoteOrigin.RPC)) {
                // send to custody only if it was added to the tracker
                // (i.e. not received from other sources in the meantime)
                custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC).finishError(LOG);
              }
            });
  }

  @Override
  public void flush() {
    retriever.flush();
  }

  private boolean hasBlobs(final BeaconBlock block) {
    return !block.getBody().getOptionalBlobKzgCommitments().map(SszList::isEmpty).orElse(true);
  }

  private boolean isDataAvailabilityAlreadySatisfied(final UInt64 slot, final Bytes32 blockRoot) {
    if (!recentChainData.containsBlock(blockRoot)) {
      return false;
    }

    if (spec.atSlot(slot)
        .getForkChoiceUtil()
        .isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope()) {
      return recentChainData.getStore().getExecutionPayloadIfAvailable(blockRoot).isPresent();
    }

    // For non-deferred forks, block import already required data availability to be satisfied.
    return true;
  }

  @Override
  public boolean isDataAvailable(final SignedBeaconBlock block) {
    // if there are no blobs for this block, consider the data as available
    if (!hasBlobs(block.getMessage())) {
      return true;
    }
    if (isDataAvailabilityAlreadySatisfied(block.getSlot(), block.getRoot())) {
      return true;
    }
    final DataColumnSamplingTracker tracker = recentlySampledColumnsByRoot.get(block.getRoot());
    return tracker != null && tracker.completionFuture().isCompletedNormally();
  }

  private boolean isInCustodyPeriod(final BeaconBlock block) {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(block.getSlot()).miscHelpers());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlotProvider.getCurrentSlot());
    return miscHelpersFulu.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        currentEpoch, spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU;
    } else if (!isInCustodyPeriod(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
    } else if (!hasBlobs(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS;
    } else {
      return SamplingEligibilityStatus.REQUIRED;
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 firstNonFinalizedSlot =
        spec.computeStartSlotAtEpoch(recentChainData.getFinalizedEpoch()).increment();
    recentlySampledColumnsByRoot
        .values()
        .removeIf(
            tracker -> {
              final boolean isCleanupDue;
              if (tracker.slot().isLessThan(firstNonFinalizedSlot)) {
                isCleanupDue = true;
              } else {
                final boolean blockImported = recentChainData.containsBlock(tracker.blockRoot());
                final boolean isDataAvailabilityDeferred =
                    spec.atSlot(tracker.slot())
                        .getForkChoiceUtil()
                        .isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope();
                isCleanupDue = blockImported && !isDataAvailabilityDeferred;
              }

              if (isCleanupDue) {
                // Outdated
                if (!tracker.completionFuture().isDone()) {
                  // make sure the future releases any pending waiters
                  tracker
                      .completionFuture()
                      .completeExceptionally(
                          new RuntimeException("DAS sampling expired while slot finalized"));
                  // Slot less than finalized slot, but we didn't complete DA check, means it's
                  // probably orphaned block with data never available - we must prune this
                  // RecentChainData contains block, but we are here - shouldn't happen
                  return true;
                }
                // cleanup only if fully sampled
                return tracker.fullySampled().get();
              }
              return false;
            });
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    LOG.debug("Sampler received block {} - origin: {}", block.getSlotAndBlockRoot(), remoteOrigin);
    if (hasBlobs(block.getMessage())) {
      getOrCreateTracker(block.getSlot(), block.getRoot()).setBlock(block);
    }
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    if (spec.atSlot(block.getSlot())
        .getForkChoiceUtil()
        .isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope()) {
      return;
    }
    removeAllForBlock(block.getSlotAndBlockRoot());
  }

  @Override
  public void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    final DataColumnSamplingTracker removed =
        recentlySampledColumnsByRoot.remove(slotAndBlockRoot.getBlockRoot());
    if (removed != null) {
      removed.completionFuture().cancel(true);
      LOG.debug("Removed data column sampling tracker {}", removed);
    }
  }

  @Override
  public void enableBlockImportOnCompletion(final SignedBeaconBlock block) {
    final DataColumnSamplingTracker tracker = recentlySampledColumnsByRoot.get(block.getRoot());
    if (tracker == null) {
      return;
    }
    if (!tracker.enableBlockImportOnCompletion()) {
      return;
    }
    tracker
        .completionFuture()
        .thenCompose(__ -> blockImportChannel.importBlock(block))
        .finish(
            () ->
                LOG.debug("Block {} re-imported after DAS sampling completion", block::toLogString),
            error ->
                LOG.error(
                    "Failed to re-import block {} after DAS sampling completion",
                    block.toLogString(),
                    error));
  }
}

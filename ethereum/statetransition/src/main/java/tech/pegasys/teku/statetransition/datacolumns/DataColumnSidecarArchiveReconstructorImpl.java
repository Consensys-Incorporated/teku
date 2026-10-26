/*
 * Copyright (c) 2025 Consensys Incorporated. All rights reserved.
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.QueueIsFullException;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class DataColumnSidecarArchiveReconstructorImpl
    implements DataColumnSidecarArchiveReconstructor, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  // At most this many reconstructions run concurrently; the rest are queued (see
  // reconstructionQueue) and callers stop waiting after RECONSTRUCTION_TIMEOUT.
  private static final int MAX_CONCURRENT_RECONSTRUCTIONS = 32;
  // Hard bound on the reconstruction backlog: once this many are already queued, further requests
  // are rejected immediately (served as empty) rather than growing the queue unboundedly.
  private static final int MAX_QUEUED_RECONSTRUCTIONS = 1024;
  // Per-request deadline: a request that has waited this long for a reconstruction gets empty and,
  // for the rest of the request, is served only reconstructions that have already completed (see
  // requestsPastDeadline). Kept well below the 10s requesters allow between response chunks
  // (Eth2OutgoingRequestHandler.RESPONSE_CHUNK_ARRIVAL_TIMEOUT), so the request moves on while the
  // peer is still listening. The shared reconstruction keeps running, so later requests join it
  // instead of starting another.
  private static final Duration RECONSTRUCTION_TIMEOUT = Duration.ofSeconds(5);
  // Backstop for a reconstruction that never completes, counted from when it starts running: it is
  // failed, freeing its throttling slot so the next request retries. Far above normal
  // reconstruction times, so a merely slow reconstruction is not duplicated.
  private static final Duration RECONSTRUCTION_HUNG_TIMEOUT = Duration.ofSeconds(60);
  // How long a successful result is kept after its last request before the per-slot prune drops
  // it. A flat wall-clock window, intentionally not derived from network/slot timing since a
  // peer's by-root re-request pattern varies by client; kept generous so successive request waves
  // reuse the result rather than re-reconstructing. A queued reconstruction left unrequested for
  // this long is skipped instead of run, as its result would be pruned straight away.
  private static final Duration RESULT_RETENTION = Duration.ofSeconds(30);
  // Bound on the successful results kept for reuse, each holding all extension sidecars of a block
  // (about 1-2 MB on mainnet). Past it the least recently requested results are evicted as new ones
  // complete, so memory stays bounded however fast reconstructions complete.
  private static final int MAX_RETAINED_RESULTS = 64;
  // Unexpected reconstruction failures recur on every request for the affected block, so at most
  // one per interval is logged at warn and the rest at debug.
  private static final Duration FAILURE_WARN_INTERVAL = Duration.ofMinutes(1);

  private final CombinedChainDataClient chainDataClient;
  private final AsyncRunner reconstructionAsyncRunner;
  // Fires the per-request and hung-reconstruction deadlines. Kept apart from
  // reconstructionAsyncRunner, whose workers are busy reconstructing exactly when deadlines matter.
  private final AsyncRunner timeoutAsyncRunner;
  private final TimeProvider timeProvider;
  private final AtomicInteger nextTaskId = new AtomicInteger(0);
  private final AtomicLong nextFailureWarnAtMillis = new AtomicLong(0);
  // Caps concurrent reconstructions at MAX_CONCURRENT_RECONSTRUCTIONS and queues the overflow, so a
  // burst of requests can't launch an unbounded number of parallel KZG reconstructions.
  private final ThrottlingTaskQueue reconstructionQueue;
  // Reconstruction is deterministic for archived (finalized) blocks, so in-flight and recently
  // completed reconstructions are shared across all ReqResp requests keyed by block. In-flight
  // entries deduplicate any number of concurrent/overlapping requests onto a single reconstruction
  // (not capacity-bounded); successful entries are reused regardless of age and pruned on each slot
  // once unrequested for RESULT_RETENTION, or evicted least recently requested first to keep at
  // most maxRetainedResults of them. Failed entries are replaced by the next request, so it
  // retries.
  private final Map<SlotAndBlockRoot, ReconstructionCacheEntry> reconstructions =
      new ConcurrentHashMap<>();
  private final int maxRetainedResults;
  // Requests that have already waited out RECONSTRUCTION_TIMEOUT once. An empty result writes
  // nothing to the stream, so further waits within the same request would add up against the
  // peer's per-chunk timeout; such a request is served only completed reconstructions and starts no
  // new ones. Cleared by onRequestCompleted.
  private final Set<Integer> requestsPastDeadline = ConcurrentHashMap.newKeySet();
  private final int halfColumns;

  private final Supplier<Boolean> isSuperNodeSupplier;
  private final Spec spec;
  private final UInt64 dataColumnSidecarExtensionRetentionEpochs;
  private final SidecarArchivePrunableChannel sidecarArchivePrunableChannel;

  private final MetricsHistogram reconstructionTimeSeconds;
  private final Counter reconstructionSuccessCounter;
  private final Counter reconstructionFailureCounter;

  public DataColumnSidecarArchiveReconstructorImpl(
      final CombinedChainDataClient chainDataClient,
      final AsyncRunner reconstructionAsyncRunner,
      final AsyncRunner timeoutAsyncRunner,
      final Supplier<Boolean> isSuperNodeSupplier,
      final Spec spec,
      final int dataColumnSidecarExtensionRetentionEpochs,
      final SidecarArchivePrunableChannel sidecarArchivePrunableChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this(
        chainDataClient,
        reconstructionAsyncRunner,
        timeoutAsyncRunner,
        isSuperNodeSupplier,
        spec,
        dataColumnSidecarExtensionRetentionEpochs,
        sidecarArchivePrunableChannel,
        metricsSystem,
        timeProvider,
        MAX_CONCURRENT_RECONSTRUCTIONS,
        MAX_QUEUED_RECONSTRUCTIONS,
        MAX_RETAINED_RESULTS);
  }

  @VisibleForTesting
  DataColumnSidecarArchiveReconstructorImpl(
      final CombinedChainDataClient chainDataClient,
      final AsyncRunner reconstructionAsyncRunner,
      final AsyncRunner timeoutAsyncRunner,
      final Supplier<Boolean> isSuperNodeSupplier,
      final Spec spec,
      final int dataColumnSidecarExtensionRetentionEpochs,
      final SidecarArchivePrunableChannel sidecarArchivePrunableChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final int maxConcurrentReconstructions,
      final int maxQueuedReconstructions,
      final int maxRetainedResults) {
    this.chainDataClient = chainDataClient;
    this.reconstructionAsyncRunner = reconstructionAsyncRunner;
    this.timeoutAsyncRunner = timeoutAsyncRunner;
    this.timeProvider = timeProvider;
    this.maxRetainedResults = maxRetainedResults;
    this.isSuperNodeSupplier = isSuperNodeSupplier;
    this.spec = spec;
    this.dataColumnSidecarExtensionRetentionEpochs =
        UInt64.valueOf(dataColumnSidecarExtensionRetentionEpochs);
    this.halfColumns =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                .getNumberOfColumns()
            / 2;
    this.sidecarArchivePrunableChannel = sidecarArchivePrunableChannel;
    // Queue rejections (full queue) are surfaced via reconstructionFailureCounter, so the
    // queue's own metrics are omitted here to keep a single reconstructor per node metric-free of
    // duplicates.
    this.reconstructionQueue =
        ThrottlingTaskQueue.create(maxConcurrentReconstructions, maxQueuedReconstructions);
    this.reconstructionTimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_seconds",
            "Time taken to reconstruct archived data column sidecars",
            new double[] {0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 5.0, 10.0});
    this.reconstructionSuccessCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_successes_total",
            "Total number of successful data column sidecar archive reconstructions");
    this.reconstructionFailureCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_failures_total",
            "Total number of failed data column sidecar archive reconstructions");
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> reconstructDataColumnSidecar(
      final SignedBeaconBlock block, final UInt64 index, final int requestId) {
    checkArgument(
        index.isGreaterThanOrEqualTo(halfColumns),
        "Only second-half column indices (>= %s) can be reconstructed, got %s",
        halfColumns,
        index);
    LOG.trace(
        "Reconstruction requested for slot {} index {} blockRoot {} (requestId {})",
        block.getSlot(),
        index,
        block.getRoot(),
        requestId);
    final long now = timeProvider.getTimeInMillis().longValue();
    if (requestsPastDeadline.contains(requestId)) {
      return getCompletedReconstruction(block.getSlotAndBlockRoot(), index, now);
    }
    final ReconstructionCacheEntry entry =
        reconstructions.compute(
            block.getSlotAndBlockRoot(),
            (__, existing) -> {
              final ReconstructionCacheEntry maybeReused =
                  existing != null && !existing.hasFailed()
                      ? existing
                      : new ReconstructionCacheEntry(RESULT_RETENTION, now);
              maybeReused.lastRequestedAtMillis = now;
              return maybeReused;
            });
    // started only once compute has returned: completing a reconstruction can evict other entries,
    // which must not happen inside compute
    if (entry.claimStart()) {
      startReconstruction(block, entry);
    }
    // the deadline applies to this caller's own future only, never to the shared reconstruction
    return entry
        .future
        .thenApply(result -> result.get(index))
        .orTimeout(timeoutAsyncRunner, RECONSTRUCTION_TIMEOUT)
        .exceptionally(
            error -> {
              requestsPastDeadline.add(requestId);
              LOG.debug(
                  "Reconstruction request for {} index {} timed out (requestId {}): {}",
                  block.getSlotAndBlockRoot(),
                  index,
                  requestId,
                  error.toString());
              return Optional.empty();
            });
  }

  @Override
  public int onRequest() {
    return getNextTaskId();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final long now = timeProvider.getTimeInMillis().longValue();
    reconstructions.values().removeIf(entry -> entry.isEvictable(now));
  }

  private SafeFuture<Optional<DataColumnSidecar>> getCompletedReconstruction(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 index, final long now) {
    final ReconstructionCacheEntry entry = reconstructions.get(slotAndBlockRoot);
    if (entry == null || !entry.future.isDone()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    entry.lastRequestedAtMillis = now;
    return entry.future.thenApply(result -> result.get(index));
  }

  private void startReconstruction(
      final SignedBeaconBlock block, final ReconstructionCacheEntry entry) {
    reconstructionQueue
        .queueTask(() -> runReconstruction(block, entry))
        .whenComplete(
            (result, error) -> {
              final boolean reconstructed =
                  error == null
                      && result.sidecars().values().stream().anyMatch(Optional::isPresent);
              // completion counts as a request, as the waiting callers read the result now: a
              // result
              // that waited in the queue must not look least recently requested once it is ready
              entry.lastRequestedAtMillis = timeProvider.getTimeInMillis().longValue();
              // a single write publishes the outcome, so a successful entry is never observed
              // as completed-but-failed
              entry.state = reconstructed ? State.SUCCEEDED : State.FAILED;
              if (reconstructed) {
                reconstructionSuccessCounter.inc();
                evictExcessResults();
              } else {
                reconstructionFailureCounter.inc();
                if (error != null) {
                  logReconstructionFailure(block, error);
                }
              }
            })
        // never propagate the error to callers - an unavailable column is Optional.empty()
        .exceptionally(__ -> emptyResult())
        .propagateTo(entry.future);
  }

  private void evictExcessResults() {
    // request times keep changing, so they are snapshotted for a consistent sort
    final List<RetainedResult> retainedResults =
        reconstructions.entrySet().stream()
            .filter(mapping -> mapping.getValue().state == State.SUCCEEDED)
            .map(
                mapping ->
                    new RetainedResult(
                        mapping.getKey(),
                        mapping.getValue(),
                        mapping.getValue().lastRequestedAtMillis))
            .toList();
    final int excess = retainedResults.size() - maxRetainedResults;
    if (excess <= 0) {
      return;
    }
    retainedResults.stream()
        .sorted(Comparator.comparingLong(RetainedResult::lastRequestedAtMillis))
        .limit(excess)
        .forEach(result -> reconstructions.remove(result.slotAndBlockRoot(), result.entry()));
  }

  private SafeFuture<ReconstructionResult> runReconstruction(
      final SignedBeaconBlock block, final ReconstructionCacheEntry entry) {
    if (entry.isUnrequestedForRetention(timeProvider.getTimeInMillis().longValue())) {
      // nobody asked for it while it was queued, so its result would be pruned on the next slot:
      // skip the work (counted as a failure, like a full-queue rejection) and let the next request
      // retry
      LOG.debug(
          "Skipping reconstruction for {}: unrequested for over {} while queued",
          block.getSlotAndBlockRoot(),
          RESULT_RETENTION);
      return SafeFuture.completedFuture(emptyResult());
    }
    // the backstop fails a copy of the task rather than the task itself, so the task still
    // completes, and records its duration, once the work eventually finishes
    final SafeFuture<ReconstructionResult> taskCopy = new SafeFuture<>();
    createTask(block).propagateTo(taskCopy);
    return taskCopy.orTimeout(timeoutAsyncRunner, RECONSTRUCTION_HUNG_TIMEOUT);
  }

  private void logReconstructionFailure(final SignedBeaconBlock block, final Throwable error) {
    // timeouts and full-queue rejections are expected under load
    if (ExceptionUtil.hasCause(error, TimeoutException.class, QueueIsFullException.class)) {
      LOG.debug("Reconstruction failed for {}: {}", block.getSlotAndBlockRoot(), error.toString());
      return;
    }
    final long now = timeProvider.getTimeInMillis().longValue();
    final long nextWarnAtMillis = nextFailureWarnAtMillis.get();
    if (now >= nextWarnAtMillis
        && nextFailureWarnAtMillis.compareAndSet(
            nextWarnAtMillis, now + FAILURE_WARN_INTERVAL.toMillis())) {
      LOG.warn("Reconstruction failed for {}", block.getSlotAndBlockRoot(), error);
    } else {
      LOG.debug("Reconstruction failed for {}", block.getSlotAndBlockRoot(), error);
    }
  }

  private SafeFuture<ReconstructionResult> createTask(final SignedBeaconBlock block) {
    final List<UInt64> firstHalfOfIndices =
        Stream.iterate(UInt64.ZERO, UInt64::increment).limit(halfColumns).toList();

    final MetricsHistogram.Timer timer = reconstructionTimeSeconds.startTimer();

    return reconstructionAsyncRunner
        .runAsync(
            () ->
                chainDataClient
                    .getDataColumnSidecars(block.getSlot(), firstHalfOfIndices)
                    .thenComposeCombined(
                        chainDataClient.getDataColumnSidecarProofs(block.getSlot()),
                        (sidecars, proofs) ->
                            reconstructionAsyncRunner.runAsync(
                                () -> reconstructDataColumnSidecar(block, sidecars, proofs))))
        .whenComplete((result, error) -> timer.closeUnchecked().run());
  }

  private ReconstructionResult reconstructDataColumnSidecar(
      final SignedBeaconBlock block,
      final List<DataColumnSidecar> sidecars,
      final List<List<KZGProof>> proofs) {
    if (sidecars.size() != halfColumns || proofs.size() != halfColumns) {
      return emptyResult();
    }
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
    for (int i = 0; i < halfColumns; i++) {
      final DataColumnSidecar sidecar = sidecars.get(i);
      if (!sidecar.getSlotAndBlockRoot().equals(slotAndBlockRoot)
          || !sidecar.getIndex().equals(UInt64.valueOf(i))) {
        return emptyResult();
      }
    }

    final List<BlobAndCellProofs> blobAndCellProofsList =
        constructBlobAndCellProofsList(sidecars, proofs);

    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(block.getSlot());

    final List<DataColumnSidecar> allDataColumnSidecars =
        dataColumnSidecarUtil.constructDataColumnSidecars(
            Optional.of(block.asHeader()),
            slotAndBlockRoot,
            Optional.of(dataColumnSidecarUtil.getKzgCommitments(block.getMessage())),
            dataColumnSidecarUtil.computeDataColumnKzgCommitmentsInclusionProof(
                block.getMessage().getBody()),
            blobAndCellProofsList);

    LOG.debug(
        "Reconstructed extension data column sidecars for slot {} blockRoot {}",
        block.getSlot(),
        slotAndBlockRoot.getBlockRoot());

    return new ReconstructionResult(
        Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
            .limit(halfColumns)
            .collect(
                Collectors.toMap(
                    index -> index,
                    index -> Optional.of(allDataColumnSidecars.get(index.intValue())))));
  }

  private List<BlobAndCellProofs> constructBlobAndCellProofsList(
      final List<DataColumnSidecar> sidecars, final List<List<KZGProof>> proofs) {
    final DataColumnSidecar firstSidecar = sidecars.getFirst();
    final int blobCount = firstSidecar.getColumn().size();
    final BlobSchema blobSchema =
        SchemaDefinitionsFulu.required(spec.atSlot(firstSidecar.getSlot()).getSchemaDefinitions())
            .getBlobSchema();
    final List<BlobAndCellProofs> blobAndCellProofsList = new ArrayList<>();
    for (int i = 0; i < blobCount; i++) {
      final int blobIndex = i;
      final Bytes blob =
          sidecars.stream()
              .map(sidecar -> sidecar.getColumn().get(blobIndex).getBytes())
              .reduce(Bytes.EMPTY, Bytes::concatenate);
      final List<KZGProof> blobProofs = new ArrayList<>();
      for (int j = 0; j < halfColumns; j++) {
        blobProofs.add(sidecars.get(j).getKzgProofs().get(blobIndex).getKZGProof());
      }
      for (int j = 0; j < halfColumns; j++) {
        blobProofs.add(proofs.get(j).get(blobIndex));
      }

      final BlobAndCellProofs blobAndCellProofs =
          new BlobAndCellProofs(blobSchema.create(blob), blobProofs);
      blobAndCellProofsList.add(blobAndCellProofs);
    }

    return blobAndCellProofsList;
  }

  private ReconstructionResult emptyResult() {
    return new ReconstructionResult(
        Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
            .limit(halfColumns)
            .collect(Collectors.toMap(index -> index, __ -> Optional.empty())));
  }

  @Override
  public boolean isSidecarPruned(final UInt64 slot, final UInt64 index) {
    if (!isSuperNodeSupplier.get()) {
      return false;
    }

    if (index.isLessThan(halfColumns)) {
      return false;
    }

    final UInt64 slotEpoch = spec.computeEpochAtSlot(slot);
    final UpdatableStore store = chainDataClient.getStore();
    if (!spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(store, slotEpoch)) {
      return false;
    }

    final Optional<UInt64> maybeFinalizedSlot = chainDataClient.getFinalizedBlockSlot();
    if (maybeFinalizedSlot.isEmpty()) {
      return false;
    }

    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(maybeFinalizedSlot.get());
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    return slotEpoch.isLessThan(
        finalizedEpoch.min(currentEpoch.minusMinZero(dataColumnSidecarExtensionRetentionEpochs)));
  }

  @Override
  public void onRequestCompleted(final int requestId) {
    requestsPastDeadline.remove(requestId);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    if (!isSuperNodeSupplier.get()) {
      return;
    }
    final UInt64 finalizedEpoch = checkpoint.getEpoch();
    final UInt64 currentEpoch = spec.getCurrentEpoch(chainDataClient.getStore());

    final UInt64 pruningBoundaryEpoch =
        finalizedEpoch.min(currentEpoch.minusMinZero(dataColumnSidecarExtensionRetentionEpochs));
    if (pruningBoundaryEpoch.isZero()) {
      return;
    }
    final UInt64 lastPrunableSlot =
        spec.computeStartSlotAtEpoch(pruningBoundaryEpoch).minusMinZero(1);
    LOG.debug(
        "Signalling data column sidecar archive-prunable slot {} "
            + "(finalizedEpoch {}, currentEpoch {}, retentionEpochs {})",
        lastPrunableSlot,
        finalizedEpoch,
        currentEpoch,
        dataColumnSidecarExtensionRetentionEpochs);
    sidecarArchivePrunableChannel.onSidecarArchivePrunableSlot(lastPrunableSlot);
  }

  private int getNextTaskId() {
    return this.nextTaskId.getAndUpdate(
        current -> current >= (Integer.MAX_VALUE - 1) ? 0 : current + 1);
  }

  record ReconstructionResult(Map<UInt64, Optional<DataColumnSidecar>> sidecars) {
    Optional<DataColumnSidecar> get(final UInt64 index) {
      return Optional.ofNullable(sidecars.get(index)).flatMap(v -> v);
    }
  }

  private enum State {
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  private record RetainedResult(
      SlotAndBlockRoot slotAndBlockRoot,
      ReconstructionCacheEntry entry,
      long lastRequestedAtMillis) {}

  /**
   * A shared reconstruction for a single block. While running it deduplicates concurrent and
   * overlapping requests; once successful it is reused by every later request until the per-slot
   * prune finds it unrequested for {@code retentionMillis}, or it is evicted to keep within the
   * retained results limit. A reconstruction that completed without producing sidecars (failed,
   * hung past RECONSTRUCTION_HUNG_TIMEOUT, rejected by a full queue, or skipped after waiting in
   * the queue unrequested for {@code retentionMillis}) is replaced by the next request, so it
   * retries.
   */
  private static final class ReconstructionCacheEntry {
    private final long retentionMillis;
    private final SafeFuture<ReconstructionResult> future = new SafeFuture<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile State state = State.RUNNING;
    private volatile long lastRequestedAtMillis;

    private ReconstructionCacheEntry(final Duration retention, final long requestedAtMillis) {
      this.retentionMillis = retention.toMillis();
      // set before the entry is queued, as a free slot runs it straight away
      this.lastRequestedAtMillis = requestedAtMillis;
    }

    /** Returns true for exactly one caller, which then starts the reconstruction. */
    private boolean claimStart() {
      return started.compareAndSet(false, true);
    }

    private boolean hasFailed() {
      return state == State.FAILED;
    }

    private boolean isUnrequestedForRetention(final long nowMillis) {
      return nowMillis - lastRequestedAtMillis > retentionMillis;
    }

    private boolean isEvictable(final long nowMillis) {
      return switch (state) {
        // still running - keep so concurrent requests share it
        case RUNNING -> false;
        case FAILED -> true;
        case SUCCEEDED -> isUnrequestedForRetention(nowMillis);
      };
    }
  }
}

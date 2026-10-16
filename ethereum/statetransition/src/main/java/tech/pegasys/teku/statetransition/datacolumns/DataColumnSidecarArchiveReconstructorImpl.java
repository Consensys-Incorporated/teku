/*
 * Copyright Consensys Software Inc., 2025
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
  // reconstructionQueue)
  // and dropped by RECONSTRUCTION_TIMEOUT if they can't be served in time.
  private static final int MAX_CONCURRENT_RECONSTRUCTIONS = 32;
  // Hard bound on the reconstruction backlog: once this many are already queued, further requests
  // are rejected immediately (served as empty) rather than growing the queue unboundedly.
  private static final int MAX_QUEUED_RECONSTRUCTIONS = 1024;
  // Per-task deadline: if a queued/running reconstruction isn't done within this, callers get
  // empty.
  private static final Duration RECONSTRUCTION_TIMEOUT = Duration.ofSeconds(10);
  // How long a completed result is kept for reuse by later, non-overlapping requests before
  // pruning. A flat wall-clock window, intentionally not derived from network/slot timing since a
  // peer's by-root re-request pattern varies by client; kept generous so successive request waves
  // reuse the result rather than re-reconstructing.
  private static final Duration RESULT_RETENTION = Duration.ofSeconds(30);

  private final CombinedChainDataClient chainDataClient;
  private final AsyncRunner reconstructionAsyncRunner;
  private final TimeProvider timeProvider;
  private final AtomicInteger nextTaskId = new AtomicInteger(0);
  // Caps concurrent reconstructions at MAX_CONCURRENT_RECONSTRUCTIONS and queues the overflow, so a
  // burst of requests can't launch an unbounded number of parallel KZG reconstructions.
  private final ThrottlingTaskQueue reconstructionQueue;
  // Reconstruction is deterministic for archived (finalized) blocks, so in-flight and recently
  // completed reconstructions are shared across all ReqResp requests keyed by block. In-flight
  // entries deduplicate any number of concurrent/overlapping requests onto a single reconstruction
  // (not capacity-bounded); completed entries are retained for RESULT_RETENTION so later,
  // non-overlapping requests reuse them, then pruned (lazily and on each slot). Failed/timed-out
  // results are not retained, so a later request retries.
  private final Map<SlotAndBlockRoot, ReconstructionCacheEntry> reconstructions =
      new ConcurrentHashMap<>();
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
      final Supplier<Boolean> isSuperNodeSupplier,
      final Spec spec,
      final int dataColumnSidecarExtensionRetentionEpochs,
      final SidecarArchivePrunableChannel sidecarArchivePrunableChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.chainDataClient = chainDataClient;
    this.reconstructionAsyncRunner = reconstructionAsyncRunner;
    this.timeProvider = timeProvider;
    this.isSuperNodeSupplier = isSuperNodeSupplier;
    this.spec = spec;
    this.dataColumnSidecarExtensionRetentionEpochs =
        UInt64.valueOf(dataColumnSidecarExtensionRetentionEpochs);
    this.halfColumns =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                .getNumberOfColumns()
            / 2;
    this.sidecarArchivePrunableChannel = sidecarArchivePrunableChannel;
    // Queue drops (full queue or timeout) are surfaced via reconstructionFailureCounter, so the
    // queue's own metrics are omitted here to keep a single reconstructor per node metric-free of
    // duplicates.
    this.reconstructionQueue =
        ThrottlingTaskQueue.create(MAX_CONCURRENT_RECONSTRUCTIONS, MAX_QUEUED_RECONSTRUCTIONS);
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
    pruneExpiredReconstructions();
    final long now = timeProvider.getTimeInMillis().longValue();
    final ReconstructionCacheEntry entry =
        reconstructions.compute(
            block.getSlotAndBlockRoot(),
            (__, existing) ->
                existing != null && !existing.isEvictable(now)
                    ? existing
                    : startReconstruction(block));
    return entry.future.thenApply(result -> result.get(index));
  }

  @Override
  public int onRequest() {
    return getNextTaskId();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    pruneExpiredReconstructions();
  }

  private void pruneExpiredReconstructions() {
    final long now = timeProvider.getTimeInMillis().longValue();
    reconstructions.values().removeIf(entry -> entry.isEvictable(now));
  }

  private ReconstructionCacheEntry startReconstruction(final SignedBeaconBlock block) {
    final ReconstructionCacheEntry entry = new ReconstructionCacheEntry(RESULT_RETENTION);
    entry.future =
        reconstructionQueue
            .queueTask(() -> createTask(block))
            // per-task deadline: a queued/running reconstruction that overruns is dropped
            .orTimeout(reconstructionAsyncRunner, RECONSTRUCTION_TIMEOUT)
            .whenComplete(
                (result, error) -> {
                  entry.completedAtMillis = timeProvider.getTimeInMillis().longValue();
                  final boolean reconstructed =
                      error == null
                          && result.sidecars().values().stream().anyMatch(Optional::isPresent);
                  if (reconstructed) {
                    entry.completedSuccessfully = true;
                    reconstructionSuccessCounter.inc();
                  } else {
                    reconstructionFailureCounter.inc();
                    if (error != null) {
                      LOG.debug(
                          "Reconstruction failed or timed out for {}: {}",
                          block.getSlotAndBlockRoot(),
                          error.toString());
                    }
                  }
                })
            // never propagate the error to callers - an unavailable column is Optional.empty()
            .exceptionally(__ -> emptyResult());
    return entry;
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
        "Reconstructed {} extension data column sidecars (indices {}..{}) for slot {} blockRoot {}",
        halfColumns,
        halfColumns,
        (halfColumns * 2) - 1,
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
    // No per-request cleanup needed: reconstructions are shared across requests and pruned by
    // RESULT_RETENTION (lazily and on each slot) or dropped on failure/timeout. Kept for the
    // DataColumnSidecarArchiveReconstructor contract.
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

  /**
   * A shared reconstruction for a single block. While running ({@code completedAtMillis < 0}) it
   * deduplicates concurrent/overlapping requests; once it has completed successfully it is retained
   * for {@code retentionMillis} so later requests reuse it. A reconstruction that completed without
   * producing sidecars (failed or timed out) is evicted on the next prune so a later request
   * retries.
   */
  private static final class ReconstructionCacheEntry {
    private final long retentionMillis;
    private SafeFuture<ReconstructionResult> future;
    private volatile long completedAtMillis = -1;
    private volatile boolean completedSuccessfully = false;

    private ReconstructionCacheEntry(final Duration retention) {
      this.retentionMillis = retention.toMillis();
    }

    private boolean isEvictable(final long nowMillis) {
      if (completedAtMillis < 0) {
        // still running - keep so concurrent requests share it
        return false;
      }
      return !completedSuccessfully || nowMillis - completedAtMillis > retentionMillis;
    }
  }
}

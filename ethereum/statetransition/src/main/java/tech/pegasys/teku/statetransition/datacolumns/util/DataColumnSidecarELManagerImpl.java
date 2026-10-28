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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getRootCauseMessage;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_EL;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_PROPOSAL;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.RECOVERED;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveCollection;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELManager;
import tech.pegasys.teku.statetransition.datacolumns.ValidDataColumnSidecarsListener;
import tech.pegasys.teku.statetransition.util.AbstractIgnoringFutureHistoricalSlot;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarELManagerImpl extends AbstractIgnoringFutureHistoricalSlot
    implements DataColumnSidecarELManager {
  private static final Logger LOG = LogManager.getLogger();
  public static final BiFunction<MetricsSystem, TimeProvider, MetricsHistogram>
      DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM =
          (metricsSystem, timeProvider) ->
              new MetricsHistogram(
                  metricsSystem,
                  timeProvider,
                  TekuMetricCategory.BEACON,
                  "data_column_sidecar_computation_seconds",
                  "Time taken to compute data column sidecar, including cells and inclusion proof (histogram)",
                  new double[] {
                    0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5,
                    5.0, 7.5, 10.0
                  });

  private final ConcurrentSkipListMap<SlotAndBlockRoot, RecoveryTask> recoveryTasks =
      new ConcurrentSkipListMap<>();
  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayer;
  private final int maxTrackers;
  private final BiConsumer<List<DataColumnSidecar>, RemoteOrigin> dataColumnSidecarPublisher;
  private final CustodyGroupCountManager custodyGroupCountManager;

  private final MetricsHistogram dataColumnSidecarComputationTimeSeconds;
  private final Counter getBlobsV2RequestsCounter;
  private final Counter getBlobsV2ResponsesCounter;
  private final MetricsHistogram getBlobsV2RuntimeSeconds;

  private final Duration localElBlobsFetchingRetryDelay;
  private final int localElBlobsFetchingMaxRetries;
  private final DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator;

  static final Set<RemoteOrigin> LOCAL_OR_RECOVERED_ORIGINS =
      Set.of(LOCAL_PROPOSAL, LOCAL_EL, RECOVERED);

  private final Subscribers<ValidDataColumnSidecarsListener> recoveredColumnSidecarSubscribers =
      Subscribers.create(true);

  private volatile boolean inSync;

  private static boolean isLocalBlockProductionOrRecovered(
      final Optional<RemoteOrigin> remoteOrigin) {
    return remoteOrigin.map(LOCAL_OR_RECOVERED_ORIGINS::contains).orElse(false);
  }

  @VisibleForTesting
  public DataColumnSidecarELManagerImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers,
      final BiConsumer<List<DataColumnSidecar>, RemoteOrigin> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final Duration localElBlobsFetchingRetryDelay,
      final int localElBlobsFetchingMaxRetries,
      final DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.maxTrackers = maxTrackers;
    this.dataColumnSidecarPublisher = dataColumnSidecarPublisher;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.localElBlobsFetchingRetryDelay = localElBlobsFetchingRetryDelay;
    this.localElBlobsFetchingMaxRetries = localElBlobsFetchingMaxRetries;
    this.dataColumnSidecarGossipValidator = dataColumnSidecarGossipValidator;
    this.dataColumnSidecarComputationTimeSeconds =
        DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM.apply(metricsSystem, timeProvider);
    this.getBlobsV2RequestsCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_requests_total",
            "Total number of engine_getBlobsV2 requests sent");
    this.getBlobsV2ResponsesCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_responses_total",
            "Total number of engine_getBlobsV2 successful responses received");
    this.getBlobsV2RuntimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_request_duration_seconds",
            "Duration of engine_getBlobsV2 requests",
            new double[] {0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 2.0, 5.0, 10.0});
  }

  @Override
  public void onNewDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    if (isLocalBlockProductionOrRecovered(Optional.of(remoteOrigin))) {
      LOG.debug(
          "Data from {} with origin {} is not subject to recovery",
          dataColumnSidecar::toLogString,
          () -> remoteOrigin);
      return;
    }

    LOG.debug(
        "Received data column sidecar {} from {}",
        dataColumnSidecar::toLogString,
        () -> remoteOrigin);

    if (spec.atSlot(dataColumnSidecar.getSlot()).getMilestone().isLessThan(SpecMilestone.FULU)) {
      LOG.debug(
          "Received data column sidecar {} before FULU. Ignoring.", dataColumnSidecar::toLogString);
      return;
    }
    if (recentChainData.containsBlock(dataColumnSidecar.getBeaconBlockRoot())) {
      LOG.debug(
          "Data column sidecar {} is from already imported block. Ignoring.",
          dataColumnSidecar::toLogString);
      return;
    }
    if (shouldIgnoreItemAtSlot(dataColumnSidecar.getSlot())) {
      LOG.debug(
          "Data column sidecar {} is too old or from the future. Ignoring.",
          dataColumnSidecar::toLogString);
      return;
    }

    final SlotAndBlockRoot slotAndBlockRoot = dataColumnSidecar.getSlotAndBlockRoot();

    final Optional<RecoveryTask> recoveryTaskFromDataColumnSidecar =
        createRecoveryTaskFromDataColumnSidecar(dataColumnSidecar);
    if (shouldStartRecovery(recoveryTaskFromDataColumnSidecar)) {
      startRecovery(slotAndBlockRoot);
    }
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    final SpecMilestone milestone = spec.atSlot(block.getSlot()).getMilestone();
    if (milestone.isLessThan(SpecMilestone.FULU)) {
      LOG.debug("Received block {} before Fulu. Ignoring.", block.getSlotAndBlockRoot());
      return;
    }
    if (isLocalBlockProductionOrRecovered(remoteOrigin)) {
      LOG.debug(
          "Block {} from {} is not subject to recovery",
          block::getSlotAndBlockRoot,
          () -> remoteOrigin);
      return;
    }

    LOG.debug("Received block {} from {}", block::getSlotAndBlockRoot, () -> remoteOrigin);

    if (recentChainData.containsBlock(block.getRoot())) {
      LOG.debug("Block {} is already imported. Ignoring.", block::getSlotAndBlockRoot);
      return;
    }
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      LOG.debug("Block {} is too old or from the future. Ignoring.", block::getSlotAndBlockRoot);
      return;
    }
    final Optional<RecoveryTask> recoveryTaskFromBlock = createRecoveryTaskFromBlock(block);
    if (shouldStartRecovery(recoveryTaskFromBlock)) {
      startRecovery(block.getSlotAndBlockRoot());
    }
  }

  private boolean shouldStartRecovery(final Optional<RecoveryTask> recoveryTask) {
    return recoveryTask.isPresent()
        && recoveryTask.get().canStart()
        && recoveryTask.get().recoveryStarted().compareAndSet(false, true);
  }

  private Optional<RecoveryTask> createRecoveryTaskFromDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    final SlotAndBlockRoot slotAndBlockRoot = dataColumnSidecar.getSlotAndBlockRoot();
    final RecoveryTask existingTask = recoveryTasks.get(slotAndBlockRoot);
    if (existingTask != null) {
      existingTask.recoveredColumnIndices().add(dataColumnSidecar.getIndex());
      return Optional.of(existingTask);
    }
    if (!dataColumnSidecar.getSlot().equals(getCurrentSlot())) {
      return Optional.empty();
    }
    makeRoomForNewTracker();
    final RecoveryTask task =
        recoveryTasks.computeIfAbsent(
            dataColumnSidecar.getSlotAndBlockRoot(), __ -> createRecoveryTask(dataColumnSidecar));
    task.recoveredColumnIndices().add(dataColumnSidecar.getIndex());
    return Optional.of(task);
  }

  private RecoveryTask createRecoveryTask(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());
    final Optional<SszList<SszKZGCommitment>> maybeKzgCommitments =
        dataColumnSidecarUtil.getMaybeKzgCommitments(dataColumnSidecar);
    if (maybeKzgCommitments.isPresent()) {
      final RecoveryTask recoveryTask =
          new RecoveryTask(
              dataColumnSidecar.getMaybeSignedBlockHeader(),
              SafeFuture.completedFuture(maybeKzgCommitments.get()),
              dataColumnSidecarUtil
                  .getMaybeKzgCommitmentsInclusionProof(dataColumnSidecar)
                  .map(SszPrimitiveCollection::asListUnboxed),
              Collections.newSetFromMap(new ConcurrentHashMap<>()),
              new AtomicBoolean(false));
      recoveryTask.recoveredColumnIndices.add(dataColumnSidecar.getIndex());
      return recoveryTask;
    }
    final RecoveryTask recoveryTask =
        new RecoveryTask(
            Optional.empty(),
            new SafeFuture<>(),
            Optional.empty(),
            Collections.newSetFromMap(new ConcurrentHashMap<>()),
            new AtomicBoolean(false));
    recoveryTask.recoveredColumnIndices.add(dataColumnSidecar.getIndex());
    return recoveryTask;
  }

  private Optional<RecoveryTask> createRecoveryTaskFromBlock(final SignedBeaconBlock block) {
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(block.getSlot());
    final RecoveryTask existingTask = recoveryTasks.get(slotAndBlockRoot);
    if (existingTask != null) {
      existingTask
          .sszKzgCommitmentsFuture()
          .complete(dataColumnSidecarUtil.getKzgCommitments(block.getMessage()));
      return Optional.of(existingTask);
    }
    if (!block.getSlot().equals(getCurrentSlot())) {
      return Optional.empty();
    }
    makeRoomForNewTracker();
    final RecoveryTask task =
        recoveryTasks.computeIfAbsent(
            slotAndBlockRoot,
            __ ->
                new RecoveryTask(
                    Optional.of(block.asHeader()),
                    SafeFuture.completedFuture(
                        dataColumnSidecarUtil.getKzgCommitments(block.getMessage())),
                    dataColumnSidecarUtil.computeDataColumnKzgCommitmentsInclusionProof(
                        block.getMessage().getBody()),
                    Collections.newSetFromMap(new ConcurrentHashMap<>()),
                    new AtomicBoolean(false)));
    task.sszKzgCommitmentsFuture()
        .complete(dataColumnSidecarUtil.getKzgCommitments(block.getMessage()));
    return Optional.of(task);
  }

  private void publishRecoveredDataColumnSidecars(
      final RecoveryTask recoveryTask,
      final Optional<SszList<SszKZGCommitment>> maybeSszKzgCommitments,
      final List<BlobAndCellProofs> blobAndCellProofs,
      final SlotAndBlockRoot slotAndBlockRoot,
      final DataColumnSidecarUtil dataColumnSidecarUtil) {
    final List<DataColumnSidecar> dataColumnSidecars;
    try (MetricsHistogram.Timer ignored = dataColumnSidecarComputationTimeSeconds.startTimer()) {
      dataColumnSidecars =
          dataColumnSidecarUtil.constructDataColumnSidecars(
              recoveryTask.maybeSignedBeaconBlockHeader(),
              slotAndBlockRoot,
              maybeSszKzgCommitments,
              recoveryTask.maybeKzgCommitmentsInclusionProof(),
              blobAndCellProofs);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
    final int samplingGroupCount = custodyGroupCountManager.getSamplingGroupCount();
    final int maxCustodyGroups = spec.getNumberOfCustodyGroups(slotAndBlockRoot.getSlot());
    final List<DataColumnSidecar> localCustodySidecars;
    if (samplingGroupCount == maxCustodyGroups) {
      localCustodySidecars = dataColumnSidecars;
    } else {
      final Set<UInt64> localCustodyIndices = custodyGroupCountManager.getSamplingColumnIndices();
      localCustodySidecars =
          dataColumnSidecars.stream()
              .filter(sidecar -> localCustodyIndices.contains(sidecar.getIndex()))
              .toList();
    }
    recoveryTask
        .recoveredColumnIndices()
        .addAll(localCustodySidecars.stream().map(DataColumnSidecar::getIndex).toList());

    LOG.debug(
        "Publishing {} data column sidecars for {}", localCustodySidecars.size(), slotAndBlockRoot);

    dataColumnSidecarGossipValidator.markForEquivocation(
        recoveryTask.maybeSignedBeaconBlockHeader().map(SignedBeaconBlockHeader::getMessage),
        localCustodySidecars,
        slotAndBlockRoot);
    if (inSync) {
      dataColumnSidecarPublisher.accept(localCustodySidecars, LOCAL_EL);
    }
    localCustodySidecars.forEach(
        sidecar -> {
          recoveredColumnSidecarSubscribers.forEach(
              subscriber -> subscriber.onNewValidSidecar(sidecar, LOCAL_EL));
        });
  }

  private void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    recoveryTasks.remove(slotAndBlockRoot);
  }

  @VisibleForTesting
  public RecoveryTask getRecoveryTask(final SlotAndBlockRoot slotAndBlockRoot) {
    return recoveryTasks.get(slotAndBlockRoot);
  }

  @Override
  public void onSyncingStatusChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);
    LOG.trace("Recovery tasks: {}", () -> new HashMap<>(recoveryTasks));
  }

  @Override
  public void subscribeToRecoveredColumnSidecar(
      final ValidDataColumnSidecarsListener sidecarListener) {
    recoveredColumnSidecarSubscribers.subscribe(sidecarListener);
  }

  @VisibleForTesting
  @Override
  protected void prune(final UInt64 slotLimit) {
    SlotAndBlockRoot toRemove = recoveryTasks.keySet().pollFirst();
    while (toRemove != null && toRemove.getSlot().isLessThanOrEqualTo(slotLimit)) {
      removeAllForBlock(toRemove);
      toRemove = recoveryTasks.keySet().pollFirst();
    }
  }

  private void makeRoomForNewTracker() {
    while (recoveryTasks.size() > maxTrackers - 1) {
      final SlotAndBlockRoot toRemove = recoveryTasks.keySet().pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove);
    }
  }

  private void startRecovery(final SlotAndBlockRoot slotAndBlockRoot) {
    LOG.debug("Data from {} is first seen, checking if recovery is needed", slotAndBlockRoot);
    asyncRunner
        .runWithRetry(
            () -> fetchMissingBlobsFromLocalEL(slotAndBlockRoot),
            localElBlobsFetchingRetryDelay,
            localElBlobsFetchingMaxRetries)
        .handleException(this::logLocalElBlobsLookupFailure)
        .finishStackTrace();
  }

  private SafeFuture<Void> fetchMissingBlobsFromLocalEL(final SlotAndBlockRoot slotAndBlockRoot) {
    final RecoveryTask recoveryTask = recoveryTasks.get(slotAndBlockRoot);

    if (recoveryTask == null) {
      return SafeFuture.COMPLETE;
    }

    if (recoveryTask
        .recoveredColumnIndices()
        .containsAll(custodyGroupCountManager.getSamplingColumnIndices())) {
      return SafeFuture.COMPLETE;
    }
    final SszList<SszKZGCommitment> sszKzgCommitments =
        recoveryTask.sszKzgCommitmentsFuture().getImmediately();

    if (sszKzgCommitments.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    final List<BlobIdentifier> missingBlobsIdentifiers =
        IntStream.range(0, sszKzgCommitments.size())
            .mapToObj(
                index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(index)))
            .toList();

    final MiscHelpers miscHelpers = spec.atSlot(slotAndBlockRoot.getSlot()).miscHelpers();
    final List<VersionedHash> versionedHashes =
        missingBlobsIdentifiers.stream()
            .map(
                blobIdentifier ->
                    miscHelpers.kzgCommitmentToVersionedHash(
                        sszKzgCommitments
                            .get(blobIdentifier.getIndex().intValue())
                            .getKZGCommitment()))
            .toList();
    getBlobsV2RequestsCounter.inc();
    final MetricsHistogram.Timer timer = getBlobsV2RuntimeSeconds.startTimer();
    return executionLayer
        .engineGetBlobAndCellProofsList(versionedHashes, slotAndBlockRoot.getSlot())
        .whenComplete((result, error) -> timer.closeUnchecked().run())
        .thenAccept(
            blobAndCellProofsList -> {
              LOG.debug("Found {} blobs", blobAndCellProofsList.size());
              if (blobAndCellProofsList.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Blobs for %s are not found on local EL, reconstruction is not possible",
                        slotAndBlockRoot));
              } else if (blobAndCellProofsList.size() != versionedHashes.size()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Queried %s versionedHashes but got %s blobAndProofs",
                        versionedHashes.size(), blobAndCellProofsList.size()));
              }

              getBlobsV2ResponsesCounter.inc();
              LOG.debug(
                  "Collected all blobSidecars from EL for slot {}, recovering data column sidecars",
                  slotAndBlockRoot::getSlot);
              publishRecoveredDataColumnSidecars(
                  recoveryTask,
                  Optional.of(sszKzgCommitments),
                  blobAndCellProofsList,
                  slotAndBlockRoot,
                  spec.getDataColumnSidecarUtil(slotAndBlockRoot.getSlot()));
            });
  }

  private void logLocalElBlobsLookupFailure(final Throwable error) {
    LOG.debug("Local EL blobs lookup failed: {}", getRootCauseMessage(error));
  }

  public record RecoveryTask(
      Optional<SignedBeaconBlockHeader> maybeSignedBeaconBlockHeader,
      SafeFuture<SszList<SszKZGCommitment>> sszKzgCommitmentsFuture,
      Optional<List<Bytes32>> maybeKzgCommitmentsInclusionProof,
      Set<UInt64> recoveredColumnIndices,
      AtomicBoolean recoveryStarted) {
    public boolean canStart() {
      return sszKzgCommitmentsFuture.isDone();
    }
  }
}

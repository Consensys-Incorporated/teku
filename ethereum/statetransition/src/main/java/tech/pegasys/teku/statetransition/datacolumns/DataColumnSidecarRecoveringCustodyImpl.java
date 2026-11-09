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

import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.RECOVERED;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DataColumnSidecarRecoveringCustodyImpl implements DataColumnSidecarRecoveringCustody {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody delegate;
  private final AsyncRunner asyncRunner;
  private final Spec spec;
  private final BiConsumer<DataColumnSidecar, RemoteOrigin> dataColumnSidecarPublisher;
  private final CustodyGroupCountManager custodyGroupCountManager;

  private final long columnCount;
  private final int recoverColumnCount;
  private final int groupCount;
  private final AtomicBoolean isSuperNode = new AtomicBoolean();

  final Function<UInt64, Duration> slotToRecoveryDelay;
  private final ConcurrentHashMap<SlotAndBlockRoot, RecoveryTask> recoveryTasks =
      new ConcurrentHashMap<>();
  private final NavigableSet<SlotAndBlockRoot> completedSlots = new ConcurrentSkipListSet<>();
  private final int recoveryTasksSizeTarget;
  private final int completedSlotsSizeTarget;

  private final Counter totalDataAvailabilityReconstructedColumns;
  private final MetricsHistogram dataAvailabilityReconstructionTimeSeconds;

  private final Subscribers<ValidDataColumnSidecarsListener> recoveredColumnSidecarSubscribers =
      Subscribers.create(true);

  private volatile boolean inSync;

  @VisibleForTesting
  protected DataColumnSidecarRecoveringCustodyImpl(
      final DataColumnSidecarCustody delegate,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final BiConsumer<DataColumnSidecar, RemoteOrigin> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager,
      final int columnCount,
      final int groupCount,
      final Function<UInt64, Duration> slotToRecoveryDelay,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final int recoveryTasksSizeTarget,
      final int completedSlotsSizeTarget) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
    this.dataColumnSidecarPublisher = dataColumnSidecarPublisher;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.recoveryTasksSizeTarget = recoveryTasksSizeTarget;
    this.completedSlotsSizeTarget = completedSlotsSizeTarget;
    this.slotToRecoveryDelay = slotToRecoveryDelay;
    this.columnCount = columnCount;
    this.groupCount = groupCount;
    this.recoverColumnCount = columnCount / 2;
    this.totalDataAvailabilityReconstructedColumns =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_availability_reconstructed_columns_total",
            "Total count of reconstructed columns");
    this.dataAvailabilityReconstructionTimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_availability_reconstruction_time_seconds",
            "Time taken to reconstruct columns",
            new double[] {
              0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 5.0,
              7.5, 10.0
            });
  }

  public DataColumnSidecarRecoveringCustodyImpl(
      final DataColumnSidecarCustody delegate,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final BiConsumer<DataColumnSidecar, RemoteOrigin> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager,
      final int columnCount,
      final int groupCount,
      final Function<UInt64, Duration> slotToRecoveryDelay,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this(
        delegate,
        asyncRunner,
        spec,
        dataColumnSidecarPublisher,
        custodyGroupCountManager,
        columnCount,
        groupCount,
        slotToRecoveryDelay,
        metricsSystem,
        timeProvider,
        // 1 epoch, having sidecars in memory, it will occupy several Mb for each slot
        spec.getGenesisSpec().getSlotsPerEpoch(),
        // 64 epochs, 2048 slots, pretty big number when you have healthy network
        // and it's just 64 byte (SlotAndBlockRoot) * 2048 = 128kb of cache
        spec.getGenesisSpec().getSlotsPerEpoch() * 64);
  }

  @Override
  public void onSyncingStatusChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    pruneRecoveryTasks();
    pruneCompletedSlots();
    if (shouldSkipProcessing(slot)) {
      return;
    }
    asyncRunner
        .runAfterDelay(
            () -> {
              LOG.debug("Check if recovery needed for slot: {}", slot);

              recoveryTasks.keySet().stream()
                  .filter(key -> key.getSlot().isLessThanOrEqualTo(slot))
                  .map(recoveryTasks::get)
                  .filter(Objects::nonNull)
                  .forEach(
                      recoveryTask -> {
                        if (recoveryTask.timedOut().compareAndSet(false, true)) {
                          maybeStartRecovery(recoveryTask);
                        }
                      });
            },
            slotToRecoveryDelay.apply(slot))
        .finishWarn(LOG);
  }

  private void pruneRecoveryTasks() {
    final Iterator<SlotAndBlockRoot> keysIterator =
        recoveryTasks.keySet().stream().sorted().iterator();
    while (recoveryTasks.size() >= recoveryTasksSizeTarget && keysIterator.hasNext()) {
      final SlotAndBlockRoot key = keysIterator.next();
      recoveryTasks.remove(key);
    }
  }

  private void pruneCompletedSlots() {
    final int slotsToPrune = completedSlots.size() - completedSlotsSizeTarget;
    if (slotsToPrune >= 0) {
      for (int i = 0; i <= slotsToPrune; i++) {
        completedSlots.removeFirst();
      }
    }
  }

  private boolean shouldSkipProcessing(final UInt64 slot) {
    if (isActiveSuperNode(slot)) {
      return false;
    }
    if (custodyGroupCountManager.getCustodyGroupCount() == groupCount) {
      if (!isSuperNode.get()) {
        LOG.debug(
            "Number of required custody groups reached maximum. Activating super node reconstruction.");
        isSuperNode.set(true);
      }
      return false;
    }
    return true;
  }

  protected void maybeStartRecovery(final RecoveryTask task) {
    if (readyToBeRecovered(task)) {
      if (task.recoveryStarted.compareAndSet(false, true)) {
        if (task.existingSidecars.size() == columnCount) {
          completedSlots.add(
              task.existingSidecars.values().stream()
                  .findFirst()
                  .orElseThrow()
                  .getSlotAndBlockRoot());
          task.existingSidecars.clear();
          return;
        }
        scheduleRecoveryTask(task);
      }
    }
  }

  @VisibleForTesting
  protected void scheduleRecoveryTask(final RecoveryTask task) {
    asyncRunner
        .runAsync(() -> prepareAndInitiateRecovery(task))
        .whenException(
            ex -> {
              LOG.debug(
                  "Error during recovery of {} task with {} sidecars",
                  task.slotAndBlockRoot,
                  task.existingSidecars.size(),
                  ex);
              // release task for future retries only if error happened during recovery
              task.recoveryStarted.set(false);
            })
        .finishError(LOG);
  }

  private boolean readyToBeRecovered(final RecoveryTask task) {
    if (task.recoveryStarted().get()) {
      // already started
      return false;
    }

    if (!task.timedOut().get()) {
      return false;
    }

    if (task.existingSidecars().size() < recoverColumnCount) {
      // not enough columns collected
      return false;
    }

    return true;
  }

  private boolean isActiveSuperNode(final UInt64 slot) {
    return isSuperNode.get()
        && spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU);
  }

  protected record RecoveryTask(
      SlotAndBlockRoot slotAndBlockRoot,
      Map<DataColumnSlotAndIdentifier, DataColumnSidecar> existingSidecars,
      AtomicBoolean recoveryStarted,
      AtomicBoolean timedOut) {}

  private void prepareAndInitiateRecovery(final RecoveryTask task) {
    LOG.debug(
        "Recovery for block: {}. DataColumnSidecars found: {}",
        task.slotAndBlockRoot,
        task.existingSidecars.size());

    try (final MetricsHistogram.Timer timer =
        dataAvailabilityReconstructionTimeSeconds.startTimer()) {
      initiateRecovery(task, task.existingSidecars.values(), timer);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initiateRecovery(
      final RecoveryTask recoveryTask,
      final Collection<DataColumnSidecar> sidecars,
      final MetricsHistogram.Timer timer) {
    final List<DataColumnSidecar> recoveredSidecars =
        MiscHelpersFulu.required(spec.atSlot(recoveryTask.slotAndBlockRoot.getSlot()).miscHelpers())
            .reconstructAllDataColumnSidecars(sidecars);
    timer.closeUnchecked().run();

    final Set<UInt64> existingSidecarsIndices =
        sidecars.stream().map(DataColumnSidecar::getIndex).collect(Collectors.toUnmodifiableSet());
    totalDataAvailabilityReconstructedColumns.inc(recoveredSidecars.size() - sidecars.size());
    recoveredSidecars.stream()
        .filter(sidecar -> !existingSidecarsIndices.contains(sidecar.getIndex()))
        .forEach(
            dataColumnSidecar -> {
              delegate
                  .onNewValidatedDataColumnSidecar(dataColumnSidecar, RECOVERED)
                  .finishError(LOG);
              if (inSync) {
                dataColumnSidecarPublisher.accept(dataColumnSidecar, RECOVERED);
              }
              recoveredColumnSidecarSubscribers.forEach(
                  subscriber -> subscriber.onNewValidSidecar(dataColumnSidecar, RECOVERED));
            });
    completedSlots.add(sidecars.stream().findFirst().orElseThrow().getSlotAndBlockRoot());
    recoveryTask.existingSidecars.clear();
    LOG.debug(
        "Data column sidecars recovery finished for block: {}", recoveryTask.slotAndBlockRoot);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    if (completedSlots.contains(dataColumnSidecar.getSlotAndBlockRoot())) {
      return SafeFuture.COMPLETE;
    }
    // Recovery is not needed for locally produced or recovered data,
    // we will get everything for it in custody w/o reconstruction
    if (remoteOrigin.equals(RemoteOrigin.RPC) || remoteOrigin.equals(RemoteOrigin.GOSSIP)) {
      LOG.debug(
          "sidecar: {} {} - remoteOrigin: {}",
          dataColumnSidecar::getSlotAndBlockRoot,
          dataColumnSidecar::getIndex,
          () -> remoteOrigin);
      createOrUpdateRecoveryTaskForDataColumnSidecar(dataColumnSidecar);
    }
    return delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar, remoteOrigin);
  }

  private void createOrUpdateRecoveryTaskForDataColumnSidecar(final DataColumnSidecar sidecar) {
    final RecoveryTask task =
        recoveryTasks.computeIfAbsent(
            sidecar.getSlotAndBlockRoot(),
            __ ->
                new RecoveryTask(
                    sidecar.getSlotAndBlockRoot(),
                    new ConcurrentHashMap<>(),
                    new AtomicBoolean(false),
                    new AtomicBoolean(false)));
    task.existingSidecars().put(DataColumnSlotAndIdentifier.fromDataColumn(sidecar), sidecar);
    maybeStartRecovery(task);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.hasCustodyDataColumnSidecar(columnId);
  }

  @Override
  public void subscribeToRecoveredColumnSidecar(
      final ValidDataColumnSidecarsListener sidecarListener) {
    recoveredColumnSidecarSubscribers.subscribe(sidecarListener);
  }

  @VisibleForTesting
  protected NavigableSet<SlotAndBlockRoot> getCompletedSlots() {
    return completedSlots;
  }

  @VisibleForTesting
  protected ConcurrentHashMap<SlotAndBlockRoot, RecoveryTask> getRecoveryTasks() {
    return recoveryTasks;
  }
}

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

package tech.pegasys.teku.statetransition.datacolumns.retriever.recovering;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

class RebuildColumnsTask {
  private static final Logger LOG = LogManager.getLogger();

  protected final List<PendingRecoveryRequest> tasks = new CopyOnWriteArrayList<>();
  private final SlotAndBlockRoot slotAndBlockRoot;
  private final UInt64 timeoutMillis;
  private final int minimumColumnsForRebuild;
  private final DataColumnSidecarDbAccessor sidecarDB;
  private final Spec spec;

  protected SafeFuture<Void> query;
  protected boolean isReadyToRebuild = false;
  protected final Map<Integer, DataColumnSidecar> sidecarMap = new ConcurrentSkipListMap<>();
  protected final AtomicBoolean done = new AtomicBoolean(false);

  RebuildColumnsTask(
      final SlotAndBlockRoot slotAndBlockRoot,
      final UInt64 timestampMillis,
      final Duration timeout,
      final int minimumColumnsForRebuild,
      final DataColumnSidecarDbAccessor sidecarDB,
      final Spec spec) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    this.timeoutMillis = timestampMillis.plus(timeout.toMillis());
    this.minimumColumnsForRebuild = minimumColumnsForRebuild;
    this.sidecarDB = sidecarDB;
    this.spec = spec;
    query = SafeFuture.COMPLETE;
  }

  // returns true if the task is added successfully
  // returns false if the task didn't match the current rebuild and got cancelled
  boolean addTask(final PendingRecoveryRequest pendingRequest) {
    if (done.get()) {
      final DataColumnSidecar sidecar = sidecarMap.get(pendingRequest.getIndex().intValue());
      if (sidecar != null
          && sidecar.getSlotAndBlockRoot().equals(pendingRequest.getSlotAndBlockRoot())) {
        LOG.trace(
            "Pending request (slotAndBlock: {}) for column {} matched completed rebuild",
            pendingRequest.getSlotAndBlockRoot(),
            pendingRequest.getIndex());

        pendingRequest.complete(sidecar);
        return true;
      } else {
        pendingRequest.cancel();
        LOG.debug(
            "Pending request (slotAndBlock: {}) for column {} was not satisfied by finished rebuild for {}",
            pendingRequest.getSlotAndBlockRoot(),
            pendingRequest.getIndex(),
            slotAndBlockRoot);
        return false;
      }
    }
    if (pendingRequest.getBlockRoot().equals(slotAndBlockRoot.getBlockRoot())
        && !pendingRequest.isDone()) {
      // we are possibly downloading to rebuild, so the sidecar may be available
      final DataColumnSidecar sidecar = sidecarMap.get(pendingRequest.getIndex().intValue());
      if (sidecar == null) {
        LOG.trace(
            "Pending request for column {} added to rebuild for (slotAndBlock: {})",
            pendingRequest.getIndex(),
            pendingRequest.getSlotAndBlockRoot());
        tasks.add(pendingRequest);
      } else {
        // we've already got the required data for this specific request, so can complete it
        LOG.debug(
            "Pending request (slotAndBlock: {}) for column {} was found while preparing to rebuild",
            pendingRequest.getSlotAndBlockRoot(),
            pendingRequest.getIndex());
        pendingRequest.complete(sidecar);
      }
    } else {
      pendingRequest.cancel();
      return false;
    }
    checkQueryResult();
    return true;
  }

  synchronized void checkQueryResult() {
    if (done.get() || isReadyToRebuild) {
      LOG.trace("checkQuery called done: {}, readyToRebuild: {}", done.get(), isReadyToRebuild);
      return;
    }
    if (!query.isDone()) {
      LOG.trace("Columns query is still running at {}", slotAndBlockRoot);
      return;
    }

    if (sidecarMap.size() >= minimumColumnsForRebuild) {
      isReadyToRebuild = true;
      LOG.debug("Determined we have sufficient columns to rebuild columns at {}", slotAndBlockRoot);
      rebuild();
    } else {
      LOG.trace("Running slot query for columns at {}", slotAndBlockRoot);
      query =
          sidecarDB
              .getColumnIdentifiers(slotAndBlockRoot)
              .thenAccept(this::fetchColumnsFromStorage)
              .exceptionally(
                  error -> {
                    LOG.error(
                        "Exception occurred while retrieving existing data column sidecars from database for slot {} and block {}",
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        error);
                    return null;
                  });
    }
  }

  // called from checkQueryResult if there were sufficient columns
  private void rebuild() {
    if (!done.get()) {
      LOG.debug(
          "Rebuilding columns at {}, {} columns in cache", slotAndBlockRoot, sidecarMap.size());
      final Map<UInt64, DataColumnSidecar> reconstructedSidecars =
          MiscHelpersFulu.required(spec.atSlot(slotAndBlockRoot.getSlot()).miscHelpers())
              .reconstructAllDataColumnSidecars(sidecarMap.values())
              .stream()
              .collect(
                  Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, Function.identity()));
      LOG.debug(
          "Rebuild KZG call completed at {}, have {} sidecars as a result",
          slotAndBlockRoot,
          reconstructedSidecars.size());
      reconstructedSidecars
          .values()
          .forEach(
              sidecar ->
                  tasks.stream()
                      .filter(task -> !task.isDone())
                      .filter(task -> sidecar.getIndex().equals(task.getIndex()))
                      .forEach(task -> task.complete(sidecar)));
      reconstructedSidecars.forEach((k, v) -> sidecarMap.putIfAbsent(k.intValue(), v));
      done.compareAndSet(false, true);
      LOG.debug("Rebuilding columns DONE {}", slotAndBlockRoot);
    } else {
      LOG.trace("Called rebuild while rebuild task was marked done already.");
    }
  }

  // chained from checkQueryResult
  protected void fetchColumnsFromStorage(
      final List<DataColumnSlotAndIdentifier> dataColumnSlotAndIdentifiers) {
    if (dataColumnSlotAndIdentifiers.isEmpty()) {
      LOG.trace("Found no data columns for {}", slotAndBlockRoot);
      return;
    } else if (dataColumnSlotAndIdentifiers.size() < minimumColumnsForRebuild) {
      LOG.trace(
          "Found {} columns cached, which is insufficient for a rebuild at {}",
          dataColumnSlotAndIdentifiers.size(),
          slotAndBlockRoot);
      return;
    }
    LOG.trace(
        "Fetching available data columns for slot {}, {} candidates",
        slotAndBlockRoot,
        dataColumnSlotAndIdentifiers.size());
    for (DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier : dataColumnSlotAndIdentifiers) {
      if (!sidecarMap.containsKey(dataColumnSlotAndIdentifier.columnIndex().intValue())) {
        LOG.trace("attempting to fetch column {}", dataColumnSlotAndIdentifier);
        sidecarDB
            .getSidecar(dataColumnSlotAndIdentifier)
            .thenAccept(
                maybeSidecar ->
                    maybeSidecar.ifPresentOrElse(
                        sidecar -> {
                          LOG.trace(
                              "root {} rebuild - found sidecar {}",
                              slotAndBlockRoot.getBlockRoot(),
                              dataColumnSlotAndIdentifier.columnIndex().intValue());
                          sidecarMap.putIfAbsent(
                              dataColumnSlotAndIdentifier.columnIndex().intValue(), sidecar);
                        },
                        () -> LOG.trace("sidecar not found {}", dataColumnSlotAndIdentifier)))
            .finishError(LOG);
      } else {
        LOG.trace(
            "Found key already in map {}", dataColumnSlotAndIdentifier.columnIndex().intValue());
      }
    }
  }

  void cancel() {
    if (done.get()) {
      return;
    }
    if (done.compareAndSet(false, true)) {
      tasks.forEach(PendingRecoveryRequest::cancel);
      query.cancel(true);
    }
  }

  boolean isDone(final UInt64 currentTimeMillis) {
    if (done.get()) {
      return true;
    }
    // if the timeout is exceeded and we're not ready to rebuild, then we should cancel the task
    if (currentTimeMillis.isGreaterThanOrEqualTo(this.timeoutMillis) && !isReadyToRebuild) {
      cancel();
      return true;
    }
    return false;
  }
}

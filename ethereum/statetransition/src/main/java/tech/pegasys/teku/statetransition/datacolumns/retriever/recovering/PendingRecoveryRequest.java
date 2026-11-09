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

import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.CANCELLED;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.DOWNLOADED;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.RECOVERED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

class PendingRecoveryRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final SafeFuture<DataColumnSidecar> future = new SafeFuture<>();
  private final DataColumnSlotAndIdentifier columnnId;
  private final UInt64 downloadTimeoutMillis;
  private final UInt64 taskTimeoutMillis;
  private final SafeFuture<DataColumnSidecar> downloadFuture;
  private final LabelledMetric<Counter> sidecarRecoveryMetric;
  private final Runnable alwaysAfter;
  private final AtomicBoolean isStarted = new AtomicBoolean(false);

  PendingRecoveryRequest(
      final DataColumnSlotAndIdentifier columnId,
      final SafeFuture<DataColumnSidecar> downloadFuture,
      final UInt64 timestamp,
      final Duration timeout,
      final Duration downloadTimeout,
      final LabelledMetric<Counter> sidecarRecoveryMetric,
      final Runnable alwaysAfter) {
    this.downloadTimeoutMillis = timestamp.plus(downloadTimeout.toMillis());
    this.taskTimeoutMillis = timestamp.plus(timeout.toMillis());
    this.columnnId = columnId;
    this.downloadFuture = downloadFuture;
    this.sidecarRecoveryMetric = sidecarRecoveryMetric;
    this.alwaysAfter = alwaysAfter;
  }

  public void start() {
    if (isStarted.compareAndSet(false, true)) {
      downloadFuture
          .thenRun(this::downloadCompleted)
          .exceptionally(this::failedDownload)
          .finishError(LOG);

      future
          .thenRun(this::onCompleted)
          .exceptionally(
              (err) -> {
                final Throwable cause = Throwables.getRootCause(err);
                if (!(cause instanceof CancellationException)) {
                  LOG.debug("Failed recovery task for column {}", columnnId, err);
                }
                sidecarRecoveryMetric.labels(CANCELLED).inc();
                return null;
              })
          .always(alwaysAfter);
    }
  }

  private Void failedDownload(final Throwable throwable) {
    final Throwable cause = Throwables.getRootCause(throwable);
    if (!(cause instanceof CancellationException)) {
      LOG.debug("Failed downloading column {}", columnnId, throwable);
    }
    return null;
  }

  // needed for the interface but generally should not be referenced
  SafeFuture<DataColumnSidecar> getFuture() {
    return future;
  }

  UInt64 getIndex() {
    return columnnId.columnIndex();
  }

  UInt64 getSlot() {
    return columnnId.slot();
  }

  Bytes32 getBlockRoot() {
    return columnnId.blockRoot();
  }

  boolean isFailedDownloading() {
    return downloadFuture.isCompletedExceptionally();
  }

  @VisibleForTesting
  SafeFuture<DataColumnSidecar> getDownloadFuture() {
    return downloadFuture;
  }

  void checkTimeout(final UInt64 currentTimeMillis) {
    if (currentTimeMillis.isGreaterThanOrEqualTo(downloadTimeoutMillis)
        && !downloadFuture.isDone()) {
      LOG.trace("Cancelling download of {} due to timeout", columnnId);
      downloadFuture.cancel(true);
    }
    if (currentTimeMillis.isGreaterThanOrEqualTo(taskTimeoutMillis) && !future.isDone()) {
      LOG.trace("Cancelling task {} due to timeout", columnnId);
      future.cancel(true);
    }
  }

  private void onCompleted() {
    if (downloadFuture.isCompletedNormally()) {
      sidecarRecoveryMetric.labels(DOWNLOADED).inc();
    } else {
      sidecarRecoveryMetric.labels(RECOVERED).inc();
    }
  }

  void complete(final DataColumnSidecar sidecar) {
    future.complete(sidecar);
  }

  void cancel() {
    downloadFuture.cancel(true);
    future.cancel(true);
  }

  boolean isDone() {
    return future.isDone();
  }

  private void downloadCompleted() {
    LOG.trace("Successfully downloaded column {}", columnnId);
    downloadFuture.propagateTo(future);
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return columnnId.getSlotAndBlockRoot();
  }
}

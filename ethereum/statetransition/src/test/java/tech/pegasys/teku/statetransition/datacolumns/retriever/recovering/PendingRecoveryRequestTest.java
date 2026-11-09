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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetrieverTest.RECOVERY_TIMEOUT;

import java.time.Duration;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PendingRecoveryRequestTest {
  static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(5);
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1_000_000);
  private final DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();
  private static final StubMetricsSystem METRICS = new StubMetricsSystem();
  private static final LabelledMetric<Counter> METRIC =
      METRICS.createLabelledCounter(TekuMetricCategory.BEACON, "FOO", "help", "result");

  @Test
  void checkTimeout_willNotTimeoutDownloadBeforeTime() {
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    final UInt64 lastMilliBeforeDownloadTimeout =
        timeProvider.getTimeInMillis().plus(DOWNLOAD_TIMEOUT.toMillis()).minus(1);
    request.checkTimeout(lastMilliBeforeDownloadTimeout);
    assertThat(request.getFuture().isDone()).isFalse();
    assertThat(downloadFuture.isDone()).isFalse();
  }

  @Test
  void checkTimeout_willTimeoutIfNotDownloadedAtDownloadLimit() {
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    final UInt64 downloadTimeoutMillis =
        timeProvider.getTimeInMillis().plus(DOWNLOAD_TIMEOUT.toMillis());
    request.checkTimeout(downloadTimeoutMillis);
    assertThat(request.getFuture().isDone()).isFalse();
    assertThat(downloadFuture.isDone()).isTrue();
  }

  @Test
  void checkTimeout_willNotTimeoutIfMarkedDone() {
    final UInt64 timeoutMillis = timeProvider.getTimeInMillis().plus(RECOVERY_TIMEOUT.toMillis());
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);

    downloadFuture.complete(sidecar);
    request.checkTimeout(timeoutMillis);
    assertThat(downloadFuture).isCompletedWithValue(sidecar);
    assertThat(request.getFuture()).isCompletedWithValue(sidecar);
  }

  @Test
  void checkTimeout_willNotTimeoutBeforeTime() {
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    final UInt64 lastMilliBeforeRecoveryTimeout =
        timeProvider.getTimeInMillis().plus(RECOVERY_TIMEOUT.toMillis()).minus(1);
    request.checkTimeout(lastMilliBeforeRecoveryTimeout);
    assertThat(request.getFuture().isDone()).isFalse();
    assertThat(downloadFuture.isDone()).isTrue();
  }

  @Test
  void checkTimeout_willTimeoutDownloadAndRecoveryAtLimit() {
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    final UInt64 timeout = timeProvider.getTimeInMillis().plus(RECOVERY_TIMEOUT.toMillis());
    request.checkTimeout(timeout);
    assertThat(request.getFuture().isDone()).isTrue();
    assertThat(downloadFuture.isDone()).isTrue();
  }

  @Test
  void checkTimeout_willNotTimeoutDownloadIfDownloadIsDone() {
    final UInt64 downloadTimeoutMillis =
        timeProvider.getTimeInMillis().plus(DOWNLOAD_TIMEOUT.toMillis());
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    assertThat(request.getFuture().isDone()).isFalse();

    downloadFuture.complete(sidecar);
    request.checkTimeout(downloadTimeoutMillis);
    assertThat(downloadFuture).isCompletedWithValue(sidecar);
    assertThat(request.getFuture()).isCompletedWithValue(sidecar);
  }

  @Test
  void shouldCompleteTaskIfDownloadCompletes() {
    final SafeFuture<DataColumnSidecar> downloadFuture = new SafeFuture<>();
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), downloadFuture);
    assertThat(request.getFuture().isDone()).isFalse();

    downloadFuture.complete(sidecar);
    assertThat(request.getFuture()).isCompletedWithValue(sidecar);
  }

  @Test
  void futureIsNotCompleteWhenDownloadFails() {
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), new SafeFuture<>());

    request.getDownloadFuture().cancel(true);

    assertThat(request.getFuture().isDone()).isFalse();
    assertThat(request.getDownloadFuture().isCancelled()).isTrue();
    assertThat(request.isFailedDownloading()).isTrue();
  }

  @Test
  void allFuturesStoppedAfterRequestCancelled() {
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.empty(), new SafeFuture<>());

    request.cancel();

    assertThat(request.getFuture().isDone()).isTrue();
    assertThat(request.getDownloadFuture().isCancelled()).isTrue();
    assertThat(request.isFailedDownloading()).isTrue();
  }

  @Test
  void canGetColumnDetails() {
    final int column = 13;
    final DataColumnSlotAndIdentifier id =
        SidecarRetrieverTest.createId(dataStructureUtil.randomBeaconBlock(100), column);
    final PendingRecoveryRequest request =
        createPendingRecoveryRequest(Optional.of(id), new SafeFuture<>());
    assertThat(request.getSlot()).isEqualTo(id.getSlotAndBlockRoot().getSlot());
    assertThat(request.getBlockRoot()).isEqualTo(id.getSlotAndBlockRoot().getBlockRoot());
    assertThat(request.getSlotAndBlockRoot()).isEqualTo(id.getSlotAndBlockRoot());
    assertThat(request.getIndex().intValue()).isEqualTo(column);
  }

  final PendingRecoveryRequest createPendingRecoveryRequest(
      final Optional<DataColumnSlotAndIdentifier> maybeId,
      final SafeFuture<DataColumnSidecar> downloadFuture) {
    final DataColumnSlotAndIdentifier id =
        maybeId.orElse(SidecarRetrieverTest.createId(dataStructureUtil.randomBeaconBlock(), 1));
    final PendingRecoveryRequest pendingRecoveryRequest =
        new PendingRecoveryRequest(
            id,
            downloadFuture,
            timeProvider.getTimeInMillis(),
            RECOVERY_TIMEOUT,
            DOWNLOAD_TIMEOUT,
            METRIC,
            () -> {});
    pendingRecoveryRequest.start();
    return pendingRecoveryRequest;
  }
}

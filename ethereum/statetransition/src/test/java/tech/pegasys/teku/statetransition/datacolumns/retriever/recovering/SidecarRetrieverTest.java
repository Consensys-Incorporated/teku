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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.CANCELLED;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.DOWNLOADED;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.RECOVERED;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever.RECOVERY_METRIC_NAME;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.KzgUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolverStub;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetrieverStub;

public class SidecarRetrieverTest {

  static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(10);
  static final Duration CHECK_INTERVAL = Duration.ofSeconds(1);

  final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(db).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final CustodyGroupCountManager custodyManager = mock(CustodyGroupCountManager.class);

  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final MiscHelpersFulu miscHelpers =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  // final KZG kzg = KZG.getInstance(false);
  final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private final DataColumnSidecarRetrieverStub delegateRetriever =
      new DataColumnSidecarRetrieverStub();

  private SidecarRetriever retriever;

  @BeforeEach
  void setUp() {
    this.retriever =
        new SidecarRetriever(
            delegateRetriever,
            spec,
            dbAccessor,
            stubAsyncRunner,
            RECOVERY_TIMEOUT,
            RECOVERY_TIMEOUT.dividedBy(2),
            CHECK_INTERVAL,
            timeProvider,
            columnCount,
            custodyManager,
            metricsSystem);
    retriever.start();
  }

  @Test
  void callingStartAgainIsOk() {
    final Cancellable checker = retriever.getPendingRequestsChecker();
    retriever.start();
    assertThat(retriever.getPendingRequestsChecker()).isEqualTo(checker);
  }

  @Test
  void canStopRetriever() {
    assertThat(retriever.getPendingRequestsChecker()).isNotNull();
    assertThat(retriever.getPendingRequestsChecker().isCancelled()).isFalse();
    retriever.stop();
    assertThat(retriever.getPendingRequestsChecker()).isNull();
  }

  @Test
  void shouldCancelAfterDownloadTimeoutWhenNotRebuilding() {
    when(custodyManager.getCustodyGroupCount()).thenReturn(8);
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);
    timeProvider.advanceTimeBy(RECOVERY_TIMEOUT.dividedBy(2));
    stubAsyncRunner.executeQueuedActions();
    assertThat(response.isDone()).isTrue();
  }

  @Test
  void shouldInitiateRebuildIfCustodyAllColumnsAndDownloadingTimesOut() {
    when(custodyManager.getCustodyGroupCount()).thenReturn(128);
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);
    timeProvider.advanceTimeBy(RECOVERY_TIMEOUT.dividedBy(2));
    stubAsyncRunner.executeQueuedActions();
    assertThat(response.isDone()).isFalse();
  }

  @Test
  void canStopMoreThanOnce() {
    retriever.stop();
    assertThat(retriever.getPendingRequestsChecker()).isNull();

    retriever.stop();
    assertThat(retriever.getPendingRequestsChecker()).isNull();
  }

  @Test
  void onNewValidatedSidecar_callsDelegateRetriever() {
    final DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();
    retriever.onNewValidatedSidecar(sidecar, RemoteOrigin.GOSSIP);
    assertThat(delegateRetriever.validatedSidecars).containsExactly(sidecar);
  }

  @Test
  void flush_callsDelegateRetriever() {
    assertThat(delegateRetriever.flushed).isFalse();
    retriever.flush();
    assertThat(delegateRetriever.flushed).isTrue();
  }

  @Test
  void successfulRetrievalShouldRemoveFromPendingRequests() {
    final int blobCount = 1;
    final int columnsInDbCount = 1;
    final BeaconBlock block = blockResolver.addBlock(10, blobCount);
    final Blob blob = dataStructureUtil.randomValidBlob();
    final List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecars(
            dataStructureUtil.signedBlock(block),
            List.of(KzgUtil.computeBlobAndCellProofs(miscHelpers.getKzg(), blob)));
    final List<Integer> dbColumnIndices =
        IntStream.range(10, Integer.MAX_VALUE).limit(columnsInDbCount).boxed().toList();
    dbColumnIndices.forEach(idx -> assertThat(db.addSidecar(sidecars.get(idx))).isDone());

    final SafeFuture<DataColumnSidecar> res0 = retriever.retrieve(createId(block, 0));

    // this will add the file to the delegate, which will finish the download task
    delegateRetriever.addReadyColumnSidecar(sidecars.get(0));

    assertThat(retriever.pendingRequestCount()).isZero();
    assertThat(res0).isDone();
    verifyMetrics(0, 1, 0);
  }

  @Test
  void cancelledRetrievalShouldRemoveFromPendingRequests() {
    final BeaconBlock block = blockResolver.addBlock(10, 1);
    final SafeFuture<DataColumnSidecar> res0 = retriever.retrieve(createId(block, 0));
    res0.completeExceptionally(new RuntimeException("ERR"));
    assertThat(retriever.pendingRequestCount()).isZero();
    stubAsyncRunner.executeQueuedActions();
    verifyMetrics(1, 0, 0);
  }

  @Test
  void stopClearsPendingRequests() {
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);
    retriever.stop();
    assertThat(retriever.getPendingRequestsChecker()).isNull();
    assertThat(retriever.getPendingRequests()).isEmpty();
    assertThat(response).isCancelled();
    verifyMetrics(1, 0, 0);
  }

  @Test
  void shouldCheckPendingRequestsOnTimer() {
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);
    timeProvider.advanceTimeBy(CHECK_INTERVAL);
    stubAsyncRunner.executeQueuedActions();
    assertThat(retriever.pendingRequestCount()).isEqualTo(1);

    timeProvider.advanceTimeBy(RECOVERY_TIMEOUT.multipliedBy(2));
    stubAsyncRunner.executeQueuedActions();
    assertThat(retriever.pendingRequestCount()).isZero();
    assertThat(response).isCompletedExceptionally();
    verifyMetrics(1, 0, 0);
  }

  @Test
  void createDoesNotIncrementMetrics() {
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);
    assertThat(response).isNotDone();
    verifyMetrics(0, 0, 0);
  }

  @Test
  void shouldTimeoutAfterExpiryTime() {
    final BeaconBlock block = blockResolver.addBlock(10, 10);
    final DataColumnSlotAndIdentifier id = createId(block, 0);
    final SafeFuture<DataColumnSidecar> response = retriever.retrieve(id);

    for (int i = 0; i < 10; i++) {
      timeProvider.advanceTimeBy(CHECK_INTERVAL);
      stubAsyncRunner.executeQueuedActions();
    }
    verifyMetrics(1, 0, 0);
    assertThat(response).isCompletedExceptionally();
  }

  static DataColumnSlotAndIdentifier createId(final BeaconBlock block, final int colIdx) {
    return new DataColumnSlotAndIdentifier(
        block.getSlot(), block.getRoot(), UInt64.valueOf(colIdx));
  }

  private void verifyMetrics(
      final int cancelledCount, final int downloadedCount, final int recoveredCount) {
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.BEACON, RECOVERY_METRIC_NAME, CANCELLED))
        .isEqualTo(cancelledCount);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.BEACON, RECOVERY_METRIC_NAME, DOWNLOADED))
        .isEqualTo(downloadedCount);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.BEACON, RECOVERY_METRIC_NAME, RECOVERED))
        .isEqualTo(recoveredCount);
  }
}

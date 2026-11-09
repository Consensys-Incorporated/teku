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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetrieverTest.RECOVERY_TIMEOUT;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
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
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

class RebuildColumnsTaskTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1_000_000);
  private final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  private final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(db).spec(spec).build();
  private final PendingRecoveryRequest request = mock(PendingRecoveryRequest.class);
  private final SafeFuture<DataColumnSidecar> future = new SafeFuture<>();
  private static final StubMetricsSystem METRICS = new StubMetricsSystem();
  private static final LabelledMetric<Counter> METRIC =
      METRICS.createLabelledCounter(TekuMetricCategory.BEACON, "FOO", "help", "result");

  @Test
  void addTask_acceptsValidPendingRecoveryRequest() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    final RebuildColumnsTask task =
        new RebuildColumnsTask(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);

    assertThat(task.isDone(timestampMillis)).isFalse();
    assertThat(task.addTask(pendingRequest)).isTrue();
    assertThat(pendingRequest.getFuture()).isNotDone();
  }

  @Test
  void addTask_rejectsIfBlockrootMismatch() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTask task =
        new RebuildColumnsTask(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, false);

    assertThat(task.addTask(pendingRequest)).isFalse();
    assertThat(pendingRequest.getFuture()).isCancelled();
  }

  @Test
  void addTask_completesIfRootMismatches() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, false);

    assertThat(task.addTask(pendingRequest)).isFalse();
    assertThat(pendingRequest.getFuture()).isCancelled();
  }

  @Test
  void addTask_completesIfRebuildIsDoneAndColumnMissing() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);
    task.done.set(true);

    assertThat(task.addTask(pendingRequest)).isFalse();
    assertThat(pendingRequest.getFuture()).isCancelled();
  }

  @Test
  void addTask_completesIfColumnIsAvailableOnIncompleteRebuildTask() {
    final UInt64 column = UInt64.ONE;
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 2, dbAccessor, spec);

    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    when(sidecar.getSlot()).thenReturn(slotAndBlockRoot.getSlot());
    task.getSidecarMap().put(column.intValue(), sidecar);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);

    assertThat(task.addTask(pendingRequest)).isTrue();
    assertThat(pendingRequest.getFuture()).isCompletedWithValue(sidecar);
    assertThat(task.isDone(timestampMillis)).isFalse();

    verifyNoMoreInteractions(request);
  }

  @Test
  void addTask_completesIfFutureIsDoneAndEntryFound() {
    final UInt64 column = UInt64.ONE;
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    // when done and sidecar present
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    task.getDone().set(true);
    task.getSidecarMap().put(column.intValue(), sidecar);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, column, true);

    when(sidecar.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);

    assertThat(task.addTask(pendingRequest)).isTrue();
    assertThat(pendingRequest.getFuture()).isCompletedWithValue(sidecar);
  }

  @Test
  void cancel_worksOnDoneTask() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);

    task.done.set(true);
    task.cancel();

    assertThat(task.isDone(timestampMillis)).isTrue();
    // we hacked done, so this task wasn't cleaned properly.
    assertThat(pendingRequest.getFuture()).isNotDone();
  }

  @Test
  void cancel_shouldMarkAsDone() {

    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);
    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);

    task.getPendingTasks().add(pendingRequest);
    task.cancel();

    assertThat(task.isDone(timestampMillis)).isTrue();
    assertThat(pendingRequest.getFuture()).isCancelled();
  }

  @Test
  void isDone_handlesTimeout() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);
    final PendingRecoveryRequestTestArticle pendingRequest =
        createPendingRecovery(slotAndBlockRoot, UInt64.ONE, true);

    task.getPendingTasks().add(pendingRequest);
    timeProvider.advanceTimeBy(RECOVERY_TIMEOUT);

    assertThat(task.isDone(timeProvider.getTimeInMillis())).isTrue();
    assertThat(pendingRequest.getFuture()).isCancelled();
  }

  @Test
  void checkQueryResult_readyToRebuild() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final DataColumnSidecarDbAccessor dbAccessor = mock(DataColumnSidecarDbAccessor.class);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> future = new SafeFuture<>();
    when(dbAccessor.getColumnIdentifiers(slotAndBlockRoot)).thenReturn(future);
    when(dbAccessor.getSidecar(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomDataColumnSidecar())));
    final List<DataColumnSlotAndIdentifier> data =
        IntStream.range(0, 64)
            .mapToObj(
                i ->
                    new DataColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(i)))
            .toList();
    // start query
    task.checkQueryResult();
    // the subsequent fetch that's run
    future.complete(data);
    // second run we'll determine the query is done, and we're ready to rebuild
    task.checkQueryResult();

    assertThat(task.isReadyToRebuild()).isTrue();
    assertThat(task.getSidecarMap().size()).isEqualTo(64);
  }

  @Test
  void checkQueryResult_notReadyToRebuild() {
    final UInt64 timestampMillis = timeProvider.getTimeInMillis();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot(UInt64.ZERO);
    final DataColumnSidecarDbAccessor dbAccessor = mock(DataColumnSidecarDbAccessor.class);
    final RebuildColumnsTaskTestArticle task =
        new RebuildColumnsTaskTestArticle(
            slotAndBlockRoot, timestampMillis, RECOVERY_TIMEOUT, 1, dbAccessor, spec);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> future = new SafeFuture<>();
    when(dbAccessor.getColumnIdentifiers(slotAndBlockRoot))
        .thenReturn(future)
        .thenReturn(new SafeFuture<>());
    final List<DataColumnSlotAndIdentifier> data =
        IntStream.range(0, 63)
            .mapToObj(
                i ->
                    new DataColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(i)))
            .toList();
    // start query
    task.checkQueryResult();
    // the subsequent fetch that's run
    future.complete(data);
    // second run we'll determine the query is done, but  we didn't find sufficient columns
    task.checkQueryResult();

    assertThat(task.isReadyToRebuild()).isFalse();
    assertThat(task.getSidecarMap()).isEmpty();
    assertThat(task.getDone()).isFalse();
    assertThat(task.getQuery()).isNotDone();
  }

  final PendingRecoveryRequestTestArticle createPendingRecovery(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 column, final boolean isMatchingRoot) {
    final DataColumnSlotAndIdentifier columnId =
        new DataColumnSlotAndIdentifier(
            slotAndBlockRoot.getSlot(),
            isMatchingRoot ? slotAndBlockRoot.getBlockRoot() : dataStructureUtil.randomBytes32(),
            column);
    final PendingRecoveryRequestTestArticle pendingRecoveryRequest =
        new PendingRecoveryRequestTestArticle(future, columnId, timeProvider.getTimeInMillis());
    pendingRecoveryRequest.start();
    return pendingRecoveryRequest;
  }

  static class PendingRecoveryRequestTestArticle extends PendingRecoveryRequest {
    PendingRecoveryRequestTestArticle(
        final SafeFuture<DataColumnSidecar> downloadFuture,
        final DataColumnSlotAndIdentifier columnId,
        final UInt64 timestamp) {
      super(
          columnId,
          downloadFuture,
          timestamp,
          RECOVERY_TIMEOUT.dividedBy(2),
          RECOVERY_TIMEOUT,
          METRIC,
          () -> {});
    }
  }

  static class RebuildColumnsTaskTestArticle extends RebuildColumnsTask {

    RebuildColumnsTaskTestArticle(
        final SlotAndBlockRoot slotAndBlockRoot,
        final UInt64 timestampMillis,
        final Duration timeout,
        final int minimumColumnsForRebuild,
        final DataColumnSidecarDbAccessor sidecarDB,
        final Spec spec) {
      super(slotAndBlockRoot, timestampMillis, timeout, minimumColumnsForRebuild, sidecarDB, spec);
    }

    boolean isReadyToRebuild() {
      return isReadyToRebuild;
    }

    SafeFuture<Void> getQuery() {
      return query;
    }

    AtomicBoolean getDone() {
      return done;
    }

    Map<Integer, DataColumnSidecar> getSidecarMap() {
      return sidecarMap;
    }

    List<PendingRecoveryRequest> getPendingTasks() {
      return tasks;
    }
  }
}

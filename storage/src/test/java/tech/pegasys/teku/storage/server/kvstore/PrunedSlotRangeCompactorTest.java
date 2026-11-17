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

package tech.pegasys.teku.storage.server.kvstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotKeyedColumn;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotRangeDataFile;

class PrunedSlotRangeCompactorTest {

  private static final SlotKeyedColumn COLUMN = SlotKeyedColumn.DATA_COLUMN_SIDECARS;
  private static final String LABEL = "data_column_sidecars";
  private static final String COMPACTION_TIME = "pruned_slots_compaction_time";
  private static final String TOMBSTONES = "pruned_slots_tombstones";
  private static final String BACKLOG_BYTES = "pruned_slots_compaction_backlog_bytes";
  private static final long MAX_BYTES = 1_000;

  private final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1_000);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PrunedSlotRangeCompactor compactor =
      new PrunedSlotRangeCompactor(
          dao,
          COLUMN,
          10,
          MAX_BYTES,
          timeProvider,
          PrunedSlotRangeCompactor.Gauges.create(metricsSystem));

  @Test
  void shouldNotCompactUntilEnoughSlotsArePruned() {
    // Slots 0 to 8 are only 9 slots
    compactor.onPruned(UInt64.valueOf(8));

    verify(dao, never()).compactSlotRange(any(), any(), any());
    assertThat(gaugeValue(COMPACTION_TIME)).isEmpty();
  }

  @Test
  void shouldCompactTheWholeRangeWhenTheDatabaseCannotListItsDataFiles() {
    when(dao.getSlotRangeDataFiles(any(), any(), any())).thenReturn(Optional.empty());

    compactor.onPruned(UInt64.valueOf(100_000));

    verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(100_000));
  }

  @Test
  void shouldSkipSlotsThatNoDataFileHolds() {
    when(dao.getSlotRangeDataFiles(any(), any(), any())).thenReturn(Optional.of(List.of()));

    compactor.onPruned(UInt64.valueOf(20));
    compactor.onPruned(UInt64.valueOf(30));

    verify(dao, never()).compactSlotRange(any(), any(), any());
    // The second prune only looks at the slots pruned since the first
    verify(dao).getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(20));
    verify(dao).getSlotRangeDataFiles(COLUMN, UInt64.valueOf(21), UInt64.valueOf(30));
    assertThat(gaugeValue(COMPACTION_TIME)).isEmpty();
  }

  @Test
  void shouldStartAtTheFirstDataFileInTheRange() {
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000)))
        .thenReturn(Optional.of(List.of(file(600, 2_000, 100), file(500, 700, 100))));

    compactor.onPruned(UInt64.valueOf(1_000));

    verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(500), UInt64.valueOf(1_000));
  }

  @Test
  void shouldStopBeforeTheDataFileThatWouldTakeTheCompactionOverTheBudget() {
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000)))
        .thenReturn(
            Optional.of(
                List.of(
                    file(300, 399, 400),
                    file(100, 199, 400),
                    file(200, 299, 400),
                    file(400, 1_500, 10))));
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.valueOf(300), UInt64.valueOf(1_010)))
        .thenReturn(Optional.of(List.of(file(300, 399, 400), file(400, 1_500, 10))));

    compactor.onPruned(UInt64.valueOf(1_000));
    // The next prune carries on where the last compaction stopped
    compactor.onPruned(UInt64.valueOf(1_010));

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(100), UInt64.valueOf(299));
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(300), UInt64.valueOf(1_010));
  }

  @Test
  void shouldReportTheBytesOfDataFilesStillToCompact() {
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000)))
        .thenReturn(
            Optional.of(
                List.of(
                    file(100, 199, 400),
                    file(200, 299, 400),
                    file(300, 399, 400),
                    file(400, 1_500, 10))));
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.valueOf(300), UInt64.valueOf(1_000)))
        .thenReturn(Optional.of(List.of(file(300, 399, 400), file(400, 1_500, 10))));

    compactor.onPruned(UInt64.valueOf(1_000));
    // Slots 300 onwards are still to compact
    assertThat(gaugeValue(BACKLOG_BYTES)).hasValue(410);

    compactor.onNothingPruned();
    assertThat(gaugeValue(BACKLOG_BYTES)).hasValue(0);
  }

  @Test
  void shouldReportNoBacklogWhenNoDataFileHoldsThePrunedSlots() {
    when(dao.getSlotRangeDataFiles(any(), any(), any())).thenReturn(Optional.of(List.of()));

    compactor.onPruned(UInt64.valueOf(20));

    assertThat(gaugeValue(BACKLOG_BYTES)).hasValue(0);
  }

  @Test
  void shouldNotReportABacklogWhenTheDatabaseCannotListItsDataFiles() {
    compactor.onPruned(UInt64.valueOf(20));

    verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(20));
    assertThat(gaugeValue(BACKLOG_BYTES)).isEmpty();
  }

  @Test
  void shouldAlwaysCompactTheDataFilesHoldingTheStartSlot() {
    // A single file over the budget still has to be compacted for the range to make progress
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000)))
        .thenReturn(
            Optional.of(List.of(file(100, 5_000, 5_000), file(100, 150, 10), file(200, 300, 10))));

    compactor.onPruned(UInt64.valueOf(1_000));

    verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(100), UInt64.valueOf(199));
  }

  @Test
  void shouldDoNothingWhenNothingIsPrunedBeforeAnythingWasPruned() {
    compactor.onNothingPruned();

    verify(dao, never()).getSlotRangeDataFiles(any(), any(), any());
    verify(dao, never()).compactSlotRange(any(), any(), any());
    verify(dao, never()).countSlotRangeTombstones(any(), any(), any());
  }

  @Test
  void shouldCarryOnWithTheBacklogWhenNothingIsPruned() {
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000)))
        .thenReturn(Optional.of(List.of(file(100, 199, 800), file(200, 299, 800))));
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.valueOf(200), UInt64.valueOf(1_000)))
        .thenReturn(Optional.of(List.of(file(200, 299, 800))));

    compactor.onPruned(UInt64.valueOf(1_000));
    compactor.onNothingPruned();

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(100), UInt64.valueOf(199));
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(200), UInt64.valueOf(1_000));
    // The tombstone count is refreshed after each compaction
    verify(dao, times(2)).countSlotRangeTombstones(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000));
  }

  @Test
  void shouldNotCompactWhenNothingIsPrunedWithoutABacklog() {
    compactor.onPruned(UInt64.valueOf(20));
    compactor.onNothingPruned();

    verify(dao, times(1)).compactSlotRange(any(), any(), any());
    verify(dao, times(1)).countSlotRangeTombstones(any(), any(), any());
  }

  @Test
  void shouldCarryOnUpToTheHighestPrunedSlotWhenNothingIsPruned() {
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.ZERO, UInt64.valueOf(30)))
        .thenReturn(Optional.of(List.of(file(0, 9, 800), file(10, 19, 800))));
    when(dao.getSlotRangeDataFiles(COLUMN, UInt64.valueOf(10), UInt64.valueOf(30)))
        .thenReturn(Optional.of(List.of(file(10, 19, 800))));

    compactor.onPruned(UInt64.valueOf(30));
    // A lower slot does not move the end of the backlog back
    compactor.onPruned(UInt64.valueOf(5));
    compactor.onNothingPruned();

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(9));
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(10), UInt64.valueOf(30));
  }

  @Test
  void shouldCompactFromSlotZeroOnTheFirstCompaction() {
    // Also covers the tombstones left before this node started
    compactor.onPruned(UInt64.valueOf(1_000));

    verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(1_000));
  }

  @Test
  void shouldCompactOnlyTheSlotsPrunedSinceTheLastCompaction() {
    compactor.onPruned(UInt64.valueOf(9));
    compactor.onPruned(UInt64.valueOf(15));
    compactor.onPruned(UInt64.valueOf(19));

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(9));
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(10), UInt64.valueOf(19));
    verify(dao, times(2)).compactSlotRange(any(), any(), any());
  }

  @Test
  void shouldIgnoreSlotsAlreadyCompacted() {
    compactor.onPruned(UInt64.valueOf(20));
    compactor.onPruned(UInt64.valueOf(5));

    verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(20));
    verify(dao, times(1)).compactSlotRange(any(), any(), any());
  }

  @Test
  void shouldRetryTheSameRangeAfterAFailedCompaction() {
    final RuntimeException error = new RuntimeException("compaction failed");
    doThrow(error).doNothing().when(dao).compactSlotRange(any(), any(), any());

    assertThatThrownBy(() -> compactor.onPruned(UInt64.valueOf(9))).isSameAs(error);
    compactor.onPruned(UInt64.valueOf(12));

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(9));
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(12));
  }

  @Test
  void shouldRecordHowLongTheLastCompactionTook() {
    doAnswer(
            invocation -> {
              timeProvider.advanceTimeByMillis(1_234);
              return null;
            })
        .doAnswer(
            invocation -> {
              timeProvider.advanceTimeByMillis(56);
              return null;
            })
        .when(dao)
        .compactSlotRange(any(), any(), any());

    compactor.onPruned(UInt64.valueOf(9));
    assertThat(gaugeValue(COMPACTION_TIME)).hasValue(1_234);

    compactor.onPruned(UInt64.valueOf(19));
    assertThat(gaugeValue(COMPACTION_TIME)).hasValue(56);
  }

  @Test
  void shouldReportTombstonesInThePrunedSlotsAfterEveryPrune() {
    when(dao.countSlotRangeTombstones(COLUMN, UInt64.ZERO, UInt64.valueOf(5)))
        .thenReturn(OptionalLong.of(42));
    when(dao.countSlotRangeTombstones(COLUMN, UInt64.ZERO, UInt64.valueOf(9)))
        .thenReturn(OptionalLong.of(3));

    // Too few slots to compact, but still reported
    compactor.onPruned(UInt64.valueOf(5));
    assertThat(gaugeValue(TOMBSTONES)).hasValue(42);

    compactor.onPruned(UInt64.valueOf(9));
    assertThat(gaugeValue(TOMBSTONES)).hasValue(3);
  }

  @Test
  void shouldCountTombstonesAfterCompacting() {
    compactor.onPruned(UInt64.valueOf(9));

    final InOrder inOrder = inOrder(dao);
    inOrder.verify(dao).compactSlotRange(COLUMN, UInt64.ZERO, UInt64.valueOf(9));
    inOrder.verify(dao).countSlotRangeTombstones(COLUMN, UInt64.ZERO, UInt64.valueOf(9));
  }

  @Test
  void shouldNotReportTombstonesWhenTheDatabaseCannotCountThem() {
    when(dao.countSlotRangeTombstones(any(), any(), any())).thenReturn(OptionalLong.empty());

    compactor.onPruned(UInt64.valueOf(9));

    assertThat(gaugeValue(TOMBSTONES)).isEmpty();
  }

  @Test
  void shouldNotFailThePruneWhenCountingTombstonesFails() {
    when(dao.countSlotRangeTombstones(any(), any(), any()))
        .thenThrow(new RuntimeException("count failed"));

    compactor.onPruned(UInt64.valueOf(9));
    compactor.onPruned(UInt64.valueOf(19));

    // The first compaction still counted, so the second starts after it
    verify(dao).compactSlotRange(COLUMN, UInt64.valueOf(10), UInt64.valueOf(19));
  }

  private static SlotRangeDataFile file(
      final long firstSlot, final long lastSlot, final long sizeBytes) {
    return new SlotRangeDataFile(UInt64.valueOf(firstSlot), UInt64.valueOf(lastSlot), sizeBytes);
  }

  private OptionalDouble gaugeValue(final String name) {
    return metricsSystem.getLabelledGauge(TekuMetricCategory.STORAGE, name).getValue(LABEL);
  }
}

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

import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotKeyedColumn;

class PrunedSlotRangeCompactorTest {

  private static final SlotKeyedColumn COLUMN = SlotKeyedColumn.DATA_COLUMN_SIDECARS;
  private static final String LABEL = "data_column_sidecars";
  private static final String COMPACTION_TIME = "pruned_slots_compaction_time";
  private static final String TOMBSTONES = "pruned_slots_tombstones";

  private final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1_000);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PrunedSlotRangeCompactor compactor =
      new PrunedSlotRangeCompactor(
          dao,
          COLUMN,
          10,
          timeProvider,
          SettableLabelledGauge.create(
              metricsSystem, TekuMetricCategory.STORAGE, COMPACTION_TIME, "help", "table"),
          SettableLabelledGauge.create(
              metricsSystem, TekuMetricCategory.STORAGE, TOMBSTONES, "help", "table"));

  @Test
  void shouldNotCompactUntilEnoughSlotsArePruned() {
    // Slots 0 to 8 are only 9 slots
    compactor.onPruned(UInt64.valueOf(8));

    verify(dao, never()).compactSlotRange(any(), any(), any());
    assertThat(gaugeValue(COMPACTION_TIME)).isEmpty();
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

  private OptionalDouble gaugeValue(final String name) {
    return metricsSystem.getLabelledGauge(TekuMetricCategory.STORAGE, name).getValue(LABEL);
  }
}

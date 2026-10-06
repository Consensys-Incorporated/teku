/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.server.pruner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.Database;

public class StatePrunerTest {

  public static final Duration PRUNE_INTERVAL = Duration.ofSeconds(26);
  private static final long SLOTS_RETAINED = 10;
  private static final long PRUNE_LIMIT = 10;
  public static final int SLOTS_PER_EPOCH = 10;
  private final Spec spec =
      TestSpecFactory.createDefault(
          builder -> builder.slotsPerEpoch(SLOTS_PER_EPOCH).minEpochsForBlockRequests(5));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final Database database = mock(Database.class);
  private final SettableLabelledGauge pruningActiveLabelledGauge =
      mock(SettableLabelledGauge.class);

  private final StatePruner pruner =
      new StatePruner(
          spec,
          database,
          asyncRunner,
          PRUNE_INTERVAL,
          SLOTS_RETAINED,
          PRUNE_LIMIT,
          "test",
          mock(SettableLabelledGauge.class),
          pruningActiveLabelledGauge,
          Duration.ofMinutes(5));

  @BeforeEach
  void setUp() {
    assertThat(pruner.start()).isCompleted();
    when(database.pruneFinalizedStates(any(), any(), anyInt()))
        .thenReturn(Optional.of(UInt64.ZERO));
  }

  @Test
  void shouldPruneWhenFirstStarted() {
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(UInt64.valueOf(50))));
    asyncRunner.executeDueActions();
    verify(database).pruneFinalizedStates(any(), any(), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge).set(eq(0.), any());
  }

  @Test
  void shouldPruneAfterInterval() {
    when(database.getFinalizedCheckpoint()).thenReturn(Optional.empty());
    asyncRunner.executeDueActions();
    verify(database, never()).pruneFinalizedStates(any(), any(), eq(PRUNE_LIMIT));

    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(UInt64.valueOf(52))));
    triggerNextPruning();
    verify(database).pruneFinalizedStates(any(), any(), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge, times(2)).set(eq(0.), any());
  }

  @Test
  void shouldNotPruneWhenFinalizedCheckpointNotSet() {
    when(database.getFinalizedCheckpoint()).thenReturn(Optional.empty());
    triggerNextPruning();
    verify(database, never()).pruneFinalizedStates(any(), any(), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge).set(eq(0.), any());
  }

  @Test
  void shouldNotPruneWhenFinalizedCheckpointBelowEpochsToKeep() {
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(1)));
    triggerNextPruning();
    verify(database, never()).pruneFinalizedStates(any(), any(), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge).set(eq(0.), any());
  }

  @Test
  void shouldPruneStatesMoreThanEpochsToKeepBeforeFinalizedCheckpoint() {
    final UInt64 finalizedEpoch = UInt64.valueOf(50);
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(finalizedEpoch)));
    triggerNextPruning();
    // SlotToKeep = FinalizedEpoch (50) * SlotsPerEpoch(10) - SlotsToKeep(10)
    // = 500 - 100 = 490, last slot to prune = 490 - 1 = 489.
    final UInt64 lastSlotToPrune = UInt64.valueOf(489);
    verify(database).pruneFinalizedStates(any(), eq(lastSlotToPrune), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge).set(eq(0.), any());
  }

  @Test
  void shouldPruneStatesWhenFirstEpochIsPrunable() {

    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(SLOTS_RETAINED + 1)));
    triggerNextPruning();
    // Should prune all states from slots that are past the last 10 finalized slots
    final UInt64 lastSlotToPrune = UInt64.valueOf(SLOTS_PER_EPOCH * SLOTS_RETAINED - 1);
    verify(database).pruneFinalizedStates(any(), eq(lastSlotToPrune), eq(PRUNE_LIMIT));
    verify(pruningActiveLabelledGauge).set(eq(0.), any());
  }

  private void triggerNextPruning() {
    timeProvider.advanceTimeBy(PRUNE_INTERVAL);
    asyncRunner.executeDueActions();
  }
}

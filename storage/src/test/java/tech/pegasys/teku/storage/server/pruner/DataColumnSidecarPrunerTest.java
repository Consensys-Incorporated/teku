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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.storage.server.Database;

public class DataColumnSidecarPrunerTest {
  public static final Duration PRUNE_INTERVAL = Duration.ofSeconds(5);
  public static final int PRUNE_LIMIT = 10;

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
  private final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

  private final UInt64 genesisTime = UInt64.valueOf(0);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final Database database = mock(Database.class);
  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final DataColumnSidecarPruner dataColumnSidecarPruner =
      new DataColumnSidecarPruner(
          spec,
          database,
          stubMetricsSystem,
          asyncRunner,
          asyncRunner,
          timeProvider,
          PRUNE_INTERVAL,
          PRUNE_LIMIT,
          false,
          "test",
          mock(SettableLabelledGauge.class),
          mock(SettableLabelledGauge.class),
          Duration.ofMinutes(5));

  @BeforeEach
  void setUp() {
    when(database.getGenesisTime()).thenReturn(Optional.of(genesisTime));
    assertThat(dataColumnSidecarPruner.start()).isCompleted();
  }

  @Test
  void shouldNotPruneWhenGenesisNotAvailable() {
    when(database.getGenesisTime()).thenReturn(Optional.empty());

    asyncRunner.executeDueActions();

    verify(database).getGenesisTime();
    verify(database, never()).pruneAllSidecars(any(), anyInt());
  }

  @Test
  void shouldNotArchiveSidecarsProofsWhenNoPrunableSlotReceived() {
    asyncRunner.executeDueActions();

    verify(database, never()).archiveSidecarsProofs(any(), any());
  }

  @Test
  void shouldArchiveMostRecentChunkOnFirstRun() {
    final UInt64 prunableSlot = UInt64.valueOf(20);
    when(database.getEarliestDataColumnSidecarSlot()).thenReturn(Optional.of(UInt64.ZERO));

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(prunableSlot);

    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarPruner.class, Level.DEBUG)) {
      asyncRunner.executeDueActions();

      // archives the most recent PRUNE_LIMIT slots: [prunableSlot - PRUNE_LIMIT, prunableSlot]
      assertThat(logCaptor.getDebugLogs())
          .anySatisfy(
              log ->
                  assertThat(log)
                      .contains("Archiving data column sidecars to proofs")
                      .contains("from slot " + (prunableSlot.longValue() - PRUNE_LIMIT))
                      .contains("up to slot " + prunableSlot));
    }

    verify(database)
        .archiveSidecarsProofs(
            UInt64.valueOf(prunableSlot.longValue() - PRUNE_LIMIT), prunableSlot);
  }

  @Test
  void shouldArchiveNextChunkBackwardsOnSubsequentRun() {
    final UInt64 prunableSlot = UInt64.valueOf(20);
    when(database.getEarliestDataColumnSidecarSlot()).thenReturn(Optional.of(UInt64.ZERO));

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(prunableSlot);
    asyncRunner.executeDueActions(); // run 1: archives [10, 20]
    asyncRunner.executeQueuedActions(); // run 2: archives [0, 9]

    verify(database).archiveSidecarsProofs(UInt64.valueOf(10), prunableSlot);
    verify(database).archiveSidecarsProofs(UInt64.ZERO, UInt64.valueOf(9));
  }

  @Test
  void shouldClampLowerBoundToEarliestDataColumnSidecarSlot() {
    final UInt64 prunableSlot = UInt64.valueOf(20);
    final UInt64 earliestSlot = UInt64.valueOf(15);
    when(database.getEarliestDataColumnSidecarSlot()).thenReturn(Optional.of(earliestSlot));

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(prunableSlot);
    asyncRunner.executeDueActions();

    // lower bound clamped to earliestSlot even though PRUNE_LIMIT would go further back
    verify(database).archiveSidecarsProofs(earliestSlot, prunableSlot);
  }

  @Test
  void shouldSkipArchivingWhenEarliestSlotIsAfterTillSlot() {
    // Common on checkpoint-synced nodes: prunable boundary is before the earliest stored sidecar.
    final UInt64 prunableSlot = UInt64.valueOf(100);
    final UInt64 earliestStoredSlot = UInt64.valueOf(500);
    when(database.getEarliestDataColumnSidecarSlot()).thenReturn(Optional.of(earliestStoredSlot));

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(prunableSlot);
    asyncRunner.executeDueActions();

    verify(database, never()).archiveSidecarsProofs(any(), any());
  }

  @Test
  void shouldPrioritiseNewDeltaAfterTillSlotBump() {
    final UInt64 initialPrunableSlot = UInt64.valueOf(30);
    final UInt64 bumpedPrunableSlot = UInt64.valueOf(50);
    when(database.getEarliestDataColumnSidecarSlot()).thenReturn(Optional.of(UInt64.ZERO));

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(initialPrunableSlot);
    asyncRunner.executeDueActions(); // run 1: archives [20, 30]
    asyncRunner.executeQueuedActions(); // run 2: archives [9, 19]
    asyncRunner.executeQueuedActions(); // run 3: archives [0, 8]

    dataColumnSidecarPruner.onSidecarArchivePrunableSlot(bumpedPrunableSlot);

    asyncRunner.executeQueuedActions(); // run 4: prioritises new delta [40, 50]
    asyncRunner.executeQueuedActions(); // run 5: fills gap between old and new [31, 39]

    verify(database).archiveSidecarsProofs(UInt64.valueOf(20), UInt64.valueOf(30));
    verify(database).archiveSidecarsProofs(UInt64.valueOf(9), UInt64.valueOf(19));
    verify(database).archiveSidecarsProofs(UInt64.ZERO, UInt64.valueOf(8));
    verify(database).archiveSidecarsProofs(UInt64.valueOf(40), UInt64.valueOf(50));
    verify(database).archiveSidecarsProofs(UInt64.valueOf(31), UInt64.valueOf(39));
  }

  @Test
  void checkCalculatedMinSlotMatchesDALastSlotOfMinEpoch() {
    final SpecConfig config = spec.forMilestone(SpecMilestone.FULU).getConfig();
    final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(config);
    final UInt64 minEpochsForDataColumnSidecarsRequests =
        UInt64.valueOf(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests() + 1);

    final UInt64 currentSlot =
        UInt64.valueOf(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests())
            .times(slotsPerEpoch)
            .plus(slotsPerEpoch * 10L);
    final UInt64 currentTime = currentSlot.times(secondsPerSlot);

    final UInt64 currentEpoch = currentSlot.dividedBy(UInt64.valueOf(slotsPerEpoch));
    final UInt64 earliestEpochToKeep = currentEpoch.minus(minEpochsForDataColumnSidecarsRequests);
    final UInt64 earliestSlotToKeep =
        spec.computeStartSlotAtEpoch(earliestEpochToKeep).minusMinZero(1);
    timeProvider.advanceTimeBy(Duration.ofSeconds(currentTime.longValue()));

    asyncRunner.executeDueActions();
    verify(database).pruneAllSidecars(eq(earliestSlotToKeep), eq(PRUNE_LIMIT));
  }
}

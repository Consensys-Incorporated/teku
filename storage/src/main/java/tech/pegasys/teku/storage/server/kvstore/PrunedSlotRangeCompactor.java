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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotKeyedColumn;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotRangeDataFile;

/**
 * Compacts the slots pruned from a slot-keyed column once enough of them have accumulated.
 *
 * <p>Pruning deletes keys one at a time, and each delete leaves a tombstone until compaction drops
 * it. Background compaction can leave a pruned range full of tombstones for a long time, and a seek
 * that lands in it has to step over every one of them before it reaches a live key. Compacting the
 * pruned range removes them for every reader. Compacting after every prune run would rewrite the
 * files at the edge of the range each time, so this waits until {@code minSlotsPerCompaction} slots
 * have been pruned since the last compaction.
 *
 * <p>The first compaction starts from slot 0, so it also clears tombstones left by pruning before
 * this node started. Slots that no data file holds are skipped without compacting them, and each
 * compaction stops before the data files it covers add up to more than {@code
 * maxBytesPerCompaction}, so a large backlog is worked through a piece at a time. Pruning often
 * finds nothing new to delete, so those runs carry on with the backlog too.
 *
 * <p>After each prune it reports how many tombstones are left in the pruned slots, where the
 * database can count them, so their build-up and removal can be watched.
 */
class PrunedSlotRangeCompactor {
  private static final Logger LOG = LogManager.getLogger();

  private final KvStoreCombinedDao dao;
  private final SlotKeyedColumn column;
  private final int minSlotsPerCompaction;
  private final long maxBytesPerCompaction;
  private final TimeProvider timeProvider;
  private final SettableLabelledGauge compactionTimeGauge;
  private final SettableLabelledGauge tombstonesGauge;
  private final String metricLabel;

  // Guarded by this
  private UInt64 nextSlotToCompact = UInt64.ZERO;
  // Guarded by this
  private Optional<UInt64> highestPrunedSlot = Optional.empty();

  PrunedSlotRangeCompactor(
      final KvStoreCombinedDao dao,
      final SlotKeyedColumn column,
      final int minSlotsPerCompaction,
      final long maxBytesPerCompaction,
      final TimeProvider timeProvider,
      final SettableLabelledGauge compactionTimeGauge,
      final SettableLabelledGauge tombstonesGauge) {
    this.dao = dao;
    this.column = column;
    this.minSlotsPerCompaction = minSlotsPerCompaction;
    this.maxBytesPerCompaction = maxBytesPerCompaction;
    this.timeProvider = timeProvider;
    this.compactionTimeGauge = compactionTimeGauge;
    this.tombstonesGauge = tombstonesGauge;
    this.metricLabel = metricLabel(column);
  }

  static String metricLabel(final SlotKeyedColumn column) {
    return column.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Records that every key of the column at or below {@code lastPrunedSlot} has been deleted,
   * compacts the slots pruned since the last compaction once there are enough of them, and updates
   * the count of tombstones left in the pruned slots. Blocking: call it from the pruner, after the
   * deletions have been committed.
   */
  synchronized void onPruned(final UInt64 lastPrunedSlot) {
    highestPrunedSlot =
        Optional.of(highestPrunedSlot.map(lastPrunedSlot::max).orElse(lastPrunedSlot));
    compactIfEnoughSlotsPruned(lastPrunedSlot);
    updateTombstoneCount(lastPrunedSlot);
  }

  /**
   * Records that a prune found nothing to delete. The slots pruned earlier are then compacted if
   * enough of them are still waiting, which they are while a backlog is being worked through. Does
   * nothing until something has been pruned since startup. Blocking: call it from the pruner.
   */
  synchronized void onNothingPruned() {
    highestPrunedSlot.ifPresent(
        lastPrunedSlot -> {
          final UInt64 previousNextSlotToCompact = nextSlotToCompact;
          compactIfEnoughSlotsPruned(lastPrunedSlot);
          if (!nextSlotToCompact.equals(previousNextSlotToCompact)) {
            updateTombstoneCount(lastPrunedSlot);
          }
        });
  }

  private void compactIfEnoughSlotsPruned(final UInt64 lastPrunedSlot) {
    if (lastPrunedSlot.isLessThan(nextSlotToCompact)
        || lastPrunedSlot.minus(nextSlotToCompact).plus(1).isLessThan(minSlotsPerCompaction)) {
      return;
    }
    final Optional<List<SlotRangeDataFile>> maybeFiles =
        dao.getSlotRangeDataFiles(column, nextSlotToCompact, lastPrunedSlot);
    if (maybeFiles.isEmpty()) {
      // The database cannot list its data files, so there is nothing to size the work by
      compact(nextSlotToCompact, lastPrunedSlot);
      return;
    }
    final List<SlotRangeDataFile> files = maybeFiles.get();
    if (files.isEmpty()) {
      LOG.debug(
          "No data files hold pruned {} slots {} to {}, nothing to compact",
          column,
          nextSlotToCompact,
          lastPrunedSlot);
      nextSlotToCompact = lastPrunedSlot.plus(1);
      return;
    }
    // Slots before the first data file are not held anywhere, so skip them
    final UInt64 startSlot =
        files.stream()
            .map(SlotRangeDataFile::firstSlot)
            .min(Comparator.naturalOrder())
            .orElseThrow()
            .max(nextSlotToCompact);
    compact(startSlot, getLastSlotWithinBudget(files, startSlot, lastPrunedSlot));
  }

  /**
   * Compacting a range reads and rewrites every data file overlapping it, so the range ends just
   * before the first data file that would take the total over the budget. The files holding the
   * start slot are always included, so every compaction makes progress.
   */
  private UInt64 getLastSlotWithinBudget(
      final List<SlotRangeDataFile> files, final UInt64 startSlot, final UInt64 lastPrunedSlot) {
    long bytes = 0;
    for (final SlotRangeDataFile file :
        files.stream().sorted(Comparator.comparing(SlotRangeDataFile::firstSlot)).toList()) {
      if (file.firstSlot().isGreaterThan(startSlot)
          && bytes + file.sizeBytes() > maxBytesPerCompaction) {
        return file.firstSlot().minus(1);
      }
      bytes += file.sizeBytes();
    }
    return lastPrunedSlot;
  }

  private void compact(final UInt64 startSlot, final UInt64 endSlot) {
    final UInt64 startTime = timeProvider.getTimeInMillis();
    dao.compactSlotRange(column, startSlot, endSlot);
    final UInt64 duration = timeProvider.getTimeInMillis().minusMinZero(startTime);
    compactionTimeGauge.set(duration.longValue(), metricLabel);
    LOG.debug("Compacted pruned {} slots {} to {} in {} ms", column, startSlot, endSlot, duration);
    nextSlotToCompact = endSlot.plus(1);
  }

  private void updateTombstoneCount(final UInt64 lastPrunedSlot) {
    // Only a metric, so a failure to count must not fail the prune
    try {
      final OptionalLong tombstones =
          dao.countSlotRangeTombstones(column, UInt64.ZERO, lastPrunedSlot);
      tombstones.ifPresent(count -> tombstonesGauge.set(count, metricLabel));
    } catch (final RuntimeException e) {
      LOG.debug("Failed to count tombstones in pruned {} slots", column, e);
    }
  }
}

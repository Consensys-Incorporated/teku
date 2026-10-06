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

package tech.pegasys.teku.statetransition.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

/**
 * Holds blocks with slots that are in the future relative to our node's current slot.
 *
 * <p>Future blocks are queued before their signature is verified, so the queue is limited both by
 * the number of blocks per slot and by the total SSZ size of all queued blocks. When the size limit
 * is reached, blocks from the furthest future slot are evicted first, since blocks closest to the
 * current slot are the ones about to be imported.
 */
public class FutureBlockPool {
  private static final Logger LOG = LogManager.getLogger();
  public static final UInt64 FUTURE_SLOT_TOLERANCE = UInt64.valueOf(2);
  private static final String METRIC_TYPE = "blocks";
  private static final String RESULT_LABEL_QUEUED = "queued";
  private static final String RESULT_LABEL_DEQUEUED = "dequeued";
  private static final String RESULT_LABEL_EVICTED = "evicted";
  private static final String RESULT_LABEL_DROPPED = "dropped";

  private final int maxBlocksPerSlot;
  private final long maxTotalBytes;
  private final ToLongFunction<SignedBeaconBlock> blockSizeFunction;
  private final SettableLabelledGauge futureItemsCounter;
  private final Counter queuedCounter;
  private final Counter dequeuedCounter;
  private final Counter evictedCounter;
  private final Counter droppedCounter;

  // per slot, blocks in insertion order mapped to their size
  private final NavigableMap<UInt64, LinkedHashMap<SignedBeaconBlock, Long>> queuedBlocks =
      new TreeMap<>();
  private UInt64 currentSlot = UInt64.ZERO;
  private int size = 0;
  private long totalBytes = 0;

  public FutureBlockPool(
      final int maxBlocksPerSlot,
      final long maxTotalBytes,
      final ToLongFunction<SignedBeaconBlock> blockSizeFunction,
      final SettableLabelledGauge futureItemsCounter,
      final LabelledMetric<Counter> resultCounter) {
    if (maxBlocksPerSlot <= 0) {
      throw new IllegalArgumentException("Max future blocks per slot must be positive");
    }
    if (maxTotalBytes < 0) {
      throw new IllegalArgumentException("Max total future blocks bytes must not be negative");
    }
    this.maxBlocksPerSlot = maxBlocksPerSlot;
    this.maxTotalBytes = maxTotalBytes;
    this.blockSizeFunction = blockSizeFunction;
    this.futureItemsCounter = futureItemsCounter;
    this.queuedCounter = resultCounter.labels(RESULT_LABEL_QUEUED);
    this.dequeuedCounter = resultCounter.labels(RESULT_LABEL_DEQUEUED);
    this.evictedCounter = resultCounter.labels(RESULT_LABEL_EVICTED);
    this.droppedCounter = resultCounter.labels(RESULT_LABEL_DROPPED);
  }

  /** Creates the counter of future blocks by what happened to them, labelled by result. */
  public static LabelledMetric<Counter> createResultCounter(final MetricsSystem metricsSystem) {
    return metricsSystem.createLabelledCounter(
        TekuMetricCategory.BEACON,
        "future_blocks_total",
        "Total number of blocks for future slots that were queued, dequeued for import, evicted"
            + " or dropped, labelled by result",
        "result");
  }

  public synchronized void onSlot(final UInt64 slot) {
    currentSlot = slot;
  }

  /**
   * Add a block to the future blocks queue.
   *
   * @param block the block to add
   * @return true if the block was accepted for future processing, even if it was already queued
   */
  public synchronized boolean add(final SignedBeaconBlock block) {
    final UInt64 slot = block.getSlot();
    if (slot.isGreaterThan(currentSlot.plus(FUTURE_SLOT_TOLERANCE))) {
      // Block is too far in the future
      droppedCounter.inc();
      return false;
    }

    final LinkedHashMap<SignedBeaconBlock, Long> blocksAtSlot = queuedBlocks.get(slot);
    if (blocksAtSlot != null && blocksAtSlot.containsKey(block)) {
      return true;
    }

    final long blockSize = blockSizeFunction.applyAsLong(block);
    if (blockSize > maxTotalBytes) {
      LOG.trace(
          "Dropping future block at slot {} because its size {} exceeds the limit {}",
          slot,
          blockSize,
          maxTotalBytes);
      droppedCounter.inc();
      return false;
    }

    // a full slot always gives up its oldest block, so count that block's size as freed capacity
    final boolean slotIsFull = blocksAtSlot != null && blocksAtSlot.size() >= maxBlocksPerSlot;
    final long slotEvictionBytes = slotIsFull ? blocksAtSlot.values().iterator().next() : 0;

    // make room by evicting blocks from slots further in the future than this block, but only if
    // that frees enough capacity, so that nothing is evicted for a block that is then dropped
    if (maxTotalBytes - totalBytes + slotEvictionBytes < blockSize) {
      final long evictableBytes =
          queuedBlocks.tailMap(slot, false).values().stream()
              .flatMap(blocks -> blocks.values().stream())
              .mapToLong(Long::longValue)
              .sum();
      if (maxTotalBytes - totalBytes + slotEvictionBytes + evictableBytes < blockSize) {
        LOG.trace("Dropping future block at slot {} because no capacity is available", slot);
        droppedCounter.inc();
        return false;
      }
    }

    if (slotIsFull) {
      removeOldestBlockAtSlot(slot);
    }
    while (maxTotalBytes - totalBytes < blockSize) {
      removeOldestBlockAtSlot(queuedBlocks.lastKey());
    }
    LOG.trace("Save future block at slot {} for later import: {}", slot, block);
    queuedBlocks.computeIfAbsent(slot, __ -> new LinkedHashMap<>()).put(block, blockSize);
    size++;
    totalBytes += blockSize;
    queuedCounter.inc();
    futureItemsCounter.set(size, METRIC_TYPE);
    return true;
  }

  /**
   * Removes all blocks that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The blocks that are no longer in the future
   */
  public synchronized List<SignedBeaconBlock> prune(final UInt64 currentSlot) {
    final List<SignedBeaconBlock> dequeued = new ArrayList<>();
    final Iterator<LinkedHashMap<SignedBeaconBlock, Long>> iterator =
        queuedBlocks.headMap(currentSlot, true).values().iterator();
    while (iterator.hasNext()) {
      final LinkedHashMap<SignedBeaconBlock, Long> blocks = iterator.next();
      blocks.forEach(
          (block, blockSize) -> {
            dequeued.add(block);
            totalBytes -= blockSize;
          });
      size -= blocks.size();
      iterator.remove();
    }
    dequeuedCounter.inc(dequeued.size());
    futureItemsCounter.set(size, METRIC_TYPE);
    return dequeued;
  }

  public synchronized boolean contains(final SignedBeaconBlock block) {
    final Map<SignedBeaconBlock, Long> blocks = queuedBlocks.get(block.getSlot());
    return blocks != null && blocks.containsKey(block);
  }

  public synchronized int size() {
    return size;
  }

  synchronized long getTotalBytes() {
    return totalBytes;
  }

  @VisibleForTesting
  public int getMaxBlocksPerSlot() {
    return maxBlocksPerSlot;
  }

  @VisibleForTesting
  public long getMaxTotalBytes() {
    return maxTotalBytes;
  }

  @VisibleForTesting
  public ToLongFunction<SignedBeaconBlock> getBlockSizeFunction() {
    return blockSizeFunction;
  }

  private void removeOldestBlockAtSlot(final UInt64 slot) {
    final LinkedHashMap<SignedBeaconBlock, Long> blocks = queuedBlocks.get(slot);
    final Iterator<Map.Entry<SignedBeaconBlock, Long>> iterator = blocks.entrySet().iterator();
    final Map.Entry<SignedBeaconBlock, Long> oldest = iterator.next();
    iterator.remove();
    size--;
    totalBytes -= oldest.getValue();
    evictedCounter.inc();
    if (blocks.isEmpty()) {
      queuedBlocks.remove(slot);
    }
    LOG.trace("Evicted future block at slot {}: {}", slot, oldest.getKey());
  }
}

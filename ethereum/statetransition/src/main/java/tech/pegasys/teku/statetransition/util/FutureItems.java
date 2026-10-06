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

package tech.pegasys.teku.statetransition.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Holds items with slots that are in the future relative to our node's current slot */
public class FutureItems<T> implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  public static final UInt64 DEFAULT_FUTURE_SLOT_TOLERANCE = UInt64.valueOf(2);
  private static final int MAX_ITEMS_PER_SLOT = 500;

  private final SettableLabelledGauge futureItemsCounter;
  private final UInt64 futureSlotTolerance;
  private final Function<T, UInt64> slotFunction;

  private final NavigableMap<UInt64, Set<T>> queuedFutureItems = new ConcurrentSkipListMap<>();
  private final String type;
  private volatile UInt64 currentSlot = UInt64.ZERO;

  private FutureItems(
      final Function<T, UInt64> slotFunction,
      final UInt64 futureSlotTolerance,
      final SettableLabelledGauge futureItemsCounter,
      final String type) {
    this.slotFunction = slotFunction;
    this.futureSlotTolerance = futureSlotTolerance;
    this.futureItemsCounter = futureItemsCounter;
    this.type = type;
  }

  public static <T> FutureItems<T> create(
      final Function<T, UInt64> slotFunction,
      final SettableLabelledGauge futureItemsCounter,
      final String type) {
    return new FutureItems<T>(
        slotFunction, DEFAULT_FUTURE_SLOT_TOLERANCE, futureItemsCounter, type);
  }

  public static <T> FutureItems<T> create(
      final Function<T, UInt64> slotFunction,
      final UInt64 futureSlotTolerance,
      final SettableLabelledGauge futureItemsCounter,
      final String type) {
    return new FutureItems<T>(slotFunction, futureSlotTolerance, futureItemsCounter, type);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
  }

  /**
   * Add an item to the future items set.
   *
   * @param item the item to add
   * @return true if the item was accepted for future processing, even if it was already queued
   */
  public boolean add(final T item) {
    final UInt64 slot = slotFunction.apply(item);
    if (slot.isGreaterThan(currentSlot.plus(futureSlotTolerance))) {
      // Item is too far in the future
      return false;
    }

    LOG.trace("Save future item at slot {} for later import: {}", slot, item);
    queuedFutureItems.computeIfAbsent(slot, key -> createNewSet()).add(item);
    futureItemsCounter.set(size(), type);
    return true;
  }

  /**
   * Removes all items that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The set of items that are no longer in the future
   */
  public List<T> prune(final UInt64 currentSlot) {
    final List<T> dequeued = new ArrayList<>();
    queuedFutureItems
        .headMap(currentSlot, true)
        .keySet()
        .forEach(key -> dequeued.addAll(queuedFutureItems.remove(key)));
    futureItemsCounter.set(size(), type);
    return dequeued;
  }

  public boolean contains(final T item) {
    return queuedFutureItems
        .getOrDefault(slotFunction.apply(item), Collections.emptySet())
        .contains(item);
  }

  public int size() {
    return queuedFutureItems.values().stream().map(Set::size).reduce(Integer::sum).orElse(0);
  }

  private Set<T> createNewSet() {
    return LimitedSet.createSynchronizedLRU(MAX_ITEMS_PER_SLOT);
  }
}

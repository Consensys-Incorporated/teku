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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class PendingPool<T> extends AbstractIgnoringFutureHistoricalSlot {
  private static final Logger LOG = LogManager.getLogger();

  private static final Comparator<SlotAndRoot> SLOT_AND_ROOT_COMPARATOR =
      Comparator.comparing(SlotAndRoot::getSlot).thenComparing(SlotAndRoot::getRoot);

  private final String itemType;
  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Map<Bytes32, T> pendingItems = new HashMap<>();
  private final NavigableSet<SlotAndRoot> orderedPendingItems =
      new TreeSet<>(SLOT_AND_ROOT_COMPARATOR);
  private final Map<Bytes32, Set<Bytes32>> pendingItemsByRequiredBlockRoot = new HashMap<>();
  private final Map<Bytes32, Long> pendingItemWeights = new HashMap<>();
  private final int maxItems;
  private final long maxTotalWeight;
  private long totalWeight = 0;

  private final Function<T, Bytes32> hashTreeRootFunction;
  private final Function<T, Collection<Bytes32>> requiredBlockRootsFunction;
  private final Function<T, UInt64> targetSlotFunction;
  private final ToLongFunction<T> itemWeightFunction;
  private final SettableLabelledGauge sizeGauge;
  private final Optional<SettableLabelledGauge> totalWeightGauge;

  PendingPool(
      final SettableLabelledGauge sizeGauge,
      final String itemType,
      final Spec spec,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxItems,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> requiredBlockRootsFunction,
      final Function<T, UInt64> targetSlotFunction) {
    this(
        sizeGauge,
        itemType,
        spec,
        historicalSlotTolerance,
        futureSlotTolerance,
        maxItems,
        Long.MAX_VALUE,
        __ -> 1L,
        Optional.empty(),
        hashTreeRootFunction,
        requiredBlockRootsFunction,
        targetSlotFunction);
  }

  PendingPool(
      final SettableLabelledGauge sizeGauge,
      final String itemType,
      final Spec spec,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxItems,
      final long maxTotalWeight,
      final ToLongFunction<T> itemWeightFunction,
      final Optional<SettableLabelledGauge> totalWeightGauge,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> requiredBlockRootsFunction,
      final Function<T, UInt64> targetSlotFunction) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    if (maxTotalWeight < 0) {
      throw new IllegalArgumentException("Max total pending pool weight must not be negative");
    }
    this.itemType = itemType;
    this.maxItems = maxItems;
    this.maxTotalWeight = maxTotalWeight;
    this.itemWeightFunction = itemWeightFunction;
    this.hashTreeRootFunction = hashTreeRootFunction;
    this.requiredBlockRootsFunction = requiredBlockRootsFunction;
    this.targetSlotFunction = targetSlotFunction;
    this.sizeGauge = sizeGauge;
    this.totalWeightGauge = totalWeightGauge;
    initMetricsLabel(); // Init the label so it appears in metrics immediately
  }

  public void initMetricsLabel() {
    sizeGauge.set(0, itemType);
    totalWeightGauge.ifPresent(gauge -> gauge.set(0, itemType));
  }

  public synchronized void add(final T item) {
    final UInt64 slot = targetSlotFunction.apply(item);
    if (shouldIgnoreItemAtSlot(slot)) {
      // Ignore items outside of the range we care about
      return;
    }

    if (maxItems <= 0) {
      return;
    }

    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    if (pendingItems.containsKey(itemRoot)) {
      return;
    }

    final long itemWeight = getItemWeight(item);
    if (itemWeight > maxTotalWeight) {
      LOG.trace(
          "Dropping unattached item at slot {} because its weight {} exceeds the pending pool limit {}",
          slot,
          itemWeight,
          maxTotalWeight);
      return;
    }

    while (shouldRemoveOldestItemBeforeAdding(itemWeight)) {
      if (!removeOldestItem()) {
        break;
      }
    }

    if (shouldRemoveOldestItemBeforeAdding(itemWeight)) {
      LOG.trace(
          "Dropping unattached item at slot {} because no pending pool capacity is available",
          slot);
      return;
    }

    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(item);
    final ArrayList<Bytes32> newRequiredRoots = new ArrayList<>();

    requiredRoots.forEach(
        requiredRoot ->
            // Index item by required roots
            pendingItemsByRequiredBlockRoot
                .computeIfAbsent(
                    requiredRoot,
                    (key) -> {
                      final Set<Bytes32> dependants = new HashSet<>();
                      newRequiredRoots.add(requiredRoot);
                      return dependants;
                    })
                .add(itemRoot));

    pendingItems.put(itemRoot, item);
    pendingItemWeights.put(itemRoot, itemWeight);
    totalWeight += itemWeight;
    updateTotalWeightGauge();
    orderedPendingItems.add(toSlotAndRoot(item));
    LOG.trace("Save unattached item at slot {} for future import: {}", slot, item);
    sizeGauge.set(pendingItems.size(), itemType);

    newRequiredRoots.forEach(
        requiredRoot ->
            requiredBlockRootSubscribers.forEach(s -> s.onRequiredBlockRoot(requiredRoot)));
  }

  public synchronized void remove(final T item) {
    if (item == null) {
      return;
    }

    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    final T removedItem = pendingItems.remove(itemRoot);
    if (removedItem == null) {
      return;
    }

    final SlotAndRoot itemSlotAndRoot = toSlotAndRoot(removedItem);
    orderedPendingItems.remove(itemSlotAndRoot);
    final Long removedWeight = pendingItemWeights.remove(itemRoot);
    if (removedWeight != null) {
      totalWeight -= removedWeight;
      updateTotalWeightGauge();
    }

    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(removedItem);
    requiredRoots.forEach(
        requiredRoot -> {
          Set<Bytes32> childSet = pendingItemsByRequiredBlockRoot.get(requiredRoot);
          if (childSet == null) {
            return;
          }
          childSet.remove(itemSlotAndRoot.getRoot());
          if (pendingItemsByRequiredBlockRoot.remove(requiredRoot, Collections.emptySet())) {
            requiredBlockRootDroppedSubscribers.forEach(
                s -> s.onRequiredBlockRootDropped(requiredRoot));
          }
        });
    sizeGauge.set(pendingItems.size(), itemType);
  }

  public synchronized List<T> removeItemsMatching(final Predicate<T> predicate) {
    final List<T> itemsToRemove = pendingItems.values().stream().filter(predicate).toList();
    itemsToRemove.forEach(this::remove);
    return itemsToRemove;
  }

  // Remove only direct dependents. Indirect dependents must wait until the intermediate pending
  // item they reference is successfully processed.
  public synchronized List<T> removeItemsDependingOn(final Bytes32 blockRoot) {
    final List<T> itemsToRemove = getItemsDependingOn(blockRoot, false);
    itemsToRemove.forEach(this::remove);
    return itemsToRemove;
  }

  @VisibleForTesting
  synchronized long getTotalWeight() {
    return totalWeight;
  }

  @VisibleForTesting
  long getMaxTotalWeight() {
    return maxTotalWeight;
  }

  public synchronized int size() {
    return pendingItems.size();
  }

  public boolean contains(final T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    return contains(itemRoot);
  }

  public synchronized boolean contains(final Bytes32 itemRoot) {
    return pendingItems.containsKey(itemRoot);
  }

  public synchronized Optional<T> get(final Bytes32 itemRoot) {
    return Optional.ofNullable(pendingItems.get(itemRoot));
  }

  public synchronized Set<Bytes32> getAllRequiredBlockRoots() {
    return pendingItemsByRequiredBlockRoot.keySet().stream()
        // Filter out items we already have but can't import yet
        .filter(root -> !pendingItems.containsKey(root))
        .collect(Collectors.toSet());
  }

  /**
   * Returns any items that are dependent on the given block root
   *
   * @param blockRoot The block root that some pending items may depend on.
   * @param includeIndirectDependents Whether to include items that depend indirectly on the given
   *     root. For example, if item B depends on item A which in turn depends on {@code blockRoot},
   *     both items A and B are returned if {@code includeIndirectDependents} is {@code true}. If
   *     {@code includeIndirectDependents} is {@code false}, only item A is returned.
   * @return The list of items which depend on the given block root.
   */
  public List<T> getItemsDependingOn(
      final Bytes32 blockRoot, final boolean includeIndirectDependents) {
    if (includeIndirectDependents) {
      return getAllItemsDependingOn(blockRoot);
    } else {
      return getItemsDirectlyDependingOn(blockRoot);
    }
  }

  /**
   * Returns any items that are directly dependent on the given block root
   *
   * @param blockRoot The block root that some pending items may depend on
   * @return A list of items that depend on this block root.
   */
  private synchronized List<T> getItemsDirectlyDependingOn(final Bytes32 blockRoot) {
    final Set<Bytes32> dependentRoots = pendingItemsByRequiredBlockRoot.get(blockRoot);
    if (dependentRoots == null) {
      return Collections.emptyList();
    }

    return dependentRoots.stream().map(pendingItems::get).filter(Objects::nonNull).toList();
  }

  /**
   * Returns all items that directly or indirectly depend on the given block root. In other words,
   * if item B depends on item A which in turn depends on {@code blockRoot}, both items A and B are
   * returned.
   *
   * @param blockRoot The block root that some pending items may depend on.
   * @return A list of items that either directly or indirectly depend on the given block root.
   */
  private synchronized List<T> getAllItemsDependingOn(final Bytes32 blockRoot) {
    final Set<Bytes32> dependentRoots = new HashSet<>();

    Set<Bytes32> requiredRoots = Set.of(blockRoot);
    while (!requiredRoots.isEmpty()) {
      final Set<Bytes32> roots =
          requiredRoots.stream()
              .map(pendingItemsByRequiredBlockRoot::get)
              .filter(Objects::nonNull)
              .flatMap(Set::stream)
              .collect(Collectors.toSet());

      dependentRoots.addAll(roots);
      requiredRoots = roots;
    }

    return dependentRoots.stream().map(pendingItems::get).filter(Objects::nonNull).toList();
  }

  public void subscribeRequiredBlockRoot(final RequiredBlockRootSubscriber subscriber) {
    requiredBlockRootSubscribers.subscribe(subscriber);
  }

  public void subscribeRequiredBlockRootDropped(
      final RequiredBlockRootDroppedSubscriber subscriber) {
    requiredBlockRootDroppedSubscribers.subscribe(subscriber);
  }

  @VisibleForTesting
  @Override
  protected synchronized void prune(final UInt64 slotLimit) {
    final List<T> toRemove = new ArrayList<>();
    for (SlotAndRoot slotAndRoot : orderedPendingItems) {
      if (slotAndRoot.getSlot().isGreaterThan(slotLimit)) {
        break;
      }
      toRemove.add(pendingItems.get(slotAndRoot.getRoot()));
    }

    toRemove.forEach(this::remove);
  }

  private SlotAndRoot toSlotAndRoot(final T item) {
    final UInt64 slot = targetSlotFunction.apply(item);
    final Bytes32 root = hashTreeRootFunction.apply(item);
    return new SlotAndRoot(slot, root);
  }

  private void updateTotalWeightGauge() {
    totalWeightGauge.ifPresent(gauge -> gauge.set(totalWeight, itemType));
  }

  private long getItemWeight(final T item) {
    final long itemWeight = itemWeightFunction.applyAsLong(item);
    if (itemWeight < 0) {
      throw new IllegalArgumentException("Pending pool item weight must not be negative");
    }
    return itemWeight;
  }

  private boolean shouldRemoveOldestItemBeforeAdding(final long itemWeight) {
    return pendingItems.size() > (maxItems - 1) || maxTotalWeight - totalWeight < itemWeight;
  }

  private boolean removeOldestItem() {
    final SlotAndRoot toRemove = orderedPendingItems.pollFirst();
    if (toRemove == null) {
      return false;
    }
    remove(pendingItems.get(toRemove.getRoot()));
    return true;
  }

  public interface RequiredBlockRootSubscriber {
    void onRequiredBlockRoot(Bytes32 blockRoot);
  }

  public interface RequiredBlockRootDroppedSubscriber {
    void onRequiredBlockRootDropped(Bytes32 blockRoot);
  }

  private static class SlotAndRoot {
    private final UInt64 slot;
    private final Bytes32 root;

    private SlotAndRoot(final UInt64 slot, final Bytes32 root) {
      this.slot = slot;
      this.root = root;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public Bytes32 getRoot() {
      return root;
    }
  }
}

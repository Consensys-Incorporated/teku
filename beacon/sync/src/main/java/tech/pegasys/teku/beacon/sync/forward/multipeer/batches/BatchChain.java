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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Tracks a set of batches which form a contiguous chain. */
public class BatchChain implements Iterable<Batch> {

  private final NavigableSet<Batch> batches =
      new TreeSet<>(
          Comparator.comparing(Batch::getFirstSlot)
              .thenComparing((o1, o2) -> Objects.equals(o1, o2) ? 0 : 1));

  public NavigableSet<Batch> batchesBeforeInclusive(final Batch batch) {
    return batches.headSet(batch, true);
  }

  public NavigableSet<Batch> batchesBeforeExclusive(final Batch batch) {
    return batches.headSet(batch, false);
  }

  public void add(final Batch batch) {
    checkNotNull(batch, "Cannot add null batch to BatchChain");
    batches.add(batch);
  }

  public NavigableSet<Batch> batchesAfterInclusive(final Batch batch) {
    return batches.tailSet(batch, true);
  }

  public NavigableSet<Batch> batchesAfterExclusive(final Batch batch) {
    return batches.tailSet(batch, false);
  }

  public NavigableSet<Batch> batchesBetweenInclusive(final Batch from, final Batch to) {
    return batches.subSet(from, true, to, true);
  }

  public NavigableSet<Batch> batchesBetweenExclusive(final Batch from, final Batch to) {
    return batches.subSet(from, false, to, false);
  }

  public NavigableSet<Batch> batchesBetweenExclusiveStart(final Batch from, final Batch to) {
    return batches.subSet(from, false, to, true);
  }

  public Optional<Batch> previousNonEmptyBatch(final Batch batch) {
    return batchesBeforeExclusive(batch).descendingSet().stream()
        .filter(previousBatch -> !previousBatch.isEmpty())
        .findFirst();
  }

  public Optional<Batch> nextNonEmptyBatch(final Batch batch) {
    return batchesAfterExclusive(batch).stream()
        .filter(followingBatch -> !followingBatch.isEmpty())
        .findFirst();
  }

  public Optional<Batch> firstImportableBatch() {
    for (Batch batch : batches) {
      if (batch.isEmpty()) {
        continue;
      }
      if (!batch.isConfirmed()) {
        return Optional.empty();
      }
      return Optional.of(batch);
    }
    return Optional.empty();
  }

  public void removeUpToIncluding(final Batch batch) {
    batchesBeforeInclusive(batch).clear();
  }

  public void removeAll() {
    batches.clear();
  }

  public void replace(final Batch batchToReplace, final Batch batch) {
    batches.remove(batchToReplace);
    batches.add(batch);
  }

  public Optional<Batch> last() {
    return batches.isEmpty() ? Optional.empty() : Optional.of(batches.last());
  }

  public boolean contains(final Batch batch) {
    return batches.contains(batch);
  }

  public boolean isEmpty() {
    return batches.isEmpty();
  }

  @Override
  public Iterator<Batch> iterator() {
    return batches.iterator();
  }

  @Override
  public void forEach(final Consumer<? super Batch> action) {
    batches.forEach(action);
  }

  @Override
  public Spliterator<Batch> spliterator() {
    return batches.spliterator();
  }

  public Stream<Batch> stream() {
    return batches.stream();
  }
}

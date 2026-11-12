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

package tech.pegasys.teku.infrastructure.ssz.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tech.pegasys.teku.infrastructure.ssz.InvalidValueSchemaException;
import tech.pegasys.teku.infrastructure.ssz.SszComposite;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableComposite;
import tech.pegasys.teku.infrastructure.ssz.SszMutableData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefComposite;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;

/**
 * Base backing {@link SszMutableData} class for mutable composite ssz structures (lists, vectors,
 * containers)
 *
 * <p>It has corresponding backing immutable {@link SszData} and the set of changed children. When
 * the {@link #commitChanges()} is called a new immutable {@link SszData} instance is created where
 * changes accumulated in this instance are merged with cached backing {@link SszData} instance
 * which weren't changed.
 *
 * <p>If this ssz data is get by reference from its parent composite view ({@link
 * SszMutableRefComposite#getByRef(int)} then all the changes are notified to the parent view (see
 * {@link SszMutableComposite#setInvalidator(Consumer)}
 *
 * <p>The mutable structures based on this class are inherently NOT thread safe
 */
public abstract class AbstractSszMutableComposite<
        SszChildT extends SszData, SszMutableChildT extends SszChildT>
    implements SszMutableRefComposite<SszChildT, SszMutableChildT> {

  protected AbstractSszComposite<SszChildT> backingImmutableData;
  private Consumer<SszMutableData> invalidator;
  private final ChildChanges childrenChanges = new ChildChanges();
  private Integer sizeCache;
  private final SszCompositeSchema<?> cachedSchema;

  /** Creates a new mutable instance with backing immutable data */
  protected AbstractSszMutableComposite(
      final AbstractSszComposite<SszChildT> backingImmutableData) {
    this.backingImmutableData = backingImmutableData;
    sizeCache = backingImmutableData.size();
    cachedSchema = backingImmutableData.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void set(final int index, final SszChildT value) {
    checkIndex(index, true);
    checkNotNull(value);
    validateChildSchema(index, value);

    final SszChildT immutableValue;
    if (value instanceof final SszMutableData sszMutableData) {
      immutableValue = (SszChildT) sszMutableData.commitChanges();
    } else {
      immutableValue = value;
    }

    childrenChanges.put(index, immutableValue);

    sizeCache = index >= sizeCache ? index + 1 : sizeCache;
    invalidate();
  }

  protected void validateChildSchema(final int index, final SszChildT value) {
    if (!value.getSchema().equals(getSchema().getChildSchema(index))) {
      throw new InvalidValueSchemaException(
          "Expected child to have schema "
              + getSchema().getChildSchema(index)
              + ", but value has schema "
              + value.getSchema());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszChildT get(final int index) {
    checkIndex(index, false);
    final Object change = childrenChanges.get(index);
    if (change == null) {
      return backingImmutableData.get(index);
    } else if (change instanceof ChildRef<?> childRef) {
      return (SszChildT) childRef.mutableChild();
    } else {
      return (SszChildT) change;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszMutableChildT getByRef(final int index) {
    if (childrenChanges.get(index) instanceof ChildRef<?> childRef) {
      return (SszMutableChildT) childRef.mutableChild();
    } else {
      SszChildT readView = get(index);
      SszMutableChildT w = (SszMutableChildT) readView.createWritableCopy();
      childrenChanges.put(index, new ChildRef<>(w));
      if (w instanceof SszMutableComposite) {
        ((SszMutableComposite<?>) w).setInvalidator(viewWrite -> invalidate());
      }
      return w;
    }
  }

  @Override
  public SszCompositeSchema<?> getSchema() {
    return cachedSchema;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void clear() {
    backingImmutableData = (AbstractSszComposite<SszChildT>) getSchema().getDefault();
    childrenChanges.clear();
    sizeCache = backingImmutableData.size();
    invalidate();
  }

  @Override
  public int size() {
    return sizeCache;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszComposite<SszChildT> commitChanges() {
    if (childrenChanges.isEmpty()) {
      return backingImmutableData;
    } else {
      IntCache<SszChildT> cache = backingImmutableData.transferCache();
      Stream<Map.Entry<Integer, SszChildT>> changesList =
          childrenChanges.stream(
              (childIndex, change) -> {
                final SszChildT newValue =
                    change instanceof ChildRef<?> childRef
                        ? (SszChildT) ((SszMutableData) childRef.mutableChild()).commitChanges()
                        : (SszChildT) change;
                // pre-fill the read cache with changed values
                cache.invalidateWithNewValueInt(childIndex, newValue);
                return Map.entry(childIndex, newValue);
              });
      TreeNode originalBackingTree = backingImmutableData.getBackingNode();
      TreeNode finalBackingTree = applyTreeChanges(changesList, originalBackingTree);
      return createImmutableSszComposite(finalBackingTree, cache);
    }
  }

  /**
   * Applies all child changes to the backing tree and returns the final tree node. Subclasses can
   * override this to implement custom tree update logic (e.g. progressive trees with mixed-depth
   * generalized indices). The default implementation chains {@link #changesToNewNodes} → {@link
   * TreeNode#updated} → {@link #doFinalTreeUpdates}.
   */
  protected TreeNode applyTreeChanges(
      final Stream<Map.Entry<Integer, SszChildT>> newChildValues,
      final TreeNode originalBackingTree) {
    TreeUpdates changes = changesToNewNodes(newChildValues, originalBackingTree);
    TreeNode newBackingTree = originalBackingTree.updated(changes);
    return doFinalTreeUpdates(newBackingTree);
  }

  protected TreeNode doFinalTreeUpdates(final TreeNode updatedTree) {
    return updatedTree;
  }

  /** Converts a set of changed view with their indices to the {@link TreeUpdates} instance */
  protected TreeUpdates changesToNewNodes(
      final Stream<Map.Entry<Integer, SszChildT>> newChildValues, final TreeNode original) {
    SszCompositeSchema<?> type = getSchema();
    if (type.getElementsPerChunk() > 1) {
      throw new IllegalStateException(
          "Packed primitive types are not supported by this implementation");
    }
    return newChildValues
        .map(
            e ->
                new TreeUpdates.Update(
                    type.getChildGeneralizedIndex(e.getKey()), e.getValue().getBackingNode()))
        .collect(TreeUpdates.collector());
  }

  /**
   * Should be implemented by subclasses to create respectful immutable view with backing tree and
   * views cache
   */
  protected abstract AbstractSszComposite<SszChildT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszChildT> viewCache);

  @Override
  public void setInvalidator(final Consumer<SszMutableData> listener) {
    invalidator = listener;
  }

  protected void invalidate() {
    if (invalidator != null) {
      invalidator.accept(this);
    }
  }

  /** Creating nested mutable copies is not supported yet */
  @Override
  public SszMutableComposite<SszChildT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable SszData only");
  }

  /**
   * Checks the child index for get or set
   *
   * @throws IndexOutOfBoundsException is index is not valid
   */
  protected abstract void checkIndex(int index, boolean set);

  /** A child handed out by reference: its changes are committed with this composite. */
  private record ChildRef<SszMutableChildT>(SszMutableChildT mutableChild) {}

  /**
   * The changed children by index, in a sparse array of pages: reads and writes take constant time,
   * whatever the number of changes, and the changes come out in index order. A slot holds the new
   * value of a child set by value, or a {@link ChildRef} for a child handed out by reference.
   */
  private static final class ChildChanges {
    private static final int PAGE_BITS = 6;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;
    private static final Object[][] NO_PAGES = new Object[0][];

    private Object[][] pages = NO_PAGES;
    private int size;

    Object get(final int index) {
      final int pageIndex = index >>> PAGE_BITS;
      if (pageIndex >= pages.length) {
        return null;
      }
      final Object[] page = pages[pageIndex];
      return page == null ? null : page[index & PAGE_MASK];
    }

    void put(final int index, final Object change) {
      final int pageIndex = index >>> PAGE_BITS;
      if (pageIndex >= pages.length) {
        pages = Arrays.copyOf(pages, Math.max(pageIndex + 1, pages.length * 2));
      }
      Object[] page = pages[pageIndex];
      if (page == null) {
        page = new Object[PAGE_SIZE];
        pages[pageIndex] = page;
      }
      final int slot = index & PAGE_MASK;
      if (page[slot] == null) {
        size++;
      }
      page[slot] = change;
    }

    boolean isEmpty() {
      return size == 0;
    }

    void clear() {
      pages = NO_PAGES;
      size = 0;
    }

    /** The changes in index order, each mapped by {@code mapper} as it is consumed. */
    <T> Stream<T> stream(final ChangeMapper<T> mapper) {
      final Iterator<T> iterator =
          new Iterator<>() {
            private int next = nextChange(0);

            @Override
            public boolean hasNext() {
              return next >= 0;
            }

            @Override
            public T next() {
              if (next < 0) {
                throw new NoSuchElementException();
              }
              final int index = next;
              next = nextChange(index + 1);
              return mapper.map(index, pages[index >>> PAGE_BITS][index & PAGE_MASK]);
            }
          };
      return StreamSupport.stream(
          Spliterators.spliterator(
              iterator, size, Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL),
          false);
    }

    /** The index of the first change at or after {@code from}, or -1 when there is none. */
    private int nextChange(final int from) {
      int pageIndex = from >>> PAGE_BITS;
      int slot = from & PAGE_MASK;
      while (pageIndex < pages.length) {
        final Object[] page = pages[pageIndex];
        if (page != null) {
          for (; slot < PAGE_SIZE; slot++) {
            if (page[slot] != null) {
              return (pageIndex << PAGE_BITS) | slot;
            }
          }
        }
        pageIndex++;
        slot = 0;
      }
      return -1;
    }
  }

  @FunctionalInterface
  private interface ChangeMapper<T> {
    T map(int index, Object change);
  }
}

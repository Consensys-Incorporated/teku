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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.stream.Stream;
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
  private final ChildChanges<SszChildT> childrenChanges = new ChildChanges<>();
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
  public void set(final int index, final SszChildT value) {
    checkIndex(index, true);
    checkNotNull(value);
    validateChildSchema(index, value);

    childrenChanges.put(index, immutable(value));

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
  public SszChildT get(final int index) {
    checkIndex(index, false);
    final SszChildT change = childrenChanges.get(index);
    return change != null ? change : backingImmutableData.get(index);
  }

  @Override
  public SszMutableChildT getByRef(final int index) {
    final SszChildT child = get(index);
    if (child instanceof SszMutableData childRef) {
      // Already handed out by reference
      return mutableChild(childRef);
    }
    final SszMutableChildT childRef = mutableChild(child.createWritableCopy());
    childrenChanges.put(index, childRef);
    if (childRef instanceof SszMutableComposite<?> mutableComposite) {
      mutableComposite.setInvalidator(viewWrite -> invalidate());
    }
    return childRef;
  }

  /** The immutable value of a child: one handed out by reference is committed. */
  @SuppressWarnings("unchecked")
  private SszChildT immutable(final SszChildT child) {
    return child instanceof SszMutableData mutableChild
        ? (SszChildT) mutableChild.commitChanges()
        : child;
  }

  @SuppressWarnings("unchecked")
  private SszMutableChildT mutableChild(final SszMutableData child) {
    return (SszMutableChildT) child;
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
  public SszComposite<SszChildT> commitChanges() {
    if (childrenChanges.isEmpty()) {
      return backingImmutableData;
    } else {
      IntCache<SszChildT> cache = backingImmutableData.transferCache();
      final List<Map.Entry<Integer, SszChildT>> newChildValues =
          new ArrayList<>(childrenChanges.size());
      childrenChanges.forEach(
          (change, childIndex) -> {
            final SszChildT newValue = immutable(change);
            // pre-fill the read cache with changed values
            cache.invalidateWithNewValueInt(childIndex, newValue);
            newChildValues.add(Map.entry(childIndex, newValue));
          });
      TreeNode originalBackingTree = backingImmutableData.getBackingNode();
      TreeNode finalBackingTree = applyTreeChanges(newChildValues.stream(), originalBackingTree);
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

  /**
   * The changed children by index, in a sparse list of pages: reads and writes take constant time,
   * whatever the number of changes, and the changes are visited in index order. A child handed out
   * by reference is held as its mutable view, a child set by value as its immutable value.
   */
  private static final class ChildChanges<T> {
    private static final int PAGE_BITS = 6;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;

    // A page is created, filled with nulls, on the first change that falls in it
    private final List<List<T>> pages = new ArrayList<>();
    private int size;

    T get(final int index) {
      final int pageIndex = index >>> PAGE_BITS;
      final List<T> page = pageIndex < pages.size() ? pages.get(pageIndex) : null;
      return page == null ? null : page.get(index & PAGE_MASK);
    }

    void put(final int index, final T change) {
      final int pageIndex = index >>> PAGE_BITS;
      if (pageIndex >= pages.size()) {
        pages.addAll(Collections.nCopies(pageIndex + 1 - pages.size(), null));
      }
      List<T> page = pages.get(pageIndex);
      if (page == null) {
        page = new ArrayList<>(Collections.nCopies(PAGE_SIZE, null));
        pages.set(pageIndex, page);
      }
      if (page.set(index & PAGE_MASK, change) == null) {
        size++;
      }
    }

    int size() {
      return size;
    }

    boolean isEmpty() {
      return size == 0;
    }

    void clear() {
      pages.clear();
      size = 0;
    }

    /** Visits the changes in index order. */
    void forEach(final ObjIntConsumer<T> action) {
      for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
        final List<T> page = pages.get(pageIndex);
        if (page != null) {
          for (int slot = 0; slot < PAGE_SIZE; slot++) {
            final T change = page.get(slot);
            if (change != null) {
              action.accept(change, (pageIndex << PAGE_BITS) | slot);
            }
          }
        }
      }
    }
  }
}

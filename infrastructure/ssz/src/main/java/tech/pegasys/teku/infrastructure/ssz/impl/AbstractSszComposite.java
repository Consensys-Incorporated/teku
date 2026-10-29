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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszComposite;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszElementVisitor;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeVisitor;

/**
 * Base backing class for immutable composite ssz structures (lists, vectors, containers)
 *
 * <p>It caches it's child ssz instances so that if the underlying tree nodes are not changed (in
 * the corresponding mutable classes) the instances are not recreated from tree nodes on later
 * access.
 *
 * <p>Though internally this class has a mutable cache it may be thought of as immutable instance
 * and used safely across threads
 *
 * @param <SszChildT> the type of children. For heterogeneous composites (like container) this type
 *     would be just generic {@link SszData}
 */
public abstract class AbstractSszComposite<SszChildT extends SszData>
    implements SszComposite<SszChildT> {

  private final IntCache<SszChildT> childrenViewCache;
  private int sizeCache = -1;
  private final SszCompositeSchema<?> schema;

  // Exactly one of these fields is set at a time: either the backing node has already been
  // materialized, or the supplier is still available to create it lazily.
  private volatile TreeNode backingNode;
  private volatile Supplier<TreeNode> lazyBackingNode;

  /** Creates an instance from a schema and a backing node */
  protected AbstractSszComposite(
      final SszCompositeSchema<?> schema, final Supplier<TreeNode> lazyBackingNode) {
    this(schema, null, lazyBackingNode, null);
  }

  protected AbstractSszComposite(final SszCompositeSchema<?> schema, final TreeNode backingNode) {
    this(schema, backingNode, null, null);
  }

  /**
   * Creates an instance from a schema and a backing node.
   *
   * <p>{@link SszData} instances cache is supplied for optimization to shortcut children creation
   * from backing nodes. The cache should correspond to the supplied backing tree.
   */
  protected AbstractSszComposite(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final IntCache<SszChildT> cache) {
    this(schema, backingNode, null, cache);
  }

  private AbstractSszComposite(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final Supplier<TreeNode> lazyBackingNode,
      final IntCache<SszChildT> cache) {
    this.schema = schema;
    this.backingNode = backingNode;
    this.lazyBackingNode = lazyBackingNode;
    this.childrenViewCache = cache != null ? cache : createCache();
  }

  /**
   * 'Transfers' the cache to a new Cache instance eliminating all the cached values from the
   * current view cache. This is made under assumption that the ssz data instance this cache is
   * transferred to would be used further with high probability and this ssz data instance would be
   * either GCed or used with lower probability
   */
  IntCache<SszChildT> transferCache() {
    return childrenViewCache.transfer();
  }

  /**
   * Creates a new empty children cache. Could be overridden by subclasses for fine tuning of the
   * initial cache size
   */
  protected IntCache<SszChildT> createCache() {
    return new ArrayIntCache<>();
  }

  @Override
  public final SszChildT get(final int index) {
    SszChildT child = childrenViewCache.getCachedInt(index);
    if (child == null) {
      child = getImplWithIndexCheck(index);
      childrenViewCache.invalidateWithNewValueInt(index, child);
    }
    return child;
  }

  private SszChildT getImplWithIndexCheck(final int index) {
    checkIndex(index);
    return getImpl(index);
  }

  /** Cache miss fallback child getter. This is where child is created from the backing tree node */
  protected abstract SszChildT getImpl(int index);

  @Override
  public SszCompositeSchema<?> getSchema() {
    return schema;
  }

  @Override
  public TreeNode getBackingNode() {
    TreeNode node = backingNode;
    if (node == null) {
      node = resolveLazyBackingNode();
    }
    return node;
  }

  private synchronized TreeNode resolveLazyBackingNode() {
    if (backingNode == null) {
      backingNode = lazyBackingNode.get();
      lazyBackingNode = null;
    }
    return backingNode;
  }

  @Override
  public final int size() {
    if (sizeCache == -1) {
      sizeCache = sizeImpl();
    }
    return sizeCache;
  }

  /** Size value is normally cached. This method calculates the size from backing tree */
  protected abstract int sizeImpl();

  /**
   * Checks the child index
   *
   * @throws IndexOutOfBoundsException if index is invalid
   */
  protected abstract void checkIndex(int index);

  /** See {@link SszList#forEachElementSsz}. Supported on collections only. */
  public boolean forEachElementSsz(final int fromIndex, final SszElementVisitor visitor) {
    checkArgument(fromIndex >= 0, "Negative start index %s", fromIndex);
    final int size = size();
    if (fromIndex >= size) {
      return true;
    }
    final ElementWalker walker = new ElementWalker(getSchema(), size, fromIndex, visitor);
    getBackingNode().iterate(GIndexUtil.SELF_G_INDEX, walker.nextGIndex, walker);
    return !walker.stopped;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszComposite)) {
      return false;
    }
    SszComposite<?> that = (SszComposite<?>) o;
    return getSchema().equals(that.getSchema()) && hashTreeRoot().equals(that.hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(28).toInt();
  }

  /**
   * Walks the backing tree in order (self, left subtree, right subtree), which is element order on
   * bounded and progressive lists alike, following the generalized index of the next element. The
   * walk starts at the first element and stops after the last one, so the zero subtrees past the
   * end of the list are never visited.
   */
  private static final class ElementWalker implements TreeVisitor {
    private final SszCompositeSchema<?> schema;
    private final SszSchema<?> elementSchema;
    private final int elementSize;
    private final int size;
    private final SszElementVisitor visitor;
    private int next;
    private long nextGIndex;
    private boolean stopped;

    private ElementWalker(
        final SszCompositeSchema<?> schema,
        final int size,
        final int fromIndex,
        final SszElementVisitor visitor) {
      checkArgument(
          schema instanceof SszCollectionSchema<?, ?> collectionSchema
              && !collectionSchema.getElementSchema().isPrimitive()
              && collectionSchema.getElementSchema().isFixedSize(),
          "Only collections of fixed-size composite elements are supported: %s",
          schema);
      this.schema = schema;
      this.elementSchema = ((SszCollectionSchema<?, ?>) schema).getElementSchema();
      this.elementSize = elementSchema.getSszFixedPartSize();
      this.size = size;
      this.visitor = visitor;
      this.next = fromIndex;
      this.nextGIndex = schema.getChildGeneralizedIndex(fromIndex);
    }

    @Override
    public boolean visit(final TreeNode node, final long gIndex) {
      final NodeRelation relation = GIndexUtil.gIdxCompare(gIndex, nextGIndex);
      if (relation == NodeRelation.SAME
          || (relation == NodeRelation.PREDECESSOR && !(node instanceof BranchNode))) {
        return visitElementsUnder(node, gIndex);
      }
      // A branch above the next element, or a node inside an element already visited
      return true;
    }

    /** Visits the elements of the subtree {@code node}, from the next one, in index order. */
    private boolean visitElementsUnder(final TreeNode node, final long gIndex) {
      final int depth = GIndexUtil.gIdxGetDepth(nextGIndex) - GIndexUtil.gIdxGetDepth(gIndex);
      final long first = nextGIndex - (gIndex << depth);
      final long end = Math.min(1L << depth, first + size - next);
      final Bytes packed =
          node instanceof SszSuperNode superNode && superNode.getDepth() == depth
              ? superNode.getData()
              : null;
      for (long i = first; i < end; i++) {
        final boolean proceed;
        if (packed == null) {
          final TreeNode element =
              node.get(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, i, depth));
          proceed = visitor.visit(next, elementSchema.sszSerializeTree(element), 0);
        } else if ((i + 1) * elementSize <= packed.size()) {
          proceed = visitor.visit(next, packed, Math.toIntExact(i * elementSize));
        } else {
          // Past the supernode's content: a zero element. Reachable on a tree loaded from storage,
          // where an all-zero chunk inside the list is restored as an empty supernode.
          proceed = visitor.visit(next, Bytes.wrap(new byte[elementSize]), 0);
        }
        next++;
        if (!proceed) {
          stopped = true;
          return false;
        }
      }
      if (next == size) {
        return false;
      }
      nextGIndex = schema.getChildGeneralizedIndex(next);
      return true;
    }
  }
}

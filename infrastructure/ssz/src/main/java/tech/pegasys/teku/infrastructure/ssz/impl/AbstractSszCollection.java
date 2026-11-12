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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPackedViewSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeVisitor;

public abstract class AbstractSszCollection<SszElementT extends SszData>
    extends AbstractSszComposite<SszElementT> implements SszCollection<SszElementT> {

  // The element schema when its views read their fields from packed bytes, else null
  private final SszPackedViewSchema<SszElementT> packedViewSchema;

  // Built on the first access to an element through a view of its packed bytes
  private volatile ElementLocator elementLocator;

  protected AbstractSszCollection(
      final SszCompositeSchema<?> schema, final Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
    this.packedViewSchema = packedViewSchemaOf(schema);
  }

  protected AbstractSszCollection(final SszCompositeSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    this.packedViewSchema = packedViewSchemaOf(schema);
  }

  protected AbstractSszCollection(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
    this.packedViewSchema = packedViewSchemaOf(schema);
  }

  @SuppressWarnings("unchecked")
  private static <V extends SszData> SszPackedViewSchema<V> packedViewSchemaOf(
      final SszCompositeSchema<?> schema) {
    return ((SszCollectionSchema<?, ?>) schema).getElementSchema()
            instanceof SszPackedViewSchema<?> viewSchema
        ? (SszPackedViewSchema<V>) viewSchema
        : null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszCollectionSchema<SszElementT, ?> getSchema() {
    return (SszCollectionSchema<SszElementT, ?>) super.getSchema();
  }

  /**
   * The views of elements that read their fields from the packed bytes are created on every access
   * (see {@link #getImpl(int)}) and never cached, so the collection does not keep one view per
   * element alive. This holds for the elements that are not packed in a supernode too, whose views
   * are then created from their tree node on every access.
   */
  @Override
  protected IntCache<SszElementT> createCache() {
    // Called by the super constructor, before the field of this class is set
    return packedViewSchemaOf(getSchema()) != null ? IntCache.noop() : super.createCache();
  }

  private ElementLocator elementLocator() {
    ElementLocator locator = elementLocator;
    if (locator == null) {
      // A benign race: concurrent first reads build equal locators
      locator = new ElementLocator(this);
      elementLocator = locator;
    }
    return locator;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected SszElementT getImpl(final int index) {
    if (packedViewSchema != null) {
      final SszElementT packedView = elementLocator().packedView(index, packedViewSchema);
      if (packedView != null) {
        return packedView;
      }
    }
    SszCollectionSchema<SszElementT, ?> type = this.getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    if (elementType.isPrimitive()) {
      // several primitive values could be packed to a single leaf node
      SszPrimitiveSchema<?, ?> primitiveElementType = (SszPrimitiveSchema<?, ?>) elementType;
      TreeNode node =
          getBackingNode().get(type.getChildGeneralizedIndex(index / type.getElementsPerChunk()));
      return (SszElementT)
          primitiveElementType.createFromPackedNode(node, index % type.getElementsPerChunk());
    } else {
      TreeNode node = getBackingNode().get(type.getChildGeneralizedIndex(index));
      return (SszElementT) elementType.createFromBackingNode(node);
    }
  }

  /// Finds the serialized bytes of an element of a list packed in supernodes, so that
  /// `getImpl(int)` can create a view over them without going down the tree to the element.
  ///
  /// ## The layout it relies on
  ///
  /// A list with a supernode hint of depth `d` keeps its elements in `SszSuperNode`s: each holds
  /// up to `2^d` consecutive elements as one flat byte array, with a plain binary tree above
  /// them. The validator registry uses `d = 8`, 256 validators of 121 bytes in each supernode, so
  /// a registry of 2.4 million validators has about 9,400 supernodes:
  ///
  /// ```
  ///         validators list root
  ///           /              \
  ///        branch            branch            <- ordinary tree
  ///       /      \          /      \
  ///    branch   branch   branch   branch
  ///      |        |        |        |
  ///   [SN 0]   [SN 1]   [SN 2]   [SN 3] ...    <- supernodes (about 9,400)
  ///   bytes    bytes    bytes    bytes
  ///   0..255  256..511 512..767  ...           <- validator indices covered
  /// ```
  ///
  /// ## The table
  ///
  /// The locator is a table of *segments*, one for each node that holds elements, in index
  /// order, kept as two parallel arrays:
  ///
  /// - `segmentStarts[s]` is the index of the first element of segment `s`
  /// - `segmentData[s]` is the content of its supernode (a reference: no bytes are copied), or
  ///   `null` when the node is not a supernode
  ///
  /// [SegmentCollector] fills it with one walk of the tree. On a packed list the walk visits the
  /// supernodes and the branches above them, about twice the number of supernodes, and never an
  /// element. A list that is not packed at all ends up as a single segment without content.
  ///
  /// ## Lookup
  ///
  /// For the element at index `i`:
  ///
  /// 1. `segment = i >>> segmentShift` when every segment but the last holds the same power of
  ///    two of elements, as on a bounded list. Otherwise, as on a progressive list, whose levels
  ///    grow, it is a binary search of `segmentStarts`.
  /// 2. `offset = (i - segmentStarts[segment]) * elementSize`
  /// 3. The element schema creates the view from `segmentData[segment]` at `offset`.
  ///
  /// The lookup answers `null` when the segment has no content or the offset is past it, and the
  /// caller then creates the view from the element's tree node.
  ///
  /// ## Lifetime
  ///
  /// A locator belongs to one immutable list and is built on the first read of an element. A
  /// change never patches it: committing a mutable list creates a new tree, in which only the
  /// supernodes that changed have new bytes, and a new list, which builds its own table on its
  /// first read. A list that is not changed keeps its table, and the list it was changed from
  /// stays valid with its own.
  ///
  /// ## Why not a cache of views
  ///
  /// A cache keeps one view per element alive for as long as the list, and hands them from list
  /// to list on every change. The table is two arrays with one entry per supernode, and a view
  /// created from it is one small object that is dropped after use.
  private static final class ElementLocator {
    private final int elementSize;
    private final int[] segmentStarts;
    private final Bytes[] segmentData;
    // log2 of the number of elements of every segment but the last, or -1 when they differ
    private final int segmentShift;

    private ElementLocator(final AbstractSszCollection<?> list) {
      this.elementSize = list.getSchema().getElementSchema().getSszFixedPartSize();
      final int size = list.size();
      final SegmentCollector collector = new SegmentCollector(list.getSchema(), size);
      if (size > 0) {
        list.getBackingNode().iterate(GIndexUtil.SELF_G_INDEX, collector.nextGIndex, collector);
      }
      if (collector.next == size) {
        this.segmentStarts = collector.starts.toIntArray();
        this.segmentData = collector.data.toArray(Bytes[]::new);
      } else {
        // The walk was abandoned: the list is not packed in supernodes
        this.segmentStarts = new int[] {0};
        this.segmentData = new Bytes[] {null};
      }
      this.segmentShift = uniformShift(segmentStarts, size);
    }

    private static int uniformShift(final int[] starts, final int size) {
      if (starts.length < 2) {
        return 31;
      }
      final int length = starts[1];
      // The last segment must not hold more elements than the others, as it does on a progressive
      // list, whose subtrees grow
      if (Integer.bitCount(length) != 1 || (long) starts.length * length < size) {
        return -1;
      }
      for (int i = 1; i < starts.length; i++) {
        if (starts[i] != i * length) {
          return -1;
        }
      }
      return Integer.numberOfTrailingZeros(length);
    }

    /**
     * The view of the element at {@code index} (within the list) created by its schema from the
     * content of the element's supernode, or null when the element has no bytes there.
     */
    private <V extends SszData> V packedView(
        final int index, final SszPackedViewSchema<V> viewSchema) {
      final int segment = segmentOf(index);
      final Bytes data = segmentData[segment];
      if (data == null) {
        return null;
      }
      final int offset = (index - segmentStarts[segment]) * elementSize;
      // No bytes for a zero element past the supernode's content, which is reachable on a tree
      // loaded from storage, where an all-zero chunk inside the list is restored as an empty
      // supernode
      return offset + elementSize <= data.size()
          ? viewSchema.createFromPackedBytes(data, offset)
          : null;
    }

    private int segmentOf(final int index) {
      if (segmentShift >= 0) {
        return index >>> segmentShift;
      }
      final int position = Arrays.binarySearch(segmentStarts, index);
      return position >= 0 ? position : -position - 2;
    }
  }

  /// Collects the segments of an [ElementLocator]: a tree visitor that walks the list once, left
  /// to right, towards the next element it has not covered yet.
  ///
  /// - A branch above that element is entered.
  /// - Any other node at or above it ends the descent and becomes a segment. A supernode whose
  ///   depth matches its position contributes its packed content, any other node a segment
  ///   without content. The segment covers `2^depth` elements, capped at the list size, and the
  ///   walk moves on to the element after them.
  /// - The walk stops once the segments cover the whole list.
  ///
  /// A list of unpacked elements would give one segment per element, so the walk is abandoned,
  /// before the last element, once there are more than `size / 8 + 16` segments, too many to be
  /// supernodes. The locator then treats the list as not packed.
  private static final class SegmentCollector implements TreeVisitor {
    private final SszCompositeSchema<?> schema;
    private final int size;
    private final int maxSegments;
    private final IntArrayList starts = new IntArrayList();
    private final List<Bytes> data = new ArrayList<>();
    private int next = 0;
    private long nextGIndex;

    private SegmentCollector(final SszCompositeSchema<?> schema, final int size) {
      this.schema = schema;
      this.size = size;
      this.maxSegments = size / 8 + 16;
      this.nextGIndex = schema.getChildGeneralizedIndex(0);
    }

    @Override
    public boolean visit(final TreeNode node, final long gIndex) {
      final NodeRelation relation = GIndexUtil.gIdxCompare(gIndex, nextGIndex);
      if (relation == NodeRelation.SAME
          || (relation == NodeRelation.PREDECESSOR && !(node instanceof BranchNode))) {
        return collectSegment(node, gIndex);
      }
      // A branch above the next element, or a node inside an element already covered
      return true;
    }

    private boolean collectSegment(final TreeNode node, final long gIndex) {
      final int depth = GIndexUtil.gIdxGetDepth(nextGIndex) - GIndexUtil.gIdxGetDepth(gIndex);
      if (starts.size() == maxSegments) {
        return false;
      }
      starts.add(next);
      data.add(
          node instanceof SszSuperNode superNode && superNode.getDepth() == depth
              ? superNode.getData()
              : null);
      next += Math.toIntExact(Math.min(1L << depth, (long) size - next));
      if (next == size) {
        return false;
      }
      nextGIndex = schema.getChildGeneralizedIndex(next);
      return true;
    }
  }
}

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
import tech.pegasys.teku.infrastructure.ssz.schema.SszSuperNodeLayout;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszCollection<SszElementT extends SszData>
    extends AbstractSszComposite<SszElementT> implements SszCollection<SszElementT> {

  // The element schema when the collection schema packs the elements in supernodes and their
  // views can read the packed bytes, else null
  private final SszPackedViewSchema<SszElementT> packedViewSchema;

  // Created on the first read of an element. Not volatile: all its fields are final, so a reader
  // racing with its creation sees a complete locator or none
  private ElementLocator elementLocator;

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
    final SszCollectionSchema<?, ?> collectionSchema = (SszCollectionSchema<?, ?>) schema;
    return collectionSchema.getSuperNodeLayout().isPresent()
            && collectionSchema.getElementSchema() instanceof SszPackedViewSchema<?> viewSchema
        ? (SszPackedViewSchema<V>) viewSchema
        : null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszCollectionSchema<SszElementT, ?> getSchema() {
    return (SszCollectionSchema<SszElementT, ?>) super.getSchema();
  }

  /**
   * The views that read their fields from the packed bytes are created on every access (see {@link
   * #packedView(int)}) and never cached: such a view is one small object, so the collection does
   * not keep one per element alive, nor hand them all to the next collection on every change. This
   * holds for the elements the tree does not hold in a supernode too, whose views are then created
   * from their tree node on every access.
   */
  @Override
  protected IntCache<SszElementT> createCache() {
    // Called by the super constructor, before the field of this class is set
    return packedViewSchemaOf(getSchema()) != null ? IntCache.noop() : super.createCache();
  }

  /**
   * The view of the element at {@code index} over the packed bytes of its supernode, or null when
   * this collection has no such views or the element is not in a supernode. The implementations of
   * {@link #getImpl(int)} for composite elements ask for it first.
   */
  protected final SszElementT packedView(final int index) {
    if (packedViewSchema == null) {
      return null;
    }
    ElementLocator locator = elementLocator;
    if (locator == null) {
      // A benign race: concurrent first reads create equal, empty locators
      locator = new ElementLocator();
      elementLocator = locator;
    }
    return locator.packedView(index);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected SszElementT getImpl(final int index) {
    final SszElementT packedView = packedView(index);
    if (packedView != null) {
      return packedView;
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

  /// Creates the views of the elements of a collection packed in supernodes over their
  /// serialized bytes, without going down the tree to the element.
  ///
  /// ## The layout it relies on
  ///
  /// A collection with a supernode hint of depth `d` keeps its elements in `SszSuperNode`s: each
  /// holds up to `2^d` consecutive elements as one flat byte array, with a plain binary tree
  /// above them. The validator registry uses `d = 8`, 256 validators of 121 bytes in each
  /// supernode, so a registry of 2.4 million validators has about 9,400 supernodes:
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
  /// A progressive list has one such tree for each of its levels, with smaller supernodes on
  /// the first levels and none on level 0. The schema describes this with the groups of its
  /// `SszSuperNodeLayout`: all the supernodes of a group hold the same number of elements. A
  /// bounded list is one group, a progressive list has one for each level.
  ///
  /// ## The table
  ///
  /// `superNodeData[group][s]` is the content of supernode `s` of the group: a reference, no
  /// bytes are copied. A slot starts empty and is filled on the first read of one of the
  /// supernode's elements, by going down the tree to that supernode only. Nothing else is
  /// visited, so on a tree that loads its nodes on demand one read loads one supernode. When
  /// the node found there is not the supernode the layout describes, as on a progressive list
  /// built from elements, the slot holds no bytes and the views of its elements are created
  /// from their tree nodes.
  ///
  /// ## Lifetime
  ///
  /// A locator belongs to one immutable collection. A change never patches it: committing a
  /// mutable list creates a new tree, in which only the supernodes that changed have new bytes,
  /// and a new list with an empty table of its own. The list it was changed from stays valid
  /// with its table.
  private final class ElementLocator {
    private final SszSuperNodeLayout layout;
    private final int elementSize;
    private final int[] groupStarts;
    private final int[] superNodeDepths;
    // By group, then by supernode: null for a group whose elements are not in supernodes. The
    // slots are written without synchronization: concurrent readers may each resolve the same
    // supernode once.
    private final Bytes[][] superNodeData;

    private ElementLocator() {
      this.layout = getSchema().getSuperNodeLayout().orElseThrow();
      this.elementSize = getSchema().getElementSchema().getSszFixedPartSize();
      final int size = size();
      final int groups = size == 0 ? 0 : layout.groupOf(size - 1) + 1;
      this.groupStarts = new int[groups];
      this.superNodeDepths = new int[groups];
      this.superNodeData = new Bytes[groups][];
      for (int group = 0; group < groups; group++) {
        final int start = layout.groupStart(group);
        final int end = group + 1 < groups ? layout.groupStart(group + 1) : size;
        final int depth = layout.superNodeDepth(group);
        groupStarts[group] = start;
        superNodeDepths[group] = depth;
        if (depth > 0) {
          superNodeData[group] = new Bytes[((end - start - 1) >>> depth) + 1];
        }
      }
    }

    private SszElementT packedView(final int index) {
      final int group = layout.groupOf(index);
      final Bytes[] groupData = superNodeData[group];
      if (groupData == null) {
        return null;
      }
      final int depth = superNodeDepths[group];
      final int position = index - groupStarts[group];
      final int superNode = position >>> depth;
      Bytes data = groupData[superNode];
      if (data == null) {
        data = loadSuperNodeData(index, depth);
        groupData[superNode] = data;
      }
      final int offset = (position & ((1 << depth) - 1)) * elementSize;
      // No bytes when the node is not a supernode, nor for a zero element past the supernode's
      // content, which is reachable on a tree loaded from storage, where an all-zero chunk inside
      // the list is restored as an empty supernode
      return offset + elementSize <= data.size()
          ? packedViewSchema.createFromPackedBytes(data, offset)
          : null;
    }

    /**
     * The content of the supernode holding the element at {@code index}, or no bytes when the node
     * at its place is not that supernode. Only the path to the node is visited, so a tree that
     * loads its nodes on demand loads nothing else.
     */
    private Bytes loadSuperNodeData(final int index, final int depth) {
      final TreeNode node =
          getBackingNode().get(getSchema().getChildGeneralizedIndex(index) >>> depth);
      return node instanceof SszSuperNode superNode && superNode.getDepth() == depth
          ? superNode.getData()
          : Bytes.EMPTY;
    }
  }
}

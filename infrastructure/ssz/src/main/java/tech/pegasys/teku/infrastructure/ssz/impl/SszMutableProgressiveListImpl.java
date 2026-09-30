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

package tech.pegasys.teku.infrastructure.ssz.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefList;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.AbstractSszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedProgressiveByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Mutable implementation of SszList backed by a progressive merkle tree. Handles both composite and
 * packed primitive element types.
 *
 * <p>Overrides {@link #applyTreeChanges} because progressive trees have mixed-depth generalized
 * indices that cannot go through the standard {@code changesToNewNodes → updated} pipeline. Changes
 * are applied via {@link ProgressiveTreeUtil#updateProgressiveTree}.
 */
public class SszMutableProgressiveListImpl<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableCollection<SszElementT, SszMutableElementT>
    implements SszMutableRefList<SszElementT, SszMutableElementT> {

  private int cachedSize;

  public SszMutableProgressiveListImpl(
      final SszProgressiveListImpl<SszElementT> backingImmutableList) {
    super(backingImmutableList);
    cachedSize = backingImmutableList.size();
  }

  @Override
  protected void validateChildSchema(final int index, final SszElementT value) {
    if (value.getSchema().isPrimitive() && getSchema().getElementSchema().isPrimitive()) {
      return;
    }
    super.validateChildSchema(index, value);
  }

  @Override
  protected TreeNode applyTreeChanges(
      final Stream<Map.Entry<Integer, SszElementT>> newChildValues,
      final TreeNode originalBackingTree) {
    final List<Map.Entry<Integer, SszElementT>> changes = newChildValues.toList();
    if (changes.isEmpty()) {
      return PackedChunkUpdateUtil.updateSize(originalBackingTree, size());
    }

    TreeNode dataTree = originalBackingTree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    if (dataTree instanceof SszPackedProgressiveByteListsNode packedNode) {
      dataTree = packedNode.materialize();
    }
    final int elementsPerChunk = getSchema().getElementsPerChunk();
    final int previousSize = backingImmutableData.size();

    final Int2ObjectMap<TreeNode> chunkUpdates =
        buildChunkUpdates(changes, dataTree, elementsPerChunk, previousSize);

    final int totalChunks =
        elementsPerChunk > 1 ? (size() + elementsPerChunk - 1) / elementsPerChunk : size();

    final TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(
            dataTree, chunkUpdates, totalChunks, getSchema()::getLevelDefaultSubtree);

    return BranchNode.create(updatedDataTree, PackedChunkUpdateUtil.createSizeNode(size()));
  }

  private Int2ObjectMap<TreeNode> buildChunkUpdates(
      final List<Map.Entry<Integer, SszElementT>> changes,
      final TreeNode dataTree,
      final int elementsPerChunk,
      final int previousSize) {
    if (elementsPerChunk > 1) {
      return buildPackedUpdates(changes, dataTree, elementsPerChunk, previousSize);
    } else {
      final Int2ObjectMap<TreeNode> chunkUpdates = new Int2ObjectOpenHashMap<>();
      for (Map.Entry<Integer, SszElementT> entry : changes) {
        chunkUpdates.put((int) entry.getKey(), entry.getValue().getBackingNode());
      }
      return chunkUpdates;
    }
  }

  @SuppressWarnings("unchecked")
  private <DataT, SszDataT extends SszPrimitive<DataT>> Int2ObjectMap<TreeNode> buildPackedUpdates(
      final List<Map.Entry<Integer, SszElementT>> changes,
      final TreeNode dataTree,
      final int elementsPerChunk,
      final int previousSize) {
    final SszPrimitiveSchema<DataT, SszDataT> primitiveSchema =
        (SszPrimitiveSchema<DataT, SszDataT>) getSchema().getElementSchema();
    final List<Map.Entry<Integer, SszDataT>> typedChanges =
        (List<Map.Entry<Integer, SszDataT>>) (List<?>) changes;
    return PackedChunkUpdateUtil.buildPackedChunkUpdates(
        typedChanges, dataTree, elementsPerChunk, previousSize, primitiveSchema);
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public void set(final int index, final SszElementT value) {
    super.set(index, value);
    if (index == cachedSize) {
      cachedSize++;
    }
  }

  @Override
  public void clear() {
    super.clear();
    cachedSize = 0;
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index < 0
        || (!set && index >= size())
        || (set && (index > size() || index >= getSchema().getMaxLength()))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive list with size " + size());
    }
  }

  @Override
  protected SszProgressiveListImpl<SszElementT> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszElementT> childrenCache) {
    return new SszProgressiveListImpl<>(getSchema(), backingNode, childrenCache);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AbstractSszProgressiveListSchema<SszElementT, ?> getSchema() {
    return (AbstractSszProgressiveListSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  public SszList<SszElementT> commitChanges() {
    return (SszList<SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}

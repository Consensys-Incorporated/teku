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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.infrastructure.ssz.impl.PackedChunkUpdateUtil;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Mutable implementation of SszBitlist backed by a progressive merkle tree.
 *
 * <p>Overrides {@link #applyTreeChanges} because progressive trees have mixed-depth generalized
 * indices. Groups bit changes by 256-bit chunk, applies packed bit updates per chunk, and uses
 * {@link ProgressiveTreeUtil#updateProgressiveTree} to apply chunk-level changes to the progressive
 * data tree.
 */
public class SszMutableProgressiveBitlistImpl
    extends AbstractSszMutablePrimitiveCollection<Boolean, SszBit>
    implements SszMutablePrimitiveList<Boolean, SszBit> {

  private static final int BITS_PER_CHUNK = 256;

  private int cachedSize;

  public SszMutableProgressiveBitlistImpl(final SszProgressiveBitlistImpl backingImmutableBitlist) {
    super(backingImmutableBitlist);
    cachedSize = backingImmutableBitlist.size();
  }

  @Override
  protected TreeNode applyTreeChanges(
      final Stream<Map.Entry<Integer, SszBit>> newChildValues, final TreeNode originalBackingTree) {
    final SszPrimitiveSchema<Boolean, SszBit> primitiveSchema = getPrimitiveElementSchema();
    final TreeNode dataTree = originalBackingTree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final int previousSize = backingImmutableData.size();

    final Int2ObjectMap<TreeNode> chunkUpdates =
        PackedChunkUpdateUtil.buildPackedChunkUpdates(
            newChildValues.toList(), dataTree, BITS_PER_CHUNK, previousSize, primitiveSchema);

    if (chunkUpdates.isEmpty()) {
      return PackedChunkUpdateUtil.updateSize(originalBackingTree, size());
    }

    final int totalChunks = (size() + BITS_PER_CHUNK - 1) / BITS_PER_CHUNK;

    final TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(dataTree, chunkUpdates, totalChunks);

    return BranchNode.create(updatedDataTree, PackedChunkUpdateUtil.createSizeNode(size()));
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public void set(final int index, final SszBit value) {
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
          "Invalid index "
              + index
              + " for progressive bitlist with size "
              + size()
              + " and max length "
              + getSchema().getMaxLength());
    }
  }

  @Override
  protected AbstractSszComposite<SszBit> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszBit> childrenCache) {
    return new SszProgressiveBitlistImpl(getSchema(), backingNode);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszProgressiveBitlistSchema getSchema() {
    return (SszProgressiveBitlistSchema) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveList<Boolean, SszBit> commitChanges() {
    return (SszPrimitiveList<Boolean, SszBit>) super.commitChanges();
  }

  @Override
  public SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}

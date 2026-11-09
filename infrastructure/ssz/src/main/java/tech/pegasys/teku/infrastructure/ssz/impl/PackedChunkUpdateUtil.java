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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Shared helpers for building packed-primitive chunk updates in progressive tree-backed mutable
 * collections. Used by {@code SszMutableProgressiveListImpl} (packed branch) and {@code
 * SszMutableProgressiveBitlistImpl}.
 */
public class PackedChunkUpdateUtil {

  private PackedChunkUpdateUtil() {}

  /**
   * Groups element-level changes by chunk index and applies {@link
   * SszPrimitiveSchema#updatePackedNode} per chunk.
   *
   * @param changes element-level changes (elementIndex -> value)
   * @param dataTree the current progressive data tree (left child of root)
   * @param elementsPerChunk how many primitive elements fit in one 32-byte chunk
   * @param previousSize the previous collection size (used to decide whether to read existing
   *     chunks)
   * @param primitiveSchema the primitive element schema
   * @return map from chunk index to updated chunk node
   */
  public static <DataT, SszDataT extends SszPrimitive<DataT>>
      Int2ObjectMap<TreeNode> buildPackedChunkUpdates(
          final List<? extends Map.Entry<Integer, SszDataT>> changes,
          final TreeNode dataTree,
          final int elementsPerChunk,
          final int previousSize,
          final SszPrimitiveSchema<DataT, SszDataT> primitiveSchema) {

    final int previousTotalChunks = (previousSize + elementsPerChunk - 1) / elementsPerChunk;
    final int previousMaxLevel =
        previousTotalChunks > 0 ? ProgressiveTreeUtil.levelForIndex(previousTotalChunks - 1) : -1;

    // Group by chunk index
    final Int2ObjectMap<List<SszPrimitiveSchema.PackedNodeUpdate<DataT, SszDataT>>> grouped =
        new Int2ObjectOpenHashMap<>();
    for (Map.Entry<Integer, SszDataT> entry : changes) {
      final int chunkIndex = entry.getKey() / elementsPerChunk;
      final int internalIndex = entry.getKey() % elementsPerChunk;
      grouped
          .computeIfAbsent(chunkIndex, k -> new ArrayList<>())
          .add(new SszPrimitiveSchema.PackedNodeUpdate<>(internalIndex, entry.getValue()));
    }

    // Apply packed updates per chunk
    final Int2ObjectMap<TreeNode> chunkUpdates = new Int2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<List<SszPrimitiveSchema.PackedNodeUpdate<DataT, SszDataT>>>
        chunkEntry : grouped.int2ObjectEntrySet()) {
      final int chunkIndex = chunkEntry.getIntKey();
      final List<SszPrimitiveSchema.PackedNodeUpdate<DataT, SszDataT>> packedUpdates =
          chunkEntry.getValue();

      TreeNode originalChunk;
      final int chunkLevel = ProgressiveTreeUtil.levelForIndex(chunkIndex);
      if (chunkLevel <= previousMaxLevel && packedUpdates.size() < elementsPerChunk) {
        final long chunkGIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(chunkIndex);
        originalChunk = dataTree.get(chunkGIdx);
      } else {
        originalChunk = LeafNode.EMPTY_LEAF;
      }

      chunkUpdates.put(chunkIndex, primitiveSchema.updatePackedNode(originalChunk, packedUpdates));
    }

    return chunkUpdates;
  }

  /**
   * Replaces the length node (right child) while preserving the data tree (left child).
   *
   * @param root the current list/bitlist root node
   * @param newSize the new size to encode
   * @return updated root node with new length
   */
  public static TreeNode updateSize(final TreeNode root, final int newSize) {
    return BranchNode.create(root.get(GIndexUtil.LEFT_CHILD_G_INDEX), createSizeNode(newSize));
  }

  public static TreeNode createSizeNode(final int size) {
    return SszUInt64.of(UInt64.fromLongBits(size)).getBackingNode();
  }
}

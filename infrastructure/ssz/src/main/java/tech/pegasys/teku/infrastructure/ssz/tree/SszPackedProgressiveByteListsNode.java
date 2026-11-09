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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

/// Backs an entire `ProgressiveList[ProgressiveByteList]` data subtree with its serialized SSZ
/// bytes (EIP-7916 progressive merkleization; the wire format is identical to a fixed list).
///
/// Same design as [SszPackedByteListsNode] with progressive shape math: the data tree is the
/// right-leaning level spine (level L holds 4^L element slots in a left-filled balanced subtree
/// of depth 2L), the tail past the last populated level is [LeafNode#EMPTY_LEAF], and empty
/// slots inside a partially filled level are the matching [TreeUtil#ZERO_TREES] subtree.
/// Element roots are computed by progressive chunk merkleization mixed with the element length.
///
/// Both `hashTreeRoot` overloads share the streaming implementation (the no-arg default
/// dispatches virtually to [#hashTreeRoot(Sha256)]) and never materialize elements. Navigation
/// is virtual; updates decay to the fully materialized progressive tree.
public class SszPackedProgressiveByteListsNode implements BranchNode {

  private final Bytes sszBytes;
  private final int[] elementOffsets;
  private final Function<Bytes, TreeNode> elementMaterializer;
  private volatile Bytes32 cachedHash = null;

  public SszPackedProgressiveByteListsNode(
      final Bytes sszBytes,
      final int[] elementOffsets,
      final Function<Bytes, TreeNode> elementMaterializer) {
    checkArgument(
        elementOffsets.length >= 2 && elementOffsets[elementOffsets.length - 1] == sszBytes.size(),
        "Offsets must include the end sentinel");
    this.sszBytes = sszBytes;
    this.elementOffsets = elementOffsets;
    this.elementMaterializer = elementMaterializer;
  }

  public Bytes getSszBytes() {
    return sszBytes;
  }

  public int getElementCount() {
    return elementOffsets.length - 1;
  }

  private Bytes elementSsz(final int index) {
    return sszBytes.slice(elementOffsets[index], elementOffsets[index + 1] - elementOffsets[index]);
  }

  private TreeNode materializeElement(final int index) {
    return elementMaterializer.apply(elementSsz(index));
  }

  /** The fully materialized equivalent progressive data tree; used by the update decay path. */
  public TreeNode materialize() {
    final List<TreeNode> elements = new ArrayList<>(getElementCount());
    for (int i = 0; i < getElementCount(); i++) {
      elements.add(materializeElement(i));
    }
    return ProgressiveTreeUtil.createProgressiveTree(elements);
  }

  private static long levelStartSlot(final int level) {
    return level == 0 ? 0 : ProgressiveTreeUtil.cumulativeCapacity(level - 1);
  }

  private TreeNode levelNode(final int level) {
    final long start = levelStartSlot(level);
    final int depth = ProgressiveTreeUtil.levelDepth(level);
    return rangeNode(start, depth);
  }

  private TreeNode spineNode(final int level) {
    if (levelStartSlot(level) >= getElementCount()) {
      return LeafNode.EMPTY_LEAF;
    }
    return new VirtualSpineNode(level);
  }

  private TreeNode rangeNode(final long firstSlot, final int depth) {
    if (firstSlot >= getElementCount()) {
      return TreeUtil.ZERO_TREES[depth];
    }
    if (depth == 0) {
      return materializeElement((int) firstSlot);
    }
    return new VirtualRangeNode(firstSlot, depth);
  }

  @Override
  @NotNull
  public TreeNode left() {
    return levelNode(0);
  }

  @Override
  @NotNull
  public TreeNode right() {
    return spineNode(1);
  }

  @Override
  public BranchNode rebind(final boolean left, final TreeNode newNode) {
    return left ? BranchNode.create(newNode, right()) : BranchNode.create(left(), newNode);
  }

  @Override
  public TreeNode updated(final TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    } else {
      return materialize().updated(newNodes);
    }
  }

  @Override
  public TreeNode updated(
      final long generalizedIndex, final Function<TreeNode, TreeNode> nodeUpdater) {
    if (GIndexUtil.gIdxIsSelf(generalizedIndex)) {
      return nodeUpdater.apply(this);
    }
    return materialize().updated(generalizedIndex, nodeUpdater);
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = BranchNode.super.hashTreeRoot();
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public Bytes32 hashTreeRoot(final Sha256 sha256) {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = spineRoot(sha256, 0);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  private Bytes32 spineRoot(final Sha256 sha256, final int level) {
    if (levelStartSlot(level) >= getElementCount()) {
      return LeafNode.EMPTY_LEAF.hashTreeRoot();
    }
    final Bytes32 levelRoot =
        rangeRoot(sha256, levelStartSlot(level), ProgressiveTreeUtil.levelDepth(level));
    final Bytes32 restRoot = spineRoot(sha256, level + 1);
    return Bytes32.wrap(sha256.digest(levelRoot, restRoot));
  }

  private Bytes32 rangeRoot(final Sha256 sha256, final long firstSlot, final int depth) {
    if (firstSlot >= getElementCount()) {
      return TreeUtil.ZERO_TREES[depth].hashTreeRoot();
    }
    if (depth == 0) {
      return elementRoot(sha256, (int) firstSlot);
    }
    final Bytes32 leftRoot = rangeRoot(sha256, firstSlot, depth - 1);
    final Bytes32 rightRoot = rangeRoot(sha256, firstSlot + (1L << (depth - 1)), depth - 1);
    return Bytes32.wrap(sha256.digest(leftRoot, rightRoot));
  }

  private Bytes32 elementRoot(final Sha256 sha256, final int index) {
    final Bytes data = elementSsz(index);
    final Bytes32 dataRoot = progressiveChunksRoot(sha256, data, 0);
    final Bytes32 lengthRoot =
        Bytes32.rightPad(Bytes.ofUnsignedLong(data.size(), ByteOrder.LITTLE_ENDIAN));
    return Bytes32.wrap(sha256.digest(dataRoot, lengthRoot));
  }

  private static Bytes32 progressiveChunksRoot(
      final Sha256 sha256, final Bytes data, final int level) {
    final long levelStartChunk = levelStartSlot(level);
    if (levelStartChunk * LeafNode.MAX_BYTE_SIZE >= data.size()) {
      return LeafNode.EMPTY_LEAF.hashTreeRoot();
    }
    final Bytes32 levelRoot =
        SszPackedByteListsNode.byteChunksRoot(
            sha256, data, (int) levelStartChunk, ProgressiveTreeUtil.levelDepth(level));
    final Bytes32 restRoot = progressiveChunksRoot(sha256, data, level + 1);
    return Bytes32.wrap(sha256.digest(levelRoot, restRoot));
  }

  @Override
  public String toString() {
    return "PackedProgressiveByteLists["
        + getElementCount()
        + " elements, "
        + sszBytes.size()
        + " bytes]";
  }

  /// Lazily materialized spine node at `level` (left = the level's balanced subtree, right =
  /// the next spine node or [LeafNode#EMPTY_LEAF]). Stateless; roots recomputed per call.
  private class VirtualSpineNode implements BranchNode {
    private final int level;

    private VirtualSpineNode(final int level) {
      this.level = level;
    }

    @Override
    @NotNull
    public TreeNode left() {
      return levelNode(level);
    }

    @Override
    @NotNull
    public TreeNode right() {
      return spineNode(level + 1);
    }

    @Override
    public BranchNode rebind(final boolean left, final TreeNode newNode) {
      return left ? BranchNode.create(newNode, right()) : BranchNode.create(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot(final Sha256 sha256) {
      return spineRoot(sha256, level);
    }

    @Override
    public String toString() {
      return "PackedProgressiveSpine[level " + level + "]";
    }
  }

  /// Lazily materialized balanced branch covering element slots
  /// `[firstSlot, firstSlot + 2^nodeDepth)` within a level. Stateless.
  private class VirtualRangeNode implements BranchNode {
    private final long firstSlot;
    private final int nodeDepth;

    private VirtualRangeNode(final long firstSlot, final int nodeDepth) {
      this.firstSlot = firstSlot;
      this.nodeDepth = nodeDepth;
    }

    @Override
    @NotNull
    public TreeNode left() {
      return rangeNode(firstSlot, nodeDepth - 1);
    }

    @Override
    @NotNull
    public TreeNode right() {
      return rangeNode(firstSlot + (1L << (nodeDepth - 1)), nodeDepth - 1);
    }

    @Override
    public BranchNode rebind(final boolean left, final TreeNode newNode) {
      return left ? BranchNode.create(newNode, right()) : BranchNode.create(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot(final Sha256 sha256) {
      return rangeRoot(sha256, firstSlot, nodeDepth);
    }

    @Override
    public String toString() {
      return "PackedProgressiveRange[slot " + firstSlot + ", depth " + nodeDepth + "]";
    }
  }
}

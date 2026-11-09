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

/// Backs an entire `List[ByteList[N], M]` vector subtree with its serialized SSZ bytes.
///
/// Instead of materializing per-element trees, this node retains the list's SSZ variable part
/// (offset table + element data, typically a zero-copy slice of the incoming buffer) and computes
/// `hashTreeRoot` by streaming merkleization with O(depth) transient memory, caching a single
/// root. Retaining `sszBytes` pins the enclosing buffer slice; acceptable because the enclosing
/// structure retains sibling slices anyway.
///
/// Navigation is virtual: `left()`/`right()` return lightweight range wrappers, element subtrees
/// are materialized on demand via the supplied materializer (and come out spine-compressed).
/// Both `updated` overloads decay this node into the fully materialized equivalent and apply the
/// update there — the cold path by design.
public class SszPackedByteListsNode implements BranchNode {

  private final Bytes sszBytes;
  private final int[] elementOffsets;
  private final int elementDataDepth;
  private final int depth;
  private final Function<Bytes, TreeNode> elementMaterializer;
  private volatile Bytes32 cachedHash = null;

  public SszPackedByteListsNode(
      final Bytes sszBytes,
      final int[] elementOffsets,
      final int elementDataDepth,
      final int depth,
      final Function<Bytes, TreeNode> elementMaterializer) {
    checkArgument(depth >= 1 && depth < TreeUtil.ZERO_TREES.length, "Invalid depth: %s", depth);
    checkArgument(
        elementDataDepth >= 0 && elementDataDepth < TreeUtil.ZERO_TREES.length,
        "Invalid elementDataDepth: %s",
        elementDataDepth);
    checkArgument(
        elementOffsets.length >= 2 && elementOffsets[elementOffsets.length - 1] == sszBytes.size(),
        "Offsets must include the end sentinel");
    checkArgument(
        elementOffsets.length - 1 <= 1L << depth, "More elements than the tree depth allows");
    this.sszBytes = sszBytes;
    this.elementOffsets = elementOffsets;
    this.elementDataDepth = elementDataDepth;
    this.depth = depth;
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

  /** The fully materialized equivalent tree (spine-compressed); used by the update decay path. */
  public TreeNode materialize() {
    final List<TreeNode> elements = new ArrayList<>(getElementCount());
    for (int i = 0; i < getElementCount(); i++) {
      elements.add(materializeElement(i));
    }
    return TreeUtil.createTree(elements, depth);
  }

  private TreeNode childNode(final long firstSlot, final int childDepth) {
    if (firstSlot >= getElementCount()) {
      return TreeUtil.ZERO_TREES[childDepth];
    }
    if (childDepth == 0) {
      return materializeElement((int) firstSlot);
    }
    return new VirtualBranchNode(firstSlot, childDepth);
  }

  @Override
  @NotNull
  public TreeNode left() {
    return childNode(0, depth - 1);
  }

  @Override
  @NotNull
  public TreeNode right() {
    return childNode(1L << (depth - 1), depth - 1);
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
      cachedHash = subtreeRoot(sha256, 0, depth);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  private Bytes32 subtreeRoot(final Sha256 sha256, final long firstSlot, final int subtreeDepth) {
    if (firstSlot >= getElementCount()) {
      return TreeUtil.ZERO_TREES[subtreeDepth].hashTreeRoot();
    }
    if (subtreeDepth == 0) {
      return elementRoot(sha256, (int) firstSlot);
    }
    final Bytes32 leftRoot = subtreeRoot(sha256, firstSlot, subtreeDepth - 1);
    final Bytes32 rightRoot =
        subtreeRoot(sha256, firstSlot + (1L << (subtreeDepth - 1)), subtreeDepth - 1);
    return Bytes32.wrap(sha256.digest(leftRoot, rightRoot));
  }

  private Bytes32 elementRoot(final Sha256 sha256, final int index) {
    final Bytes data = elementSsz(index);
    final Bytes32 dataRoot = byteChunksRoot(sha256, data, 0, elementDataDepth);
    final Bytes32 lengthRoot =
        Bytes32.rightPad(Bytes.ofUnsignedLong(data.size(), ByteOrder.LITTLE_ENDIAN));
    return Bytes32.wrap(sha256.digest(dataRoot, lengthRoot));
  }

  static Bytes32 byteChunksRoot(
      final Sha256 sha256, final Bytes data, final int firstChunk, final int chunksDepth) {
    final long firstByte = (long) firstChunk * LeafNode.MAX_BYTE_SIZE;
    if (firstByte >= data.size()) {
      return TreeUtil.ZERO_TREES[chunksDepth].hashTreeRoot();
    }
    if (chunksDepth == 0) {
      final int firstByteInt = Math.toIntExact(firstByte);
      return Bytes32.rightPad(
          data.slice(firstByteInt, Math.min(LeafNode.MAX_BYTE_SIZE, data.size() - firstByteInt)));
    }
    final Bytes32 leftRoot = byteChunksRoot(sha256, data, firstChunk, chunksDepth - 1);
    final Bytes32 rightRoot =
        byteChunksRoot(sha256, data, firstChunk + (1 << (chunksDepth - 1)), chunksDepth - 1);
    return Bytes32.wrap(sha256.digest(leftRoot, rightRoot));
  }

  @Override
  public String toString() {
    return "PackedByteLists[" + getElementCount() + " elements, " + sszBytes.size() + " bytes]";
  }

  /// Lazily materialized branch covering element slots `[firstSlot, firstSlot + 2^nodeDepth)`.
  /// Stateless: roots are recomputed per call; anything holding subtree roots long-term should
  /// materialize instead.
  private class VirtualBranchNode implements BranchNode {
    private final long firstSlot;
    private final int nodeDepth;

    private VirtualBranchNode(final long firstSlot, final int nodeDepth) {
      this.firstSlot = firstSlot;
      this.nodeDepth = nodeDepth;
    }

    @Override
    @NotNull
    public TreeNode left() {
      return childNode(firstSlot, nodeDepth - 1);
    }

    @Override
    @NotNull
    public TreeNode right() {
      return childNode(firstSlot + (1L << (nodeDepth - 1)), nodeDepth - 1);
    }

    @Override
    public BranchNode rebind(final boolean left, final TreeNode newNode) {
      return left ? BranchNode.create(newNode, right()) : BranchNode.create(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot(final Sha256 sha256) {
      return subtreeRoot(sha256, firstSlot, nodeDepth);
    }

    @Override
    public String toString() {
      return "PackedByteListsRange[slot " + firstSlot + ", depth " + nodeDepth + "]";
    }
  }
}

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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

/// Compact representation of a subtree of depth `depth` whose only non-zero content is a single
/// child subtree of depth `childDepth` occupying the leftmost position. It is equivalent to a
/// chain of `depth - childDepth` branch nodes where every right child is the zero subtree of the
/// corresponding depth:
///
/// ```
///              o                 <- this node (depth)
///             / \
///            o   Z[depth-1]
///           / \
///         ...  Z[childDepth+1]
///         / \
///     child  Z[childDepth]
/// ```
///
/// This is the typical shape of a sparsely populated list tree — e.g. a short
/// `ByteList[HUGE_LIMIT]` — where materializing the chain would cost one branch node plus one
/// cached root per level. This node stores only the child and computes its root by folding the
/// child root with the precomputed zero subtree roots, caching a single [Bytes32].
///
/// Navigation ([#get(long)], [#iterate(long, long, TreeVisitor)]) is virtual: intermediate spine
/// levels are materialized on demand as lightweight wrappers. Updates decay the touched path into
/// regular branch nodes, matching the previous representation.
public class ZeroPaddedSpineNode implements BranchNode {

  private final TreeNode child;
  private final int childDepth;
  private final int depth;
  private volatile Bytes32 cachedHash = null;

  private ZeroPaddedSpineNode(final TreeNode child, final int childDepth, final int depth) {
    this.child = child;
    this.childDepth = childDepth;
    this.depth = depth;
  }

  /// Creates a node equivalent to the zero-padded chain of branch nodes from `depth` down to a
  /// left-aligned `child` subtree of depth `childDepth`.
  public static TreeNode create(final TreeNode child, final int childDepth, final int depth) {
    checkArgument(
        childDepth >= 0 && depth > childDepth && depth < TreeUtil.ZERO_TREES.length,
        "Invalid spine depths: childDepth=%s, depth=%s",
        childDepth,
        depth);
    return new ZeroPaddedSpineNode(child, childDepth, depth);
  }

  @Override
  @NotNull
  public TreeNode left() {
    return depth - 1 == childDepth ? child : new ZeroPaddedSpineNode(child, childDepth, depth - 1);
  }

  @Override
  @NotNull
  public TreeNode right() {
    return TreeUtil.ZERO_TREES[depth - 1];
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
      final Pair<TreeUpdates, TreeUpdates> children = newNodes.splitAtPivot();
      return BranchNode.create(
          left().updated(children.getLeft()), right().updated(children.getRight()));
    }
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
      Bytes32 root = child.hashTreeRoot(sha256);
      for (int level = childDepth; level < depth; level++) {
        root = Bytes32.wrap(sha256.digest(root, TreeUtil.ZERO_TREES[level].hashTreeRoot()));
      }
      cachedHash = root;
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public String toString() {
    return "(" + child + " +ZeroPaddedSpine-" + (depth - childDepth) + ")";
  }
}

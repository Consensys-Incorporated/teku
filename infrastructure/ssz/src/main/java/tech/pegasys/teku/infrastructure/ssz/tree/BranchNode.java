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
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;

/**
 * Branch node of a tree. This node type corresponds to the 'Commit' node in the spec:
 * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
 */
public interface BranchNode extends TreeNode {

  /**
   * Creates a basic binary Branch node with left and right child
   *
   * @param left Non-null left child
   * @param right Non-null right child
   */
  static BranchNode create(final TreeNode left, final TreeNode right) {
    checkNotNull(left);
    checkNotNull(right);
    return new SimpleBranchNode(left, right);
  }

  /**
   * Returns left child node. It can be either a default or non-default node. Note that both left
   * and right child may be the same default instance
   */
  @NotNull
  TreeNode left();

  /**
   * Returns right child node. It can be either a default or non-default node. Note that both left
   * and right child may be the same default instance
   */
  @NotNull
  TreeNode right();

  /**
   * Rebind 'sets' a new left/right child of this node. Rebind doesn't modify this instance but
   * creates and returns a new one which contains a new assigned and old unmodified child
   */
  BranchNode rebind(boolean left, TreeNode newNode);

  @Override
  default Bytes32 hashTreeRoot(final Sha256 sha256) {
    final Bytes32 leftRoot = left().hashTreeRoot(sha256);
    final Bytes32 rightRoot = right().hashTreeRoot(sha256);
    return Bytes32.wrap(sha256.digest(leftRoot, rightRoot));
  }

  @NotNull
  @Override
  default TreeNode get(final long target) {
    checkArgument(target >= 1, "Invalid index: %s", target);
    if (GIndexUtil.gIdxIsSelf(target)) {
      return this;
    } else {
      long relativeGIndex = GIndexUtil.gIdxGetRelativeGIndex(target, 1);
      return GIndexUtil.gIdxGetChildIndex(target, 1) == 0
          ? left().get(relativeGIndex)
          : right().get(relativeGIndex);
    }
  }

  @Override
  default boolean iterate(
      final long thisGeneralizedIndex,
      final long startGeneralizedIndex,
      final TreeVisitor visitor) {

    if (GIndexUtil.gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.LEFT) {
      return true;
    } else {
      return visitor.visit(this, thisGeneralizedIndex)
          && left()
              .iterate(
                  GIndexUtil.gIdxLeftGIndex(thisGeneralizedIndex), startGeneralizedIndex, visitor)
          && right()
              .iterate(
                  GIndexUtil.gIdxRightGIndex(thisGeneralizedIndex), startGeneralizedIndex, visitor);
    }
  }

  @Override
  default TreeNode updated(final long target, final Function<TreeNode, TreeNode> nodeUpdater) {
    if (GIndexUtil.gIdxIsSelf(target)) {
      return nodeUpdater.apply(this);
    } else {
      long relativeGIndex = GIndexUtil.gIdxGetRelativeGIndex(target, 1);
      if (GIndexUtil.gIdxGetChildIndex(target, 1) == 0) {
        TreeNode newLeftChild = left().updated(relativeGIndex, nodeUpdater);
        return rebind(true, newLeftChild);
      } else {
        TreeNode newRightChild = right().updated(relativeGIndex, nodeUpdater);
        return rebind(false, newRightChild);
      }
    }
  }
}

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

import static java.util.Collections.singletonList;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

/**
 * Basic interface for Backing Tree node Backing Binary Tree concept for SSZ structures is described
 * here: https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#tree
 *
 * <p>Tree node is immutable by design. Any update on a tree creates new nodes which refer both new
 * data nodes and old unmodified nodes
 */
public interface TreeNode {

  /**
   * Calculates (if necessary) and returns `hash_tree_root` of this tree node. Worth to mention that
   * `hash_tree_root` of a {@link LeafNode} is the node {@link Bytes32} content
   */
  default Bytes32 hashTreeRoot() {
    final Sha256 sha256 = Hash.getSha256Instance();
    return hashTreeRoot(sha256);
  }

  Bytes32 hashTreeRoot(Sha256 sha256);

  /**
   * Gets this node descendant by its 'generalized index'
   *
   * @param generalizedIndex generalized index of a tree is specified here:
   *     https://github.com/ethereum/consensus-specs/blob/2787fea5feb8d5977ebee7c578c5d835cff6dc21/specs/light_client/merkle_proofs.md#generalized-merkle-tree-index
   * @return node descendant
   * @throws IllegalArgumentException if no node exists for the passed generalized index
   */
  @NotNull
  TreeNode get(long generalizedIndex);

  /**
   * Iterates recursively this node children (including the node itself) in the order Self -> Left
   * subtree -> Right subtree
   *
   * <p>This method can be considered low-level and mostly intended as a single implementation point
   * for subclasses. Consider using higher-level methods {@link #iterateRange(long, long,
   * TreeVisitor)}, {@link #iterateAll(TreeVisitor)} and {@link #iterateAll(Consumer)}
   *
   * @param thisGeneralizedIndex the generalized index of this node or {@link
   *     GIndexUtil#SELF_G_INDEX} if this node is considered the root. {@link
   *     TreeVisitor#visit(TreeNode, long)} index will be calculated with respect to this parameter
   * @param startGeneralizedIndex The generalized index to start iteration from. All tree
   *     predecessor and successor nodes of a node at this index will be visited. All nodes 'to the
   *     left' of start node are to be skipped. The index may point to a non-existing node, in this
   *     case the nearest existing predecessor node would be the starting node To start iteration
   *     from the leftmost node use {@link GIndexUtil#LEFTMOST_G_INDEX}
   * @param visitor Callback for nodes. When visitor returns false, iteration breaks
   * @return true if the iteration should proceed or false to break iteration
   */
  boolean iterate(long thisGeneralizedIndex, long startGeneralizedIndex, TreeVisitor visitor);

  /**
   * Iterates all nodes between and including startGeneralizedIndex and endGeneralizedIndexInclusive
   * in order Self -> Left subtree -> Right subtree
   *
   * <p>All tree predecessor and successor nodes of startGeneralizedIndex and
   * endGeneralizedIndexInclusive nodes will be visited. All nodes 'to the left' of the start node
   * and 'to the right' of the end node are to be skipped. An index may point to a non-existing
   * node, in this case the nearest existing predecessor node would be considered the starting node.
   *
   * <p>To start iteration from the leftmost node specify startGeneralizedIndex equal to {@link
   * GIndexUtil#LEFTMOST_G_INDEX} To iteration till the rightmost node specify
   * endGeneralizedIndexInclusive equal to {@link GIndexUtil#RIGHTMOST_G_INDEX}
   */
  default void iterateRange(
      final long startGeneralizedIndex,
      final long endGeneralizedIndexInclusive,
      final TreeVisitor visitor) {
    iterate(
        GIndexUtil.SELF_G_INDEX,
        startGeneralizedIndex,
        TillIndexVisitor.create(visitor, endGeneralizedIndexInclusive));
  }

  /** Iterates all tree nodes in the order Self -> Left subtree -> Right subtree */
  default void iterateAll(final TreeVisitor visitor) {
    iterate(GIndexUtil.SELF_G_INDEX, GIndexUtil.LEFTMOST_G_INDEX, visitor);
  }

  /** Iterates all tree nodes in the order Self -> Left subtree -> Right subtree */
  default void iterateAll(final Consumer<TreeNode> simpleVisitor) {
    iterateAll(
        (node, __) -> {
          simpleVisitor.accept(node);
          return true;
        });
  }

  /**
   * The same as {@link #updated(long, TreeNode)} except that existing node can be used to calculate
   * a new node
   *
   * <p>Three method overloads call each other in a cycle. The implementation class should override
   * one of them and may override more for efficiency
   *
   * @see #updated(TreeUpdates)
   * @see #updated(long, TreeNode)
   * @see #updated(long, Function)
   */
  default TreeNode updated(
      final long generalizedIndex, final Function<TreeNode, TreeNode> nodeUpdater) {
    TreeNode newNode = nodeUpdater.apply(get(generalizedIndex));
    return updated(
        new TreeUpdates(singletonList(new TreeUpdates.Update(generalizedIndex, newNode))));
  }

  /**
   * Updates the tree in a batch.
   *
   * <p>Three method overloads call each other in a cycle. The implementation class should override
   * one of them and may override more for efficiency
   *
   * @see #updated(TreeUpdates)
   * @see #updated(long, TreeNode)
   * @see #updated(long, Function)
   */
  default TreeNode updated(final TreeUpdates newNodes) {
    TreeNode ret = this;
    for (int i = 0; i < newNodes.size(); i++) {
      ret = ret.updated(newNodes.getRelativeGIndex(i), newNodes.getNode(i));
    }
    return ret;
  }

  /**
   * 'Sets' a new node on place of the node at generalized index. This node and all its descendants
   * are left immutable. The updated subtree node is returned.
   *
   * <p>Three method overloads call each other in a cycle. The implementation class should override
   * one of them and may override more for efficiency
   *
   * @param generalizedIndex index of tree node to be replaced
   * @param node new node either leaf of subtree root node
   * @return the updated subtree root node
   * @see #updated(TreeUpdates)
   * @see #updated(long, TreeNode)
   * @see #updated(long, Function)
   */
  default TreeNode updated(final long generalizedIndex, final TreeNode node) {
    return updated(generalizedIndex, oldNode -> node);
  }
}

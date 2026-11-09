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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation.PREDECESSOR;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxCompose;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

public class StoringUtil {

  /**
   * Store nodes from {@code rootNode}, treating nodes at {@code childDepth} as leaf nodes.
   *
   * <p>It is expected that {@code nodeStore} will then continue iterating the leaf nodes using the
   * appropriate child schema.
   *
   * @param nodeStore the {@link TreeNodeStore} to store node data in
   * @param maxBranchLevelsSkipped the maximum number of levels to skip between stored branch nodes
   * @param rootNode the root node to store
   * @param rootGIndex the GIndex of the root node in the overall node tree
   * @param childDepth the depth of child nodes from the root node
   * @param lastUsefulGIndex the GIndex of the right most node to store
   */
  public static void storeNodesToDepth(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int childDepth,
      final long lastUsefulGIndex,
      final TargetDepthNodeHandler targetDepthNodeHandler) {
    checkArgument(childDepth > 0, "Depth must be positive");
    if (nodeStore.canSkipBranch(rootNode.hashTreeRoot(), rootGIndex)) {
      return;
    }
    if (childDepth <= maxBranchLevelsSkipped) {
      visitChildNodesAtDepth(
          nodeStore, rootNode, rootGIndex, childDepth, lastUsefulGIndex, targetDepthNodeHandler);
    } else {
      // Max compression depth exceeded so will need to record some interim branch nodes
      storeIntermediateBranches(
          nodeStore,
          maxBranchLevelsSkipped,
          rootNode,
          rootGIndex,
          childDepth,
          lastUsefulGIndex,
          targetDepthNodeHandler);
    }
  }

  private static void storeIntermediateBranches(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int childDepth,
      final long lastUsefulGIndex,
      final TargetDepthNodeHandler targetDepthNodeHandler) {
    final int remainingDepth = childDepth - maxBranchLevelsSkipped;
    final int childCount =
        getUsefulChildCountAtBranchChildDepth(
            rootGIndex, maxBranchLevelsSkipped, remainingDepth, lastUsefulGIndex);
    final Bytes32[] childRoots = new Bytes32[childCount];
    for (int childIndex = 0; childIndex < childCount; childIndex++) {
      final long childRelativeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, maxBranchLevelsSkipped);
      final long childGIndex = gIdxCompose(rootGIndex, childRelativeGIndex);

      final TreeNode childNode = rootNode.get(childRelativeGIndex);
      childRoots[childIndex] = childNode.hashTreeRoot();
      storeNodesToDepth(
          nodeStore,
          maxBranchLevelsSkipped,
          childNode,
          childGIndex,
          remainingDepth,
          lastUsefulGIndex,
          targetDepthNodeHandler);
    }
    nodeStore.storeBranchNode(
        rootNode.hashTreeRoot(), rootGIndex, maxBranchLevelsSkipped, childRoots);
  }

  private static void visitChildNodesAtDepth(
      final TreeNodeStore nodeVisitor,
      final TreeNode rootNode,
      final long rootGIndex,
      final int childDepth,
      final long lastUsefulGIndex,
      final TargetDepthNodeHandler targetDepthNodeHandler) {
    final int childCount = getUsefulChildCount(rootGIndex, childDepth, lastUsefulGIndex);
    final Bytes32[] childRoots = new Bytes32[childCount];
    for (int childIndex = 0; childIndex < childCount; childIndex++) {
      final long childRelativeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, childDepth);
      final TreeNode childNode = rootNode.get(childRelativeGIndex);
      childRoots[childIndex] = childNode.hashTreeRoot();
      targetDepthNodeHandler.storeTargetDepthNode(
          childNode, gIdxCompose(rootGIndex, childRelativeGIndex));
    }
    nodeVisitor.storeBranchNode(rootNode.hashTreeRoot(), rootGIndex, childDepth, childRoots);
  }

  private static int getUsefulChildCountAtBranchChildDepth(
      final long rootGIndex,
      final int depthFromRoot,
      final int depthFromLastUsefulGIndex,
      final long lastUsefulGIndex) {
    // Find parent GIndex of lastUsefulGIndex at depth to be stored
    final long lastUsefulGIndexAtTargetDepth = lastUsefulGIndex >>> depthFromLastUsefulGIndex;

    return getUsefulChildCount(rootGIndex, depthFromRoot, lastUsefulGIndexAtTargetDepth);
  }

  private static int getUsefulChildCount(
      final long rootGIndex, final int childDepth, final long lastUsefulGIndex) {
    final int childCount;
    if (GIndexUtil.gIdxCompare(rootGIndex, lastUsefulGIndex) == PREDECESSOR) {
      childCount = GIndexUtil.gIdxChildIndexFromGIndex(lastUsefulGIndex, childDepth) + 1;
    } else {
      childCount = Math.toIntExact(1L << childDepth);
    }
    return childCount;
  }

  public interface TargetDepthNodeHandler {
    /**
     * Called when a descendant node at the requested depth is reached.
     *
     * @param node the node at the target depth
     * @param gIndex the generalized index of the node
     */
    void storeTargetDepthNode(TreeNode node, long gIndex);
  }
}

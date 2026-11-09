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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.infrastructure.ssz.tree.LazyBranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class LoadingUtil {

  public static TreeNode loadNodesToDepth(
      final TreeNodeSource nodeSource,
      final Bytes32 rootHash,
      final long rootGIndex,
      final int depthToLoad,
      final TreeNode defaultTree,
      final long lastUsefulGIndex,
      final ChildLoader childLoader) {
    if (depthToLoad == 0) {
      if (GIndexUtil.gIdxCompare(rootGIndex, lastUsefulGIndex) == NodeRelation.RIGHT) {
        // Leaf node is past the last useful node so can just use the default tree
        return defaultTree;
      }
      // Only one child so wrapper is inlined
      return childLoader.loadChild(nodeSource, rootHash, rootGIndex);
    }

    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash)) {
      // Zero branch, but it may be "useful" and need to ensure leaf data has the right lengths
      // or it may be "useless" and we can just use the default tree
      if (isZeroBranchUseful(rootGIndex, lastUsefulGIndex)) {
        return createUsefulEmptyBranch(
            nodeSource, rootGIndex, depthToLoad, defaultTree, lastUsefulGIndex, childLoader);
      }
      return defaultTree;
    }
    final CompressedBranchInfo rootBranchInfo = nodeSource.loadBranchNode(rootHash, rootGIndex);
    final int branchDepth = rootBranchInfo.getDepth();
    checkState(
        branchDepth <= depthToLoad,
        "Stored branch node %s crosses schema boundary. Stored depth %s, max allowed depth %s",
        rootHash,
        branchDepth,
        depthToLoad);
    final Bytes32[] childHashes = rootBranchInfo.getChildren();
    final List<TreeNode> children = new ArrayList<>(childHashes.length);

    // Walk through child hashes in pairs and create the branch nodes for the level above them
    final int buildNodesAtDepth = rootBranchInfo.getDepth() - 1;
    int childIndex = 0;
    while (childIndex < childHashes.length) {
      final long branchNodeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex / 2, buildNodesAtDepth);
      final long composedBranchNodeGIndex = GIndexUtil.gIdxCompose(rootGIndex, branchNodeGIndex);
      final Bytes32 leftHash = childHashes[childIndex];
      final Bytes32 rightHash;
      if (childIndex + 1 < childHashes.length) {
        rightHash = childHashes[childIndex + 1];
      } else {
        rightHash =
            defaultTree
                .get(
                    GIndexUtil.gIdxChildGIndex(
                        GIndexUtil.SELF_G_INDEX, childIndex + 1, branchDepth))
                .hashTreeRoot();
      }
      children.add(
          LazyBranchNode.createWithUnknownHash(
              leftHash,
              rightHash,
              () ->
                  loadNodesToDepth(
                      nodeSource,
                      leftHash,
                      GIndexUtil.gIdxLeftGIndex(composedBranchNodeGIndex),
                      depthToLoad - branchDepth,
                      defaultTree.get(GIndexUtil.gIdxLeftGIndex(branchNodeGIndex)),
                      lastUsefulGIndex,
                      childLoader),
              () ->
                  loadNodesToDepth(
                      nodeSource,
                      rightHash,
                      GIndexUtil.gIdxRightGIndex(composedBranchNodeGIndex),
                      depthToLoad - branchDepth,
                      defaultTree.get(GIndexUtil.gIdxRightGIndex(branchNodeGIndex)),
                      lastUsefulGIndex,
                      childLoader)));
      childIndex += 2;
    }

    // Add default nodes for any remaining children, again using branch nodes one level up
    final int totalChildCount = Math.toIntExact(1L << branchDepth);
    while (childIndex < totalChildCount) {
      children.add(
          defaultTree.get(
              GIndexUtil.gIdxChildGIndex(
                  GIndexUtil.SELF_G_INDEX, childIndex / 2, buildNodesAtDepth)));
      childIndex += 2;
    }
    return TreeUtil.createTree(children, buildNodesAtDepth);
  }

  private static boolean isZeroBranchUseful(final long rootGIndex, final long lastUsefulGIndex) {
    final NodeRelation relationRootToLastUseful =
        GIndexUtil.gIdxCompare(rootGIndex, lastUsefulGIndex);
    return switch (relationRootToLastUseful) {
      case LEFT, PREDECESSOR -> true;
      case SAME, RIGHT, SUCCESSOR -> false;
      default ->
          throw new IllegalStateException("Unknown relation type: " + relationRootToLastUseful);
    };
  }

  private static BranchNode createUsefulEmptyBranch(
      final TreeNodeSource nodeSource,
      final long rootGIndex,
      final int depthToLoad,
      final TreeNode defaultTree,
      final long lastUsefulGIndex,
      final ChildLoader childLoader) {
    final TreeNode defaultLeftNode = defaultTree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final TreeNode leftNode =
        loadNodesToDepth(
            nodeSource,
            defaultLeftNode.hashTreeRoot(),
            GIndexUtil.gIdxLeftGIndex(rootGIndex),
            depthToLoad - 1,
            defaultLeftNode,
            lastUsefulGIndex,
            childLoader);
    final TreeNode defaultRightNode = defaultTree.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
    final TreeNode rightNode =
        loadNodesToDepth(
            nodeSource,
            defaultRightNode.hashTreeRoot(),
            GIndexUtil.gIdxRightGIndex(rootGIndex),
            depthToLoad - 1,
            defaultRightNode,
            lastUsefulGIndex,
            childLoader);
    return BranchNode.create(leftNode, rightNode);
  }

  static TreeNode loadCollectionChild(
      final TreeNodeSource childNodeSource,
      final Bytes32 childHash,
      final long childGIndex,
      final int length,
      final int elementsPerChunk,
      final int treeDepth,
      final SszSchema<?> elementSchema) {
    if (elementSchema.isPrimitive()) {
      final Bytes data = childNodeSource.loadLeafNode(childHash, childGIndex);
      if (data.size() > Bytes32.SIZE) {
        return LeafNode.create(data);
      } else {
        // Potentially need to trim the data
        final int fullNodeCount = length / elementsPerChunk;
        int lastNodeElementCount = length % elementsPerChunk;
        if (lastNodeElementCount == 0) {
          return createLeaf(data);
        }
        final long lastNodeGIndex =
            GIndexUtil.gIdxChildGIndex(childGIndex >>> treeDepth, fullNodeCount, treeDepth);
        if (lastNodeGIndex != childGIndex) {
          return createLeaf(data);
        }
        // Need to trim the data
        final int bitsSize = ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize();
        int lastNodeSizeBytes = bitsCeilToBytes(lastNodeElementCount * bitsSize);
        return createLeaf(data.slice(0, lastNodeSizeBytes));
      }
    } else {
      return elementSchema.loadBackingNodes(childNodeSource, childHash, childGIndex);
    }
  }

  static LeafNode createLeaf(final Bytes data) {
    if (data.size() < Bytes32.SIZE && data.isZero()) {
      return LeafNode.ZERO_LEAVES[data.size()];
    } else {
      return LeafNode.create(data);
    }
  }

  public interface ChildLoader {
    TreeNode loadChild(TreeNodeSource nodeSource, Bytes32 childHash, long childGIndex);
  }
}

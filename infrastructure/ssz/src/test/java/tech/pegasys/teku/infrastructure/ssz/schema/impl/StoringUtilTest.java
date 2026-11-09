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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.SELF_G_INDEX;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationMode;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.StoringUtil.TargetDepthNodeHandler;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

class StoringUtilTest {

  private static final int UNLIMITED_SKIP = Integer.MAX_VALUE;
  private final TreeNodeStore nodeStore = mock(TreeNodeStore.class);
  private final TargetDepthNodeHandler childHandler = mock(TargetDepthNodeHandler.class);
  private final InOrder inOrder = inOrder(nodeStore, childHandler);

  @BeforeEach
  void setUp() {
    when(nodeStore.canSkipBranch(any(), anyLong())).thenReturn(false);
  }

  @Test
  void shouldStoreBothChildrenOfBranchNode() {
    final LeafNode left = LeafNode.create(Bytes.of(1));
    final LeafNode right = LeafNode.create(Bytes.of(2));
    final TreeNode rootNode = TreeUtil.createTree(List.of(left, right));
    StoringUtil.storeNodesToDepth(
        nodeStore, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, 1, RIGHTMOST_G_INDEX, childHandler);
    inOrder.verify(childHandler).storeTargetDepthNode(left, GIndexUtil.LEFT_CHILD_G_INDEX);
    inOrder.verify(childHandler).storeTargetDepthNode(right, GIndexUtil.RIGHT_CHILD_G_INDEX);
    inOrder
        .verify(nodeStore)
        .storeBranchNode(rootNode.hashTreeRoot(), SELF_G_INDEX, 1, roots(left, right));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStoreChildrenAtSpecifiedDepth() {
    final int depth = 5;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    StoringUtil.storeNodesToDepth(
        nodeStore, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, depth, RIGHTMOST_G_INDEX, childHandler);

    verifyChildrenStored(children, depth);
    inOrder
        .verify(nodeStore)
        .storeBranchNode(rootNode.hashTreeRoot(), SELF_G_INDEX, depth, roots(children));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldReportGeneralisedIndexCorrectlyWhenRootNodeIsNotSelfIndex() {
    final int rootGIndex = 12;
    final LeafNode left = LeafNode.create(Bytes.of(1));
    final LeafNode right = LeafNode.create(Bytes.of(2));
    final TreeNode rootNode = TreeUtil.createTree(List.of(left, right));
    StoringUtil.storeNodesToDepth(
        nodeStore, UNLIMITED_SKIP, rootNode, rootGIndex, 1, RIGHTMOST_G_INDEX, childHandler);
    inOrder.verify(childHandler).storeTargetDepthNode(left, GIndexUtil.gIdxLeftGIndex(rootGIndex));
    inOrder
        .verify(childHandler)
        .storeTargetDepthNode(right, GIndexUtil.gIdxRightGIndex(rootGIndex));
    inOrder
        .verify(nodeStore)
        .storeBranchNode(rootNode.hashTreeRoot(), rootGIndex, 1, roots(left, right));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStoreIntermediateBranchesWhenDepthExceedsMaxSkipLevels_evenInterval() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 2;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        rootNode,
        SELF_G_INDEX,
        depth,
        RIGHTMOST_G_INDEX,
        childHandler);

    // Store branch nodes 5 levels deep
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Store children first
      verifyChildrenStored(branchGIndex, branchChildren, maxBranchLevelsSkipped);
      // Then branch node itself
      inOrder
          .verify(nodeStore)
          .storeBranchNode(
              branchNode.hashTreeRoot(),
              branchGIndex,
              maxBranchLevelsSkipped,
              roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(nodeStore)
        .storeBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldStoreIntermediateBranchesWhenDepthExceedsMaxSkipLevels_unevenInterval() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 3;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        rootNode,
        SELF_G_INDEX,
        depth,
        RIGHTMOST_G_INDEX,
        childHandler);

    // Store branch nodes 5 levels deep
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    final int remainingDepth = depth - maxBranchLevelsSkipped;
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Store children first
      verifyChildrenStored(branchGIndex, branchChildren, remainingDepth);
      // Then branch node itself
      inOrder
          .verify(nodeStore)
          .storeBranchNode(
              branchNode.hashTreeRoot(), branchGIndex, remainingDepth, roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(nodeStore)
        .storeBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldNotStoreAnyNodesIfRootCanBeSkipped() {
    final int depth = 5;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    when(nodeStore.canSkipBranch(rootNode.hashTreeRoot(), SELF_G_INDEX)).thenReturn(true);

    StoringUtil.storeNodesToDepth(
        nodeStore, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, depth, RIGHTMOST_G_INDEX, childHandler);

    inOrder.verify(nodeStore).canSkipBranch(rootNode.hashTreeRoot(), SELF_G_INDEX);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldSkipIntermediateBranchesWhenPossible() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 2;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    final IntSet skippableBranchIndices = IntSet.of(0, 2);
    // Can skip intermediate branches 0 and 2, but should store the others
    skippableBranchIndices.forEach(
        index ->
            when(nodeStore.canSkipBranch(
                    branchNodes.get(index).hashTreeRoot(),
                    GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, index, maxBranchLevelsSkipped)))
                .thenReturn(true));

    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        rootNode,
        SELF_G_INDEX,
        depth,
        RIGHTMOST_G_INDEX,
        childHandler);

    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Should skip all nodes under branches 0 and 2
      final VerificationMode verificationMode =
          skippableBranchIndices.contains(i) ? never() : times(1);
      // Store children first
      verifyChildrenStored(verificationMode, branchGIndex, branchChildren, maxBranchLevelsSkipped);
      // Then branch node itself
      inOrder
          .verify(nodeStore, verificationMode)
          .storeBranchNode(
              branchNode.hashTreeRoot(),
              branchGIndex,
              maxBranchLevelsSkipped,
              roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(nodeStore)
        .storeBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldNotStoreUnnecessaryChildNodes_singleBranchLevel() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = Integer.MAX_VALUE;
    final List<TreeNode> children = createChildrenForDepth(depth);

    final TreeNode rootNode = TreeUtil.createTree(children, depth);
    final long rootNodeGIndex = 20;
    final int lastUsefulChildIndex = 5;
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootNodeGIndex, lastUsefulChildIndex, depth);

    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        rootNode,
        rootNodeGIndex,
        depth,
        lastUsefulGIndex,
        childHandler);

    // toIndex is exclusive in subList so + 1 to ensure we include the last useful child
    final List<TreeNode> storedChildren = children.subList(0, lastUsefulChildIndex + 1);
    verifyChildrenStored(rootNodeGIndex, storedChildren, depth);
    verify(childHandler, never()).storeTargetDepthNode(eq(children.get(6)), anyLong());
    verify(nodeStore)
        .storeBranchNode(rootNode.hashTreeRoot(), rootNodeGIndex, depth, roots(storedChildren));
  }

  private List<TreeNode> getNodesAtDepth(final TreeNode rootNode, final int depth) {
    final List<TreeNode> nodes = new ArrayList<>();
    final long childCount = 1L << depth;
    for (long i = 0; i < childCount; i++) {
      final long childGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, depth);
      nodes.add(rootNode.get(childGIndex));
    }
    return nodes;
  }

  private void verifyChildrenStored(final List<TreeNode> children, final int depth) {
    verifyChildrenStored(SELF_G_INDEX, children, depth);
  }

  private void verifyChildrenStored(
      final long rootGIndex, final List<TreeNode> children, final int depth) {
    verifyChildrenStored(times(1), rootGIndex, children, depth);
  }

  private void verifyChildrenStored(
      final VerificationMode verificationMode,
      final long rootGIndex,
      final List<TreeNode> children,
      final int depth) {
    for (int i = 0; i < children.size(); i++) {
      final TreeNode child = children.get(i);
      inOrder
          .verify(childHandler, verificationMode)
          .storeTargetDepthNode(child, GIndexUtil.gIdxChildGIndex(rootGIndex, i, depth));
    }
  }

  private List<TreeNode> createChildrenForDepth(final int depth) {
    final List<TreeNode> children = new ArrayList<>();
    for (int i = 0; i < (int) 1L << depth; i++) {
      children.add(LeafNode.create(Bytes.ofUnsignedInt(i, ByteOrder.BIG_ENDIAN)));
    }
    return children;
  }

  private Bytes32[] roots(final Collection<TreeNode> nodes) {
    return roots(nodes.stream());
  }

  private Bytes32[] roots(final TreeNode... nodes) {
    return roots(Stream.of(nodes));
  }

  private Bytes32[] roots(final Stream<TreeNode> nodes) {
    return nodes.map(TreeNode::hashTreeRoot).toArray(Bytes32[]::new);
  }
}

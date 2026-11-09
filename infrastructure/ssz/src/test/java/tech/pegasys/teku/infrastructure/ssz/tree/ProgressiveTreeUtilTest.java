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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.InMemoryStoringTreeNodeStore;

class ProgressiveTreeUtilTest {

  @Test
  void levelCapacity_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.levelCapacity(0)).isEqualTo(1);
    assertThat(ProgressiveTreeUtil.levelCapacity(1)).isEqualTo(4);
    assertThat(ProgressiveTreeUtil.levelCapacity(2)).isEqualTo(16);
    assertThat(ProgressiveTreeUtil.levelCapacity(3)).isEqualTo(64);
    assertThat(ProgressiveTreeUtil.levelCapacity(4)).isEqualTo(256);
  }

  @Test
  void cumulativeCapacity_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(0)).isEqualTo(1);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(1)).isEqualTo(5);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(2)).isEqualTo(21);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(3)).isEqualTo(85);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(4)).isEqualTo(341);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(5)).isEqualTo(1365);
  }

  @Test
  void levelDepth_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.levelDepth(0)).isEqualTo(0);
    assertThat(ProgressiveTreeUtil.levelDepth(1)).isEqualTo(2);
    assertThat(ProgressiveTreeUtil.levelDepth(2)).isEqualTo(4);
    assertThat(ProgressiveTreeUtil.levelDepth(3)).isEqualTo(6);
  }

  @Test
  void levelForIndex_boundariesForLevels0Through10() {
    // Test first and last index of each level using cumulativeCapacity to compute boundaries.
    // Level L spans indices [cumulativeCapacity(L-1), cumulativeCapacity(L) - 1]
    // (with level 0 starting at index 0).
    for (int level = 0; level <= 10; level++) {
      final long firstIndex = level == 0 ? 0 : ProgressiveTreeUtil.cumulativeCapacity(level - 1);
      final long lastIndex = ProgressiveTreeUtil.cumulativeCapacity(level) - 1;

      assertThat(ProgressiveTreeUtil.levelForIndex(firstIndex))
          .describedAs("first index of level %d", level)
          .isEqualTo(level);
      assertThat(ProgressiveTreeUtil.levelForIndex(lastIndex))
          .describedAs("last index of level %d", level)
          .isEqualTo(level);
    }
  }

  @Test
  void levelForIndex_highLevels() {
    // Verify the formula handles large indices where 3L * elementIndex + 1 exercises
    // the full bit-width of numberOfLeadingZeros.
    for (int level : new int[] {15, 20}) {
      final long firstIndex = ProgressiveTreeUtil.cumulativeCapacity(level - 1);
      final long lastIndex = ProgressiveTreeUtil.cumulativeCapacity(level) - 1;

      assertThat(ProgressiveTreeUtil.levelForIndex(firstIndex))
          .describedAs("first index of level %d", level)
          .isEqualTo(level);
      assertThat(ProgressiveTreeUtil.levelForIndex(lastIndex))
          .describedAs("last index of level %d", level)
          .isEqualTo(level);
    }
  }

  @Test
  void levelForIndex_negativeIndexThrows() {
    assertThatThrownBy(() -> ProgressiveTreeUtil.levelForIndex(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void spineGIndex_matchesIterativeComputation() {
    for (int level = 0; level <= 20; level++) {
      long expected = GIndexUtil.SELF_G_INDEX;
      for (int i = 0; i < level; i++) {
        expected = GIndexUtil.gIdxRightGIndex(expected);
      }
      expected = GIndexUtil.gIdxLeftGIndex(expected);

      assertThat(ProgressiveTreeUtil.spineGIndex(level))
          .describedAs("spineGIndex for level %d", level)
          .isEqualTo(expected);
    }
  }

  @Test
  void createProgressiveTree_emptyChunks_returnsEmptyLeaf() {
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of());
    assertThat(tree).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_singleChunk_createsCorrectTree() {
    final LeafNode leaf = LeafNode.create(Bytes.fromHexString("0x01"));
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(leaf));
    // Should be BranchNode(leaf, EMPTY_LEAF)
    assertThat(tree).isInstanceOf(BranchNode.class);
    final BranchNode branch = (BranchNode) tree;
    assertThat(branch.left()).isEqualTo(leaf);
    assertThat(branch.right()).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_twoChunks_createsCorrectStructure() {
    final LeafNode leaf0 = LeafNode.create(Bytes.fromHexString("0x01"));
    final LeafNode leaf1 = LeafNode.create(Bytes.fromHexString("0x02"));
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(leaf0, leaf1));
    // Level 0 (cap=1): leaf0
    // Level 1 (cap=4): [leaf1, zero, zero, zero] (depth=2 balanced tree)
    // Result: BranchNode(leaf0, BranchNode(level1_tree, EMPTY_LEAF))
    assertThat(tree).isInstanceOf(BranchNode.class);
    final BranchNode root = (BranchNode) tree;
    assertThat(root.left()).isEqualTo(leaf0);
    assertThat(root.right()).isInstanceOf(BranchNode.class);

    final BranchNode rightBranch = (BranchNode) root.right();
    // Left child of right branch is the level-1 balanced subtree with leaf1
    // Right child is EMPTY_LEAF (no more levels)
    assertThat(rightBranch.right()).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_fiveChunks_usesThreeLevels() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(LeafNode.create(Bytes.of((byte) (i + 1))));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    // Level 0 (cap=1): chunks[0]
    // Level 1 (cap=4): chunks[1..4]
    // Level 2 (cap=16): chunks[5..] -> empty -> not created, just EMPTY_LEAF as right
    // Structure: BranchNode(chunk0, BranchNode(level1_tree, EMPTY_LEAF))
    assertThat(tree).isInstanceOf(BranchNode.class);
  }

  @Test
  void getElementGeneralizedIndex_element0_isLeftChild() {
    final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(0);
    // Element 0 is at level 0, which is the left child of root.
    // Level 0 has depth 0, so the element is the left child directly.
    assertThat(gIdx).isEqualTo(GIndexUtil.LEFT_CHILD_G_INDEX); // 0b10
  }

  @Test
  void getElementGeneralizedIndex_element1_isAtLevel1() {
    final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(1);
    // Element 1 is at level 1, position 0 in a 4-element balanced subtree (depth=2)
    // Path: right, left, then position 0 at depth 2
    // right from root = 0b11
    // left = 0b110
    // position 0 at depth 2 = 0b11000
    assertThat(gIdx).isEqualTo(0b11000);
  }

  @Test
  void getElementGeneralizedIndex_canNavigateTree() {
    // Create a tree with known values and verify we can navigate to each element
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      final byte[] data = new byte[32];
      data[0] = (byte) (i + 1);
      chunks.add(LeafNode.create(Bytes32.wrap(data)));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    for (int i = 0; i < 6; i++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      final TreeNode node = tree.get(gIdx);
      assertThat(node).isInstanceOf(LeafNode.class);
      assertThat(((LeafNode) node).getData().get(0)).isEqualTo((byte) (i + 1));
    }
  }

  @Test
  void hashTreeRoot_emptyTree_isZero() {
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of());
    assertThat(tree.hashTreeRoot()).isEqualTo(Bytes32.ZERO);
  }

  // ===== storeProgressiveSpine / loadProgressiveSpine tests =====

  private static LeafNode leaf32(final int value) {
    final byte[] data = new byte[32];
    data[0] = (byte) value;
    return LeafNode.create(Bytes32.wrap(data));
  }

  @Test
  void storeAndLoadSpine_emptyTree_returnsEmptyLeaf() {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long dataRootGIndex = GIndexUtil.LEFT_CHILD_G_INDEX;

    // Store empty tree
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore, dataRootGIndex, LeafNode.EMPTY_LEAF, 0, (g, n, c, d) -> {});

    // Load empty tree
    final TreeNode result =
        ProgressiveTreeUtil.loadProgressiveSpine(
            nodeStore, Bytes32.ZERO, dataRootGIndex, 0, (h, g, c, d) -> LeafNode.EMPTY_LEAF);

    assertThat(result).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void storeAndLoadSpine_singleChunk_roundtrips() {
    final LeafNode chunk = leaf32(42);
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(chunk));

    assertSpineRoundtrip(tree, 1);
  }

  @Test
  void storeAndLoadSpine_singleAllZeroChunk_roundtripsWithNavigableTree() {
    // Regression: when chunk is all-zero, progressiveDataTree.hashTreeRoot() equals
    // ZERO_TREES[1].hashTreeRoot(). The old code returned LeafNode.EMPTY_LEAF instead of
    // a proper BranchNode, causing navigation errors (e.g. "Invalid root index: 2").
    final LeafNode zeroChunk = LeafNode.create(Bytes32.ZERO);
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(zeroChunk));

    // Sanity check: the tree's hash collides with ZERO_TREES[1]
    assertThat(tree.hashTreeRoot()).isEqualTo(TreeUtil.ZERO_TREES[1].hashTreeRoot());

    // The tree must still be navigable after store/load (not a LeafNode)
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long dataRootGIndex = GIndexUtil.LEFT_CHILD_G_INDEX;
    final Map<Bytes32, TreeNode> subtreeByHash = new HashMap<>();
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        tree,
        1,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            subtreeByHash.put(levelSubtree.hashTreeRoot(), levelSubtree));

    final TreeNode result =
        ProgressiveTreeUtil.loadProgressiveSpine(
            nodeStore,
            tree.hashTreeRoot(),
            dataRootGIndex,
            1,
            (levelHash, levelGIndex, chunksInLevel, depth) -> subtreeByHash.get(levelHash));

    assertThat(result.hashTreeRoot()).isEqualTo(tree.hashTreeRoot());
    // Must be a BranchNode, not a LeafNode, so navigation (e.g. get(2)) works
    assertThat(result).isInstanceOf(BranchNode.class);
  }

  @Test
  void storeAndLoadSpine_multipleChunks_roundtrips() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      chunks.add(leaf32(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    assertSpineRoundtrip(tree, 6);
  }

  @Test
  void storeAndLoadSpine_fullLevel1_roundtrips() {
    // 5 chunks: level 0 (1) + level 1 (4) — fills both levels exactly
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(leaf32(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    assertSpineRoundtrip(tree, 5);
  }

  @Test
  void storeAndLoadSpine_levelStorerReceivesCorrectParameters() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      chunks.add(leaf32(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long dataRootGIndex = GIndexUtil.LEFT_CHILD_G_INDEX;

    final List<int[]> storedLevels = new ArrayList<>();
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        tree,
        6,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            storedLevels.add(new int[] {chunksInLevel, depth}));

    // 6 chunks: level 0 (1 chunk, depth 0), level 1 (4 chunks, depth 2), level 2 (1 chunk, depth 4)
    assertThat(storedLevels).hasSize(3);
    assertThat(storedLevels.get(0)).containsExactly(1, 0);
    assertThat(storedLevels.get(1)).containsExactly(4, 2);
    assertThat(storedLevels.get(2)).containsExactly(1, 4);
  }

  private void assertSpineRoundtrip(final TreeNode tree, final int totalChunks) {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long dataRootGIndex = GIndexUtil.LEFT_CHILD_G_INDEX;

    // Store level subtrees by hash (level subtrees can be BranchNodes, not just LeafNodes)
    final Map<Bytes32, TreeNode> subtreeByHash = new HashMap<>();
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        tree,
        totalChunks,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            subtreeByHash.put(levelSubtree.hashTreeRoot(), levelSubtree));

    // Load: retrieve level subtrees by hash
    final TreeNode result =
        ProgressiveTreeUtil.loadProgressiveSpine(
            nodeStore,
            tree.hashTreeRoot(),
            dataRootGIndex,
            totalChunks,
            (levelHash, levelGIndex, chunksInLevel, depth) -> subtreeByHash.get(levelHash));

    assertThat(result.hashTreeRoot()).isEqualTo(tree.hashTreeRoot());
  }
}

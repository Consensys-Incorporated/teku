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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class ProgressiveTreeUtilUpdateTest {

  private static LeafNode leaf(final int value) {
    final byte[] data = new byte[32];
    data[0] = (byte) value;
    return LeafNode.create(Bytes32.wrap(data));
  }

  @Test
  void emptyUpdates_returnsSameTree() {
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(leaf(1), leaf(2)));
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 2);

    assertThat(result).isSameAs(tree);
  }

  @Test
  void emptyTree_withNewTotalChunksZero_returnsEmptyLeaf() {
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();

    final TreeNode result =
        ProgressiveTreeUtil.updateProgressiveTree(LeafNode.EMPTY_LEAF, updates, 0);

    assertThat(result).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void modifyExistingChunk_level0() {
    final List<LeafNode> chunks = List.of(leaf(1), leaf(2));
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(0, leaf(99));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 2);

    // Verify chunk 0 was updated
    final TreeNode node0 = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(0));
    assertThat(((LeafNode) node0).getData().get(0)).isEqualTo((byte) 99);

    // Verify chunk 1 is unchanged
    final TreeNode node1 = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(1));
    assertThat(((LeafNode) node1).getData().get(0)).isEqualTo((byte) 2);
  }

  @Test
  void modifyExistingChunk_level1() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Modify chunk at index 3 (level 1, position 2)
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(3, leaf(99));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 5);

    // Verify the modification
    final TreeNode node3 = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(3));
    assertThat(((LeafNode) node3).getData().get(0)).isEqualTo((byte) 99);

    // Verify other chunks unchanged
    for (int i = 0; i < 5; i++) {
      if (i == 3) {
        continue;
      }
      final TreeNode node = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(i));
      assertThat(((LeafNode) node).getData().get(0)).isEqualTo((byte) (i + 1));
    }
  }

  @Test
  void modifyChunksAcrossMultipleLevels() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      chunks.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Modify chunk 0 (level 0) and chunk 5 (level 2)
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(0, leaf(10));
    updates.put(5, leaf(60));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 6);

    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(0)))
                .getData()
                .get(0))
        .isEqualTo((byte) 10);
    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(5)))
                .getData()
                .get(0))
        .isEqualTo((byte) 60);

    // Unchanged chunks
    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(1)))
                .getData()
                .get(0))
        .isEqualTo((byte) 2);
  }

  @Test
  void appendWithinExistingLevelCapacity() {
    // Start with 2 chunks (levels 0 and 1 with 1 of 4 slots used)
    final List<LeafNode> chunks = List.of(leaf(1), leaf(2));
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Append chunk at index 2 (still level 1, within capacity)
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(2, leaf(3));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 3);

    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(0)))
                .getData()
                .get(0))
        .isEqualTo((byte) 1);
    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(1)))
                .getData()
                .get(0))
        .isEqualTo((byte) 2);
    assertThat(
            ((LeafNode) result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(2)))
                .getData()
                .get(0))
        .isEqualTo((byte) 3);
  }

  @Test
  void appendRequiringNewLevel() {
    // Start with 5 chunks (levels 0, 1 full)
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Append chunk at index 5 (level 2, requires new level)
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(5, leaf(6));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 6);

    // Verify all chunks
    for (int i = 0; i < 6; i++) {
      final TreeNode node = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(i));
      assertThat(((LeafNode) node).getData().get(0)).isEqualTo((byte) (i + 1));
    }
  }

  @Test
  void appendFromEmpty() {
    final TreeNode tree = LeafNode.EMPTY_LEAF;

    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(0, leaf(1));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 1);

    final TreeNode node0 = result.get(ProgressiveTreeUtil.getElementGeneralizedIndex(0));
    assertThat(((LeafNode) node0).getData().get(0)).isEqualTo((byte) 1);
  }

  @Test
  void structuralSharing_unchangedSubtreesReuseSameNodeReference() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Only modify chunk 0 (level 0)
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(0, leaf(99));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 5);

    // Level 1 subtree should be the same object reference (structural sharing)
    final BranchNode originalRoot = (BranchNode) tree;
    final BranchNode resultRoot = (BranchNode) result;

    // Right child of root contains level 1+ spine
    final BranchNode originalRightSpine = (BranchNode) originalRoot.right();
    final BranchNode resultRightSpine = (BranchNode) resultRoot.right();

    // Level 1's balanced subtree (left child of right spine) should be same reference
    assertThat(resultRightSpine.left()).isSameAs(originalRightSpine.left());
  }

  @Test
  void updateProducesCorrectHashTreeRoot() {
    // Create tree from chunks, then update one and verify hash matches fresh creation
    final List<LeafNode> original = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      original.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(original);

    // Update chunk 1 to value 99
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(1, leaf(99));
    final TreeNode updated = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 3);

    // Build expected tree from scratch
    final List<LeafNode> expected = new ArrayList<>();
    expected.add(leaf(1));
    expected.add(leaf(99));
    expected.add(leaf(3));
    final TreeNode expectedTree = ProgressiveTreeUtil.createProgressiveTree(expected);

    assertThat(updated.hashTreeRoot()).isEqualTo(expectedTree.hashTreeRoot());
  }

  @Test
  void mixedModificationsAndAppends() {
    final List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      chunks.add(leaf(i + 1));
    }
    final TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    // Modify index 0, append indices 3 and 4
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(0, leaf(10));
    updates.put(3, leaf(4));
    updates.put(4, leaf(5));

    final TreeNode result = ProgressiveTreeUtil.updateProgressiveTree(tree, updates, 5);

    // Build expected
    final List<LeafNode> expected = List.of(leaf(10), leaf(2), leaf(3), leaf(4), leaf(5));
    final TreeNode expectedTree = ProgressiveTreeUtil.createProgressiveTree(expected);

    assertThat(result.hashTreeRoot()).isEqualTo(expectedTree.hashTreeRoot());
  }

  @Test
  void updateProgressiveTree_usesLevelDefaultSupplierForNewLevels() {
    // Marker default subtree so we can detect which base the new level was built from.
    // Must be a real depth-2 subtree: updateLevel applies TreeUpdates with depth-2
    // gIndices against it, and LeafNode.updated throws on any non-SELF gIndex.
    final TreeNode markerLeaf =
        LeafNode.create(
            Bytes32.fromHexString(
                "0xabababababababababababababababababababababababababababababababab"));
    final TreeNode marker = TreeUtil.createTree(List.of(), markerLeaf, 2);
    final ProgressiveTreeUtil.LevelDefaultSupplier markerDefaults =
        level -> level == 1 ? marker : TreeUtil.ZERO_TREES[ProgressiveTreeUtil.levelDepth(level)];

    // Start from a tree with 1 chunk (level 0 only), append chunk index 1 (first chunk of level 1)
    final TreeNode initialTree =
        ProgressiveTreeUtil.createProgressiveTree(List.of(LeafNode.create(Bytes32.ZERO)));
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(1, LeafNode.create(Bytes32.leftPad(Bytes.of(1))));

    final TreeNode updated =
        ProgressiveTreeUtil.updateProgressiveTree(initialTree, updates, 2, markerDefaults);

    // The chunk update landed at level-1 position 0; the other three level-1 slots keep
    // the marker leaves instead of zero leaves, so the root differs from the plain
    // supplier's result iff the supplier's subtree was used as the update base.
    final TreeNode plainUpdated =
        ProgressiveTreeUtil.updateProgressiveTree(initialTree, updates, 2);
    assertThat(updated.hashTreeRoot()).isNotEqualTo(plainUpdated.hashTreeRoot());
  }

  @Test
  void updateProgressiveTree_zeroLevelDefaultsMatchesLegacyOverload() {
    final TreeNode initialTree =
        ProgressiveTreeUtil.createProgressiveTree(List.of(LeafNode.create(Bytes32.ZERO)));
    final Int2ObjectMap<TreeNode> updates = new Int2ObjectOpenHashMap<>();
    updates.put(1, LeafNode.create(Bytes32.leftPad(Bytes.of(1))));

    final TreeNode viaOverload =
        ProgressiveTreeUtil.updateProgressiveTree(
            initialTree, updates, 2, ProgressiveTreeUtil.ZERO_LEVEL_DEFAULTS);
    final TreeNode viaLegacy = ProgressiveTreeUtil.updateProgressiveTree(initialTree, updates, 2);
    assertThat(viaOverload.hashTreeRoot()).isEqualTo(viaLegacy.hashTreeRoot());
  }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;

public class ZeroPaddedSpineNodeTest {

  /**
   * Reference implementation: the fully materialized zero-padded tree exactly as {@link
   * TreeUtil#createTree(List, int)} built it before spine node compression was introduced.
   */
  private static TreeNode naiveTree(
      final List<? extends TreeNode> leaves, final int from, final int to, final int depth) {
    if (from == to) {
      return TreeUtil.ZERO_TREES[depth];
    } else if (depth == 0) {
      return leaves.get(from);
    } else {
      final long index = 1L << (depth - 1);
      final int mid = index > to - from ? to : from + (int) index;
      return BranchNode.create(
          naiveTree(leaves, from, mid, depth - 1), naiveTree(leaves, mid, to, depth - 1));
    }
  }

  private static TreeNode naiveTree(final List<? extends TreeNode> leaves, final int depth) {
    return naiveTree(leaves, 0, leaves.size(), depth);
  }

  private static List<LeafNode> leaves(final int count) {
    return IntStream.range(0, count).mapToObj(i -> TreeTest.newTestLeaf(i + 1)).toList();
  }

  @Test
  public void createTree_shouldWrapSparseLeavesInSpineNode() {
    final TreeNode tree = TreeUtil.createTree(leaves(1), 25);
    assertThat(tree).isInstanceOf(ZeroPaddedSpineNode.class);
  }

  @Test
  public void createTree_shouldNotWrapWhenLeavesFillTheDepth() {
    assertThat(TreeUtil.createTree(leaves(4), 2)).isNotInstanceOf(ZeroPaddedSpineNode.class);
    assertThat(TreeUtil.createTree(leaves(3), 2)).isNotInstanceOf(ZeroPaddedSpineNode.class);
  }

  @Test
  public void createTree_shouldReturnZeroTreeForEmptyLeaves() {
    assertThat(TreeUtil.createTree(List.of(), 25)).isSameAs(TreeUtil.ZERO_TREES[25]);
  }

  @ParameterizedTest
  @CsvSource({
    "1, 1",
    "1, 5",
    "1, 25",
    "2, 25",
    "3, 6",
    "5, 25",
    "8, 4",
    "100, 25",
    "1000, 25",
    "0, 25"
  })
  public void hashTreeRoot_shouldMatchFullyMaterializedTree(final int leafCount, final int depth) {
    final List<LeafNode> leaves = leaves(leafCount);
    final TreeNode tree = TreeUtil.createTree(leaves, depth);
    final TreeNode naive = naiveTree(leaves, depth);
    assertThat(tree.hashTreeRoot()).isEqualTo(naive.hashTreeRoot());
  }

  @Test
  public void get_shouldNavigateToLeavesAndZeroSubtrees() {
    final List<LeafNode> leaves = leaves(3);
    final int depth = 10;
    final TreeNode tree = TreeUtil.createTree(leaves, depth);

    for (int i = 0; i < leaves.size(); i++) {
      final long leafGIndex = GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, i, depth);
      assertThat(tree.get(leafGIndex)).isEqualTo(leaves.get(i));
    }
    // right child of the root is an untouched zero subtree
    assertThat(tree.get(GIndexUtil.RIGHT_CHILD_G_INDEX).hashTreeRoot())
        .isEqualTo(TreeUtil.ZERO_TREES[depth - 1].hashTreeRoot());
    // a leaf position beyond the populated prefix is a zero leaf
    final long emptyLeafGIndex = GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 7, depth);
    assertThat(tree.get(emptyLeafGIndex).hashTreeRoot())
        .isEqualTo(LeafNode.EMPTY_LEAF.hashTreeRoot());
  }

  @Test
  public void iterate_shouldVisitSameLeavesAsMaterializedTree() {
    final List<LeafNode> leaves = leaves(5);
    final int depth = 12;
    final Bytes spineData = TreeUtil.concatenateLeavesData(TreeUtil.createTree(leaves, depth));
    final Bytes naiveData = TreeUtil.concatenateLeavesData(naiveTree(leaves, depth));
    assertThat(spineData).isEqualTo(naiveData);
  }

  @ParameterizedTest
  @CsvSource({"0", "2", "7", "100"})
  public void updated_shouldMatchMaterializedTreeUpdate(final int updatePosition) {
    final List<LeafNode> leaves = leaves(3);
    final int depth = 10;
    final TreeNode tree = TreeUtil.createTree(leaves, depth);
    final TreeNode naive = naiveTree(leaves, depth);

    final long gIndex = GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, updatePosition, depth);
    final TreeNode newLeaf = TreeTest.newTestLeaf(777);

    assertThat(tree.updated(gIndex, newLeaf).hashTreeRoot())
        .isEqualTo(naive.updated(gIndex, newLeaf).hashTreeRoot());
  }

  @Test
  public void updated_shouldSupportBatchUpdatesAcrossTheSpine() {
    final List<LeafNode> leaves = leaves(2);
    final int depth = 8;
    final TreeNode tree = TreeUtil.createTree(leaves, depth);
    final TreeNode naive = naiveTree(leaves, depth);

    final List<TreeUpdates.Update> updates = new ArrayList<>();
    updates.add(
        new TreeUpdates.Update(
            GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, depth),
            TreeTest.newTestLeaf(555)));
    updates.add(
        new TreeUpdates.Update(
            GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 200, depth),
            TreeTest.newTestLeaf(666)));

    assertThat(tree.updated(new TreeUpdates(new ArrayList<>(updates))).hashTreeRoot())
        .isEqualTo(naive.updated(new TreeUpdates(new ArrayList<>(updates))).hashTreeRoot());
  }

  @Test
  public void listOfTinyByteLists_shouldRoundTripAndMatchMaterializedRoot() {
    // degenerate case: List[ByteList[1 << 30], 1 << 20] holding many single-byte elements
    final SszByteListSchema<SszByteList> elementSchema = SszByteListSchema.create(1L << 30);
    final SszListSchema<SszByteList, ?> listSchema = SszListSchema.create(elementSchema, 1L << 20);

    final List<SszByteList> elements =
        IntStream.range(0, 100).mapToObj(i -> elementSchema.fromBytes(Bytes.of(i))).toList();
    final var list = listSchema.createFromElements(elements);

    // serialization round trip preserves data and root
    final Bytes serialized = list.sszSerialize();
    final var deserialized = listSchema.sszDeserialize(serialized);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
    assertThat(deserialized.sszSerialize()).isEqualTo(serialized);
    assertThat(deserialized.get(42).getBytes()).isEqualTo(Bytes.of(42));

    // root matches the fully materialized reference construction
    final List<LeafNode> elementLeaf = List.of(LeafNode.create(Bytes.of(42)));
    final TreeNode naiveElementData = naiveTree(elementLeaf, elementSchema.treeDepth());
    assertThat(list.get(42).getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX).hashTreeRoot())
        .isEqualTo(naiveElementData.hashTreeRoot());
  }
}

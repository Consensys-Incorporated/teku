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
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatLongCollection;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.TestUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates.Update;

public class TreeTest {

  public static LeafNode newTestLeaf(final long l) {
    return LeafNode.create(Bytes32.leftPad(Bytes.ofUnsignedLong(l, ByteOrder.BIG_ENDIAN)));
  }

  @Test
  public void testCreateTreeFromLeafNodes() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 5).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));

    BranchNode n10 = (BranchNode) n1.left();
    BranchNode n11 = (BranchNode) n1.right();
    BranchNode n100 = (BranchNode) n10.left();
    BranchNode n101 = (BranchNode) n10.right();
    BranchNode n110 = (BranchNode) n11.left();
    BranchNode n111 = (BranchNode) n11.right();

    assertThat(n100.left()).isEqualTo(newTestLeaf(0));
    assertThat(n100.right()).isEqualTo(newTestLeaf(1));
    assertThat(n101.left()).isEqualTo(newTestLeaf(2));
    assertThat(n101.right()).isEqualTo(newTestLeaf(3));
    assertThat(n110.left()).isEqualTo(newTestLeaf(4));
    assertThat(n110.right()).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n111.left()).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n111.right()).isSameAs(LeafNode.EMPTY_LEAF);

    assertThat(n1.get(0b1)).isSameAs(n1);
    assertThat(n1.get(0b10)).isSameAs(n10);
    assertThat(n1.get(0b111)).isSameAs(n111);
    assertThat(n1.get(0b1000)).isSameAs(n100.left());
    assertThat(n1.get(0b1100)).isSameAs(n110.left());
    assertThat(n10.get(0b100)).isSameAs(n100.left());
    assertThat(n11.get(0b100)).isSameAs(n110.left());
  }

  @Test
  public void testCreateTreeWithDefaultNode() {
    final TreeNode defaultLeaf = newTestLeaf(111);
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 5).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()),
                defaultLeaf,
                3);

    BranchNode n10 = (BranchNode) n1.left();
    BranchNode n11 = (BranchNode) n1.right();
    BranchNode n100 = (BranchNode) n10.left();
    BranchNode n101 = (BranchNode) n10.right();
    BranchNode n110 = (BranchNode) n11.left();
    BranchNode n111 = (BranchNode) n11.right();

    assertThat(n100.left()).isEqualTo(newTestLeaf(0));
    assertThat(n100.right()).isEqualTo(newTestLeaf(1));
    assertThat(n101.left()).isEqualTo(newTestLeaf(2));
    assertThat(n101.right()).isEqualTo(newTestLeaf(3));
    assertThat(n110.left()).isEqualTo(newTestLeaf(4));
    assertThat(n110.right()).isSameAs(defaultLeaf);
    assertThat(n111.left()).isSameAs(defaultLeaf);
    assertThat(n111.right()).isSameAs(defaultLeaf);
  }

  @Test
  public void testZeroLeafDefaultTree() {
    TreeNode n1 = TreeUtil.createDefaultTree(5, LeafNode.EMPTY_LEAF);
    assertThat(n1.get(0b1000)).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b101));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b110));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b111));
    assertThat(n1.get(0b10)).isSameAs(n1.get(0b11));
  }

  @Test
  public void testNonZeroLeafDefaultTree() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(5, LeafNode.EMPTY_LEAF);

    TreeNode defaultLeaf = newTestLeaf(111);
    BranchNode n1 = (BranchNode) TreeUtil.createDefaultTree(5, defaultLeaf);
    assertThat(n1.get(0b1000)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1001)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1100)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1101)).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(LeafNode.EMPTY_LEAF);
    assertThat(n1.get(0b111)).isSameAs(zeroTree.get(0b111));
  }

  @Test
  public void testUpdated() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(8, LeafNode.EMPTY_LEAF);
    TreeNode t1a = zeroTree.updated(8 + 0, newTestLeaf(111));
    TreeNode t1b = zeroTree.updated(8 + 0, newTestLeaf(111));
    assertThat(t1a).isNotSameAs(t1b);
    assertThat(t1a.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(IntStream.range(1, 8).mapToObj(idx -> t1a.get(8 + idx)))
        .containsOnly(LeafNode.EMPTY_LEAF);
    assertThat(t1a.hashTreeRoot()).isEqualTo(t1b.hashTreeRoot());

    TreeNode t2a = t1a.updated(8 + 3, newTestLeaf(222));
    TreeNode t2b =
        zeroTree.updated(
            new TreeUpdates(
                List.of(new Update(8 + 0, newTestLeaf(111)), new Update(8 + 3, newTestLeaf(222)))));
    assertThat(t2a).isNotSameAs(t2b);
    assertThat(t2a.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(t2a.get(8 + 3)).isEqualTo(newTestLeaf(222));
    assertThat(IntStream.of(1, 2, 4, 5, 6, 7).mapToObj(idx -> t2a.get(8 + idx)))
        .containsOnly(LeafNode.EMPTY_LEAF);
    assertThat(t2a.hashTreeRoot()).isEqualTo(t2b.hashTreeRoot());

    TreeNode zeroTreeB =
        t2a.updated(
            new TreeUpdates(
                List.of(
                    new Update(8 + 0, LeafNode.EMPTY_LEAF),
                    new Update(8 + 3, LeafNode.EMPTY_LEAF))));
    assertThat(zeroTree.hashTreeRoot()).isEqualTo(zeroTreeB.hashTreeRoot());
  }

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testHashThreadSafe() {
    // since the hash can be calculated lazily and cached inside TreeNode there are
    // potential threading issues
    TreeNode tree = TreeUtil.createDefaultTree(32 * 1024, newTestLeaf(111));
    List<Future<Bytes32>> hashFuts = TestUtil.executeParallel(tree::hashTreeRoot, 512);
    assertThat(TestUtil.waitAll(hashFuts)).containsOnly(tree.hashTreeRoot());
  }

  @Test
  void testLeavesIterator() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 8).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));
    Assertions.assertThat(collectLeaves(n1, 0b1000, 0b1000)).containsExactly(newTestLeaf(0));
    Assertions.assertThat(collectLeaves(n1, 0b1000, 0b1001))
        .containsExactly(newTestLeaf(0), newTestLeaf(1));
    Assertions.assertThat(collectLeaves(n1, 0b100, 0b100))
        .containsExactly(newTestLeaf(0), newTestLeaf(1));
    Assertions.assertThat(collectLeaves(n1, 0b101, 0b1100))
        .containsExactly(newTestLeaf(2), newTestLeaf(3), newTestLeaf(4));
    Assertions.assertThat(collectLeaves(n1, 0b101, 0b110))
        .containsExactly(newTestLeaf(2), newTestLeaf(3), newTestLeaf(4), newTestLeaf(5));
    Assertions.assertThat(collectLeaves(n1, 0b100, 0b110))
        .containsExactly(
            newTestLeaf(0),
            newTestLeaf(1),
            newTestLeaf(2),
            newTestLeaf(3),
            newTestLeaf(4),
            newTestLeaf(5));
  }

  private BranchNode createNonPlainTree() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 4).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));
    BranchNode n2 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(4, 6).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));
    return BranchNode.create(n1, n2);
  }

  @Test
  void testTreeNodeIterator() {
    BranchNode root = createNonPlainTree();

    LongList iteratedIndices = new LongArrayList();
    root.iterateAll(
        (node, idx) -> {
          assertThat(root.get(idx)).isSameAs(node);
          iteratedIndices.add(idx);
          return true;
        });

    assertThatLongCollection(iteratedIndices)
        .containsExactly(
            0b1L, 0b10L, 0b100L, 0b1000L, 0b1001L, 0b101L, 0b1010L, 0b1011L, 0b11L, 0b110L, 0b111L);
  }

  @Test
  void testTreeNodeIteratorWithRange() {
    BranchNode root = createNonPlainTree();

    LongList iteratedIndices = new LongArrayList();
    root.iterateRange(
        0b101,
        0b110,
        (node, idx) -> {
          assertThat(root.get(idx)).isSameAs(node);
          iteratedIndices.add(idx);
          return true;
        });

    assertThatLongCollection(iteratedIndices)
        .containsExactly(0b1L, 0b10L, 0b101L, 0b1010L, 0b1011L, 0b11L, 0b110L);
  }

  @Test
  void testTreeNodeIteratorWithDegenerateRange() {
    BranchNode root = createNonPlainTree();

    LongList iteratedIndices = new LongArrayList();
    root.iterateRange(
        0b100000,
        0b100001,
        (node, idx) -> {
          assertThat(root.get(idx)).isSameAs(node);
          iteratedIndices.add(idx);
          return true;
        });

    assertThatLongCollection(iteratedIndices).containsExactly(0b1L, 0b10L, 0b100L, 0b1000L);
  }

  @Test
  void testTreeNodeIteratorWithEqualStartEndNodes() {
    BranchNode root = createNonPlainTree();

    LongList iteratedIndices = new LongArrayList();
    root.iterateRange(
        0b11,
        0b11,
        (node, idx) -> {
          assertThat(root.get(idx)).isSameAs(node);
          iteratedIndices.add(idx);
          return true;
        });

    assertThatLongCollection(iteratedIndices).containsExactly(0b1L, 0b11L, 0b110L, 0b111L);
  }

  static List<LeafNode> collectLeaves(final TreeNode n, final long from, final long to) {
    List<LeafNode> ret = new ArrayList<>();
    TreeUtil.iterateLeaves(n, from, to, ret::add);
    return ret;
  }
}

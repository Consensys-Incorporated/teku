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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class MerkleUtilTest {

  @Test
  void testEmptyProof() {
    TreeNode root = TreeUtil.createTree(Collections.singletonList(TreeTest.newTestLeaf(1)));
    List<Bytes32> proof = MerkleUtil.constructMerkleProof(root, GIndexUtil.SELF_G_INDEX);

    assertThat(proof).isEqualTo(Collections.emptyList());
  }

  @Test
  void testDepthOneProof() {
    List<LeafNode> leaves = Arrays.asList(TreeTest.newTestLeaf(2), TreeTest.newTestLeaf(3));
    TreeNode root = TreeUtil.createTree(leaves);

    List<Bytes32> proofExpected = Collections.singletonList(root.get(0b11).hashTreeRoot());
    List<Bytes32> proofActual = MerkleUtil.constructMerkleProof(root, 0b10);

    assertThat(proofActual).isEqualTo(proofExpected);
  }

  @Test
  void testProofLeftOfRoot() {
    // Proof for leaf with value 10
    List<LeafNode> leaves =
        Arrays.asList(
            TreeTest.newTestLeaf(8),
            TreeTest.newTestLeaf(9),
            TreeTest.newTestLeaf(10),
            TreeTest.newTestLeaf(11),
            TreeTest.newTestLeaf(12));
    TreeNode root = TreeUtil.createTree(leaves);

    List<Bytes32> proofExpected =
        Arrays.asList(
            root.get(0b1011).hashTreeRoot(),
            root.get(0b100).hashTreeRoot(),
            root.get(0b11).hashTreeRoot());
    List<Bytes32> proofActual = MerkleUtil.constructMerkleProof(root, 0b1010);

    assertThat(proofActual).isEqualTo(proofExpected);
  }

  @Test
  void testProofRightOfRoot() {
    // Proof for leaf with value 12
    List<LeafNode> leaves =
        Arrays.asList(
            TreeTest.newTestLeaf(8),
            TreeTest.newTestLeaf(9),
            TreeTest.newTestLeaf(10),
            TreeTest.newTestLeaf(11),
            TreeTest.newTestLeaf(12));
    TreeNode root = TreeUtil.createTree(leaves);

    List<Bytes32> proofExpected =
        Arrays.asList(
            root.get(0b1101).hashTreeRoot(),
            root.get(0b111).hashTreeRoot(),
            root.get(0b10).hashTreeRoot());
    List<Bytes32> proofActual = MerkleUtil.constructMerkleProof(root, 0b1100);

    assertThat(proofActual).isEqualTo(proofExpected);
  }

  @Test
  void testPathInvalidIndex() {
    assertThatThrownBy(
            () -> {
              MerkleUtil.getPathToNode(0);
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid node index");
  }

  @Test
  void testPathFromRootToRoot() {
    List<Long> path = MerkleUtil.getPathToNode(GIndexUtil.SELF_G_INDEX);
    assertThat(path).isEqualTo(Collections.singletonList(GIndexUtil.SELF_G_INDEX));
  }

  @Test
  void testPathDepthOneTree() {
    List<Long> path = MerkleUtil.getPathToNode(0b10);
    assertThat(path).isEqualTo(Arrays.asList(0b1L, 0b10L));
  }

  @Test
  void testPathLeftOfRoot() {
    List<Long> path = MerkleUtil.getPathToNode(0b1010);
    assertThat(path).isEqualTo(Arrays.asList(0b1L, 0b10L, 0b101L, 0b1010L));
  }

  @Test
  void testPathRightOfRoot() {
    List<Long> path = MerkleUtil.getPathToNode(0b1100);
    assertThat(path).isEqualTo(Arrays.asList(0b1L, 0b11L, 0b110L, 0b1100L));
  }
}

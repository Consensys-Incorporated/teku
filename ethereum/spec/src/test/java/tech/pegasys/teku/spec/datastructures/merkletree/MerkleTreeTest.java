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

package tech.pegasys.teku.spec.datastructures.merkletree;

import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatBooleanCollection;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.util.MerkleTree;

public class MerkleTreeTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private MerkleTree merkleTree1;
  private MerkleTree merkleTree2;
  private final int treeDepth = 4;

  private final List<Bytes32> leaves =
      List.of(
          Bytes32.fromHexString("0x0001"),
          Bytes32.fromHexString("0x0002"),
          Bytes32.fromHexString("0x0003"),
          Bytes32.fromHexString("0x0004"),
          Bytes32.fromHexString("0x0005"),
          Bytes32.fromHexString("0x0006"),
          Bytes32.fromHexString("0x0007"),
          Bytes32.fromHexString("0x0008"),
          Bytes32.fromHexString("0x0009"),
          Bytes32.fromHexString("0x0010"),
          Bytes32.fromHexString("0x0011"),
          Bytes32.fromHexString("0x0012"),
          Bytes32.fromHexString("0x0013"),
          Bytes32.fromHexString("0x0014"),
          Bytes32.fromHexString("0x0015"),
          Bytes32.fromHexString("0x0016"));

  @Test
  void getProof() {
    merkleTree1 = new MerkleTree(treeDepth);

    BooleanList results = new BooleanArrayList();
    for (int index = 0; index < 7; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      results.add(
          genesisSpec
              .predicates()
              .isValidMerkleBranch(
                  leaf,
                  toSszBytes32Vector(merkleTree1.getProof(leaf)),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root));
    }
    assertThatBooleanCollection(results).allSatisfy(Assertions::assertTrue);
  }

  private SszBytes32Vector toSszBytes32Vector(final List<Bytes32> list) {
    return SszBytes32VectorSchema.create(list.size()).of(list);
  }

  @Test
  void proofsWithViewBoundaryOptimizedTree_getProofForIndexAlwaysSmallerThanLimit() {
    merkleTree1 = new MerkleTree(treeDepth);
    merkleTree2 = new MerkleTree(treeDepth);

    for (int i = 0; i < 8; i++) {
      merkleTree2.add(leaves.get(i));
    }

    BooleanList results = new BooleanArrayList();
    for (int index = 0; index < 8; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      results.add(
          genesisSpec
              .predicates()
              .isValidMerkleBranch(
                  leaf,
                  toSszBytes32Vector(merkleTree2.getProofWithViewBoundary(leaf, index + 1)),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root));
    }
    assertThatBooleanCollection(results).allSatisfy(Assertions::assertTrue);
  }

  @Test
  void proofsWithViewBoundary_getProofForEachIndexInTheSmallTree() {
    merkleTree1 = new MerkleTree(treeDepth);
    merkleTree2 = new MerkleTree(treeDepth);

    for (int i = 0; i < 16; i++) {
      merkleTree2.add(leaves.get(i));
    }

    for (int i = 0; i < 10; i++) {
      merkleTree1.add(leaves.get(i));
    }

    Bytes32 root = merkleTree1.getRoot();

    BooleanList results = new BooleanArrayList();
    for (int index = 0; index < 10; index++) {
      results.add(
          genesisSpec
              .predicates()
              .isValidMerkleBranch(
                  leaves.get(index),
                  toSszBytes32Vector(merkleTree2.getProofWithViewBoundary(index, 10)),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root));
    }
    assertThatBooleanCollection(results).allSatisfy(Assertions::assertTrue);
  }
}

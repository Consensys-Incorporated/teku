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
import static tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNodeTest.elementsOfSizes;
import static tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNodeTest.serializeElements;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;

public class SszPackedProgressiveByteListsNodeTest {

  static final SszProgressiveByteListSchema<SszByteList> ELEMENT_SCHEMA =
      new SszProgressiveByteListSchema<>();
  static final SszProgressiveListSchema<SszByteList> ORACLE_SCHEMA =
      SszProgressiveListSchema.create(ELEMENT_SCHEMA);

  static SszPackedProgressiveByteListsNode node(final List<Bytes> elements) {
    return node(elements, SszPackedProgressiveByteListsNodeTest::materializeElement);
  }

  static SszPackedProgressiveByteListsNode node(
      final List<Bytes> elements, final Function<Bytes, TreeNode> materializer) {
    return new SszPackedProgressiveByteListsNode(
        serializeElements(elements), SszPackedByteListsNodeTest.offsetsOf(elements), materializer);
  }

  static TreeNode materializeElement(final Bytes elementSsz) {
    try (SszReader reader = SszReader.fromBytes(elementSsz)) {
      return ELEMENT_SCHEMA.sszDeserializeTree(reader);
    }
  }

  static TreeNode oracleDataNode(final List<Bytes> elements) {
    return ORACLE_SCHEMA
        .sszDeserialize(serializeElements(elements))
        .getBackingNode()
        .get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  /** Element counts crossing progressive level boundaries (cumulative capacities 1, 5, 21, 85). */
  static List<Integer> levelBoundaryCounts() {
    return List.of(1, 2, 4, 5, 6, 20, 21, 22, 85, 86, 100);
  }

  /** Element sizes crossing chunk and element-internal level boundaries (chunks 0..21). */
  static List<int[]> sizeMatrix() {
    return List.of(
        new int[] {0},
        new int[] {1},
        new int[] {31},
        new int[] {32},
        new int[] {33},
        new int[] {100},
        new int[] {160},
        new int[] {192},
        new int[] {672},
        new int[] {0, 1, 31, 32, 33, 100, 160, 192, 672});
  }

  @ParameterizedTest
  @MethodSource("levelBoundaryCounts")
  public void hashTreeRoot_shouldMatchOracleAcrossLevelBoundaries(final int count) {
    final List<Bytes> elements = elementsOfSizes(IntStream.range(0, count).map(i -> 5).toArray());
    assertThat(node(elements).hashTreeRoot()).isEqualTo(oracleDataNode(elements).hashTreeRoot());
  }

  @ParameterizedTest
  @MethodSource("sizeMatrix")
  public void hashTreeRoot_shouldMatchOracleAcrossElementSizes(final int[] sizes) {
    final List<Bytes> elements = elementsOfSizes(sizes);
    assertThat(node(elements).hashTreeRoot()).isEqualTo(oracleDataNode(elements).hashTreeRoot());
  }

  @Test
  public void hashTreeRoot_shouldNotMaterializeElements() {
    final List<Bytes> elements = elementsOfSizes(1, 0, 33, 100, 672);
    final SszPackedProgressiveByteListsNode packed =
        node(
            elements,
            bytes -> {
              throw new AssertionError("hashTreeRoot must not materialize elements");
            });
    assertThat(packed.hashTreeRoot()).isEqualTo(oracleDataNode(elements).hashTreeRoot());
  }

  @Test
  public void get_shouldMatchOracleForAllElementAndSpinePositions() {
    final List<Bytes> elements = elementsOfSizes(1, 0, 33, 100, 160, 5, 7);
    final SszPackedProgressiveByteListsNode packed = node(elements);
    final TreeNode oracle = oracleDataNode(elements);
    // every element position (7 elements spread over levels 0-2)
    for (int i = 0; i < elements.size(); i++) {
      final long gIndex = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      assertThat(packed.get(gIndex).hashTreeRoot())
          .describedAs("element %s", i)
          .isEqualTo(oracle.get(gIndex).hashTreeRoot());
    }
    // an empty slot inside the partially filled level 2 (element index 8 of capacity 21)
    final long emptySlotGIndex = ProgressiveTreeUtil.getElementGeneralizedIndex(8);
    assertThat(packed.get(emptySlotGIndex).hashTreeRoot())
        .isEqualTo(oracle.get(emptySlotGIndex).hashTreeRoot());
    // spine positions: both root children
    assertThat(packed.get(GIndexUtil.LEFT_CHILD_G_INDEX).hashTreeRoot())
        .isEqualTo(oracle.get(GIndexUtil.LEFT_CHILD_G_INDEX).hashTreeRoot());
    assertThat(packed.get(GIndexUtil.RIGHT_CHILD_G_INDEX).hashTreeRoot())
        .isEqualTo(oracle.get(GIndexUtil.RIGHT_CHILD_G_INDEX).hashTreeRoot());
    // inside-element navigation: element 2's length mixin
    final long insideGIndex =
        GIndexUtil.gIdxCompose(
            ProgressiveTreeUtil.getElementGeneralizedIndex(2), GIndexUtil.RIGHT_CHILD_G_INDEX);
    assertThat(packed.get(insideGIndex).hashTreeRoot())
        .isEqualTo(oracle.get(insideGIndex).hashTreeRoot());
  }

  @Test
  public void merkleProof_shouldMatchOracle() {
    final List<Bytes> elements = elementsOfSizes(1, 0, 33, 100, 160, 5, 7);
    final SszPackedProgressiveByteListsNode packed = node(elements);
    final TreeNode oracle = oracleDataNode(elements);
    final long gIndex = ProgressiveTreeUtil.getElementGeneralizedIndex(5);
    assertThat(MerkleUtil.constructMerkleProof(packed, gIndex))
        .isEqualTo(MerkleUtil.constructMerkleProof(oracle, gIndex));
  }

  @Test
  public void iterate_shouldVisitLeavesMatchingOracleData() {
    final List<Bytes> elements = elementsOfSizes(1, 0, 33, 100, 160, 5, 7);
    assertThat(TreeUtil.concatenateLeavesData(node(elements)))
        .isEqualTo(TreeUtil.concatenateLeavesData(oracleDataNode(elements)));
  }

  @Test
  public void updated_shouldDecayAndMatchOracle() {
    final List<Bytes> elements = elementsOfSizes(1, 0, 33, 100, 160, 5, 7);
    final SszPackedProgressiveByteListsNode packed = node(elements);
    final TreeNode oracle = oracleDataNode(elements);
    final TreeNode replacement = materializeElement(Bytes.of(9, 9, 9));
    final long gIndex = ProgressiveTreeUtil.getElementGeneralizedIndex(3);
    final TreeNode updatedPacked = packed.updated(gIndex, replacement);
    assertThat(updatedPacked.hashTreeRoot())
        .isEqualTo(oracle.updated(gIndex, replacement).hashTreeRoot());
    assertThat(updatedPacked).isNotInstanceOf(SszPackedProgressiveByteListsNode.class);
    assertThat(TreeUtil.concatenateLeavesData(updatedPacked))
        .isEqualTo(TreeUtil.concatenateLeavesData(oracle.updated(gIndex, replacement)));
  }
}

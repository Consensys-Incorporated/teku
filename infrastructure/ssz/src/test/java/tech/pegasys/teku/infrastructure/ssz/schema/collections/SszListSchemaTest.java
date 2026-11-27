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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSubContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.custom.SszCustomListSchema;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.InMemoryStoringTreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszListSchemaTest extends SszListSchemaTestBase {

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return Stream.concat(
        SszListSchemaTestBase.complexListSchemas(), Stream.of(new SszCustomListSchema()));
  }

  @Test
  void create_shouldCreateSpecializedSchema() {
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.BIT_SCHEMA, 10))
        .isInstanceOf(SszBitlistSchema.class);
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10))
        .isInstanceOf(SszUInt64ListSchema.class);
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 10))
        .isInstanceOf(SszPrimitiveListSchema.class);
  }

  @Test
  void loadBackingNodes_shouldRestoreTree_multipleBranchSteps_problematicBitvector() {
    final SszBitvectorSchema<SszBitvector> bitVectorSchema = SszBitvectorSchema.create(1);
    final SszListSchema<SszBitvector, ? extends SszList<SszBitvector>> listSchema =
        SszListSchema.create(bitVectorSchema, 3);
    final SszList<SszBitvector> data =
        listSchema.of(
            bitVectorSchema.of(true), bitVectorSchema.of(false), bitVectorSchema.of(true));
    assertTreeRoundtrip(listSchema, 1, data);
  }

  /**
   * Element schemas whose default value has a zero tree as hash tree root, so that runs of default
   * elements in a collection hash to deeper zero trees.
   */
  private static Stream<SszSchema<?>> zeroRootElementSchemas() {
    return Stream.of(
        TestSubContainer.SSZ_SCHEMA,
        SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 8),
        SszVectorSchema.create(TestSubContainer.SSZ_SCHEMA, 2),
        SszBytes32VectorSchema.create(2),
        SszByteVectorSchema.create(48),
        SszBitvectorSchema.create(512),
        SszUInt64ListSchema.create(4),
        SszBitlistSchema.create(8),
        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA),
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA)));
  }

  static Stream<Arguments> defaultElementCases() {
    return zeroRootElementSchemas()
        .flatMap(
            elementSchema ->
                Stream.<SszCollectionSchema<?, ?>>of(
                    SszListSchema.create(elementSchema, 2048),
                    SszVectorSchema.create(elementSchema, 8),
                    SszListSchema.createProgressive(elementSchema)))
        .flatMap(
            schema ->
                // 0 is a default element, 1 is a non-default one
                Stream.of("11001111", "10000001", "00000000")
                    .flatMap(
                        defaultElementMask ->
                            Stream.of(1, 2, 5, 15)
                                .map(
                                    maxBranchLevelsSkipped ->
                                        Arguments.of(
                                            schema, defaultElementMask, maxBranchLevelsSkipped))));
  }

  @ParameterizedTest(name = "{0} elements {1} skipping {2} levels")
  @MethodSource("defaultElementCases")
  void loadBackingNodes_shouldRestoreDefaultCompositeElements(
      final SszCollectionSchema<SszData, ?> schema,
      final String defaultElementMask,
      final int maxBranchLevelsSkipped) {
    final SszSchema<SszData> elementSchema = schema.getElementSchema();
    final SszData defaultElement = elementSchema.getDefault();
    assertThat(TreeUtil.ZERO_TREES_BY_ROOT).containsKey(defaultElement.hashTreeRoot());
    final SszData nonDefaultElement =
        randomSsz
            .randomDataStream(elementSchema)
            .filter(element -> !element.hashTreeRoot().equals(defaultElement.hashTreeRoot()))
            .findFirst()
            .orElseThrow();
    final List<SszData> elements =
        defaultElementMask
            .chars()
            .mapToObj(c -> c == '0' ? defaultElement : nonDefaultElement)
            .toList();
    final SszCollection<SszData> data = schema.createFromElements(elements);

    assertLoadedElementsMatch(schema, maxBranchLevelsSkipped, data);
  }

  static Stream<Arguments> zeroTreeRootValueCases() {
    final SszBytes32 zeroTreeRoot = SszBytes32.of(TreeUtil.ZERO_TREES[1].hashTreeRoot());
    final SszBytes32 other = SszBytes32.of(Bytes32.fromHexString("0x1234"));
    final SszBytes32VectorSchema<?> pairSchema = SszBytes32VectorSchema.create(2);
    return Stream.concat(
        // a leaf holding the root of a zero tree
        zeroTreeRootValueCasesFor(SszPrimitiveSchemas.BYTES32_SCHEMA, zeroTreeRoot, other),
        // a non-default composite whose root is the root of a zero tree
        zeroTreeRootValueCasesFor(
            pairSchema,
            pairSchema.createFromElements(List.of(zeroTreeRoot, zeroTreeRoot)),
            pairSchema.createFromElements(List.of(other, other))));
  }

  private static Stream<Arguments> zeroTreeRootValueCasesFor(
      final SszSchema<?> elementSchema,
      final SszData zeroTreeRootElement,
      final SszData otherElement) {
    return Stream.<SszCollectionSchema<?, ?>>of(
            SszListSchema.create(elementSchema, 2048),
            SszVectorSchema.create(elementSchema, 8),
            SszListSchema.createProgressive(elementSchema))
        .flatMap(
            schema ->
                // z is a zero tree root element, o is another one
                Stream.of("ozoooooo", "oozzoooo", "ozzzzooz")
                    .flatMap(
                        zeroTreeRootMask ->
                            Stream.of(1, 2, 5, 15)
                                .map(
                                    maxBranchLevelsSkipped ->
                                        Arguments.of(
                                            schema,
                                            zeroTreeRootMask,
                                            maxBranchLevelsSkipped,
                                            zeroTreeRootElement,
                                            otherElement))));
  }

  @ParameterizedTest(name = "{0} elements {1} skipping {2} levels")
  @MethodSource("zeroTreeRootValueCases")
  void loadBackingNodes_shouldRestoreValuesWithZeroTreeRoots(
      final SszCollectionSchema<SszData, ?> schema,
      final String zeroTreeRootMask,
      final int maxBranchLevelsSkipped,
      final SszData zeroTreeRootElement,
      final SszData otherElement) {
    assertThat(TreeUtil.ZERO_TREES_BY_ROOT).containsKey(zeroTreeRootElement.hashTreeRoot());
    assertThat(zeroTreeRootElement).isNotEqualTo(schema.getElementSchema().getDefault());
    final List<SszData> elements =
        zeroTreeRootMask
            .chars()
            .mapToObj(c -> c == 'z' ? zeroTreeRootElement : otherElement)
            .toList();
    final SszCollection<SszData> data = schema.createFromElements(elements);

    assertLoadedElementsMatch(schema, maxBranchLevelsSkipped, data);
  }

  private <T extends SszData> void assertLoadedElementsMatch(
      final SszCollectionSchema<T, ?> schema,
      final int maxBranchLevelsSkipped,
      final SszCollection<T> data) {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long rootGIndex = 34;
    schema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, rootGIndex, data.getBackingNode());
    final TreeNode loadedNode = schema.loadBackingNodes(nodeStore, data.hashTreeRoot(), rootGIndex);
    final SszCollection<T> loaded = schema.createFromBackingNode(loadedNode);

    assertThat(loaded.size()).isEqualTo(data.size());
    for (int i = 0; i < data.size(); i++) {
      assertThat(loaded.get(i)).describedAs("element %s", i).isEqualTo(data.get(i));
      assertThat(loaded.get(i).hashTreeRoot())
          .describedAs("root of element %s", i)
          .isEqualTo(data.get(i).hashTreeRoot());
    }
    assertThat(loadedNode.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    assertThat(loaded).isEqualTo(data);
    assertThat(loaded.sszSerialize()).isEqualTo(data.sszSerialize());
  }
}

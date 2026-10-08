/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.schema.TreeNodeAssert.assertThatTreeNode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveListSchemaTest {

  private static final SszProgressiveListSchema<SszUInt64> UINT64_LIST_SCHEMA =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);

  @Test
  void createProgressive_shouldSpecializeByteSchema() throws Exception {
    final SszListSchema<SszByte, ? extends SszList<SszByte>> schema =
        SszListSchema.createProgressive(SszPrimitiveSchemas.BYTE_SCHEMA);

    assertThat(schema).isInstanceOf(SszProgressiveByteListSchema.class);
    assertThat(schema.getDefault()).isInstanceOf(SszByteList.class);

    final SszList<?> jsonResult = JsonUtil.parse("\"0x010203\"", schema.getJsonTypeDefinition());
    assertThat(jsonResult).isInstanceOf(SszByteList.class);
    assertThat(((SszByteList) jsonResult).getBytes()).isEqualTo(Bytes.fromHexString("0x010203"));
  }

  @Test
  void createProgressive_shouldSpecializeUInt8Schema() {
    final SszListSchema<SszByte, ? extends SszList<SszByte>> schema =
        SszListSchema.createProgressive(SszPrimitiveSchemas.UINT8_SCHEMA);

    assertThat(schema).isInstanceOf(SszProgressiveByteListSchema.class);
    assertThat(schema).isInstanceOf(SszByteListSchema.class);
    assertThat(schema.getDefault()).isInstanceOf(SszByteList.class);
  }

  @Test
  void createProgressive_shouldPreserveHintsForByteSchema() {
    final SszSchemaHints hints = SszSchemaHints.sszSuperNode(4);
    final SszListSchema<SszByte, ? extends SszList<SszByte>> schema =
        SszListSchema.createProgressive(SszPrimitiveSchemas.BYTE_SCHEMA, hints);

    assertThat(schema).isInstanceOf(SszProgressiveByteListSchema.class);
    assertThat(((SszProgressiveByteListSchema<?>) schema).getHints()).isEqualTo(hints);
  }

  @Test
  void createProgressive_shouldSpecializeUInt64Schema() {
    final SszListSchema<SszUInt64, ? extends SszList<SszUInt64>> schema =
        SszListSchema.createProgressive(SszPrimitiveSchemas.UINT64_SCHEMA);

    assertThat(schema).isInstanceOf(SszProgressiveUInt64ListSchema.class);
    assertThat(schema.getDefault()).isInstanceOf(SszUInt64List.class);
  }

  @Test
  void createProgressive_shouldUseGenericSchemaForCompositeElements() {
    final SszContainerSchema<? extends SszContainer> elementSchema =
        SszContainerSchema.createProgressive(
            "Element",
            new boolean[] {true},
            List.of(NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA)));

    final SszListSchema<? extends SszContainer, ? extends SszList<? extends SszContainer>> schema =
        SszListSchema.createProgressive(elementSchema);

    assertThat(schema).isInstanceOf(SszProgressiveListSchema.class);
  }

  @Test
  void primitiveListRoundtrip() {
    final List<SszUInt64> elements =
        IntStream.range(0, 10)
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(i)))
            .collect(Collectors.toList());

    final TreeNode tree = UINT64_LIST_SCHEMA.createTreeFromElements(elements);
    final SszList<SszUInt64> list = UINT64_LIST_SCHEMA.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(list.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }

    final Bytes ssz = list.sszSerialize();
    final SszList<SszUInt64> deserialized = UINT64_LIST_SCHEMA.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(10);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());

    for (int i = 0; i < 10; i++) {
      assertThat(deserialized.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }
  }

  @Test
  void sszDeserialize_shouldValidateBooleanEncodingInEveryChunk() {
    final SszProgressiveListSchema<SszBoolean> schema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA);

    final byte[] valid = new byte[40];
    Arrays.fill(valid, (byte) 1);
    assertThat(schema.sszDeserialize(Bytes.wrap(valid)).size()).isEqualTo(40);

    final byte[] invalid = valid.clone();
    invalid[35] = 2; // second 32-byte chunk
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.wrap(invalid)))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void fixedSizeCompositeListRoundtrip() {
    // Fixed-size composite elements (all fields fixed) — exercises sszSerializeFixed composite path
    final SszContainerSchema<? extends SszContainer> elementSchema =
        SszContainerSchema.createProgressive(
            "FixedElement",
            new boolean[] {true, true},
            List.of(
                NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA)));

    final SszProgressiveListSchema<? extends SszData> listSchema =
        SszProgressiveListSchema.create(elementSchema);

    final List<SszData> elements = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final TreeNode fieldTree =
          elementSchema.createTreeFromFieldValues(
              List.of(
                  SszUInt64.of(UInt64.valueOf(i)),
                  SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) i)));
      elements.add(elementSchema.createFromBackingNode(fieldTree));
    }

    @SuppressWarnings("unchecked")
    final SszProgressiveListSchema<SszData> rawSchema =
        (SszProgressiveListSchema<SszData>) listSchema;
    final TreeNode tree = rawSchema.createTreeFromElements(elements);
    final SszList<SszData> list = rawSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(3);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszData> deserialized = rawSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(3);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void variableSizeCompositeListRoundtrip() {
    // Variable-size composite elements — exercises sszSerializeVariable path
    final SszContainerSchema<? extends SszContainer> elementSchema =
        SszContainerSchema.createProgressive(
            "VarElement",
            new boolean[] {true, true},
            List.of(
                NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("b", new SszProgressiveBitlistSchema())));

    final SszProgressiveListSchema<? extends SszData> listSchema =
        SszProgressiveListSchema.create(elementSchema);

    final List<SszData> elements = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final TreeNode fieldTree =
          elementSchema.createTreeFromFieldValues(
              List.of(
                  SszUInt64.of(UInt64.valueOf(i)),
                  new SszProgressiveBitlistSchema().ofBits(i + 1)));
      elements.add(elementSchema.createFromBackingNode(fieldTree));
    }

    @SuppressWarnings("unchecked")
    final SszProgressiveListSchema<SszData> rawSchema =
        (SszProgressiveListSchema<SszData>) listSchema;
    final TreeNode tree = rawSchema.createTreeFromElements(elements);
    final SszList<SszData> list = rawSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(3);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszData> deserialized = rawSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(3);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void emptyList() {
    final SszList<SszUInt64> list = UINT64_LIST_SCHEMA.getDefault();
    assertThat(list.size()).isZero();
    assertThat(list.sszSerialize()).isEqualTo(Bytes.EMPTY);
    assertThat(list.hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }

  @Test
  void getMaxLength_shouldReturnMaxValue() {
    assertThat(UINT64_LIST_SCHEMA.getMaxLength()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void getElementSchema_shouldReturnElementSchema() {
    assertThat(UINT64_LIST_SCHEMA.getElementSchema()).isEqualTo(SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  @Test
  void treeDepth_shouldThrow() {
    assertThatThrownBy(UINT64_LIST_SCHEMA::treeDepth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void treeWidth_shouldThrow() {
    assertThatThrownBy(UINT64_LIST_SCHEMA::treeWidth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isFixedSize_shouldReturnFalse() {
    assertThat(UINT64_LIST_SCHEMA.isFixedSize()).isFalse();
  }

  @Test
  void getDefaultTree_shouldBeEmptyList() {
    final SszList<SszUInt64> defaultList =
        UINT64_LIST_SCHEMA.createFromBackingNode(UINT64_LIST_SCHEMA.getDefaultTree());
    assertThat(defaultList.size()).isZero();
  }

  @Test
  void unboundedFactoryForms_shouldProduceUsableDefaults() {
    final SszProgressiveListSchema<SszUInt64> defaultHintsSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszProgressiveListSchema<SszUInt64> explicitHintsSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, SszSchemaHints.none());
    final SszProgressiveListSchema<SszUInt64> constructorSchema =
        new SszProgressiveListSchema<>(SszPrimitiveSchemas.UINT64_SCHEMA);

    assertThat(defaultHintsSchema.getDefault().size()).isZero();
    assertThat(explicitHintsSchema.getDefault().size()).isZero();
    assertThat(constructorSchema.getDefault().size()).isZero();
  }

  @Test
  void getChildGeneralizedIndex_shouldReturnCorrectGIndex() {
    long gIdx0 = UINT64_LIST_SCHEMA.getChildGeneralizedIndex(0);
    long gIdx1 = UINT64_LIST_SCHEMA.getChildGeneralizedIndex(1);
    // Indices should be different for different elements
    assertThat(gIdx0).isNotEqualTo(gIdx1);
    // Both should be valid (positive)
    assertThat(gIdx0).isPositive();
    assertThat(gIdx1).isPositive();
  }

  @Test
  void equals_sameElementSchema_shouldBeEqual() {
    final SszProgressiveListSchema<SszUInt64> other =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
    assertThat(UINT64_LIST_SCHEMA).isEqualTo(other);
    assertThat(UINT64_LIST_SCHEMA.hashCode()).isEqualTo(other.hashCode());
  }

  @Test
  void equals_differentElementSchema_shouldNotBeEqual() {
    final SszProgressiveListSchema<?> byteListSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA);
    assertThat(UINT64_LIST_SCHEMA).isNotEqualTo(byteListSchema);
  }

  @Test
  void getSszLengthBounds_shouldBeUnbounded() {
    assertThat(
            SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                .getSszLengthBounds()
                .isUnbounded())
        .isTrue();
  }

  @Test
  void equals_defaultHintsEqualsExplicitNoneHints() {
    assertThat(
            SszProgressiveListSchema.create(
                SszPrimitiveSchemas.UINT64_SCHEMA, SszSchemaHints.none()))
        .isEqualTo(SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  @Test
  void equals_differentHints_shouldNotBeEqual() {
    final SszProgressiveListSchema<SszUInt64> defaultHints =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszProgressiveListSchema<SszUInt64> superNodeHints =
        SszProgressiveListSchema.create(
            SszPrimitiveSchemas.UINT64_SCHEMA, SszSchemaHints.sszSuperNode(4));
    assertThat(defaultHints).isNotEqualTo(superNodeHints);
  }

  @Test
  void getName_shouldContainElementSchema() {
    assertThat(UINT64_LIST_SCHEMA.getName()).isPresent();
    assertThat(UINT64_LIST_SCHEMA.getName().get()).startsWith("ProgressiveList[");
  }

  @SuppressWarnings("unchecked")
  private static <T extends SszData> SszProgressiveListSchema<T> createProgressiveListSchema(
      final SszSchema<? extends T> elementSchema) {
    return new SszProgressiveListSchema<>((SszSchema<T>) elementSchema);
  }

  @Test
  void variableSizeElements_roundtrip() {
    // List of variable-size elements (lists inside a progressive list)
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    final SszList<SszUInt64> inner1 = innerListSchema.getDefault();
    final SszList<SszUInt64> inner2 =
        innerListSchema.createFromElements(List.of(SszUInt64.of(UInt64.valueOf(42))));

    final List<SszList<SszUInt64>> elements = List.of(inner1, inner2);
    final TreeNode tree = varListSchema.createTreeFromElements(elements);
    final SszList<SszList<SszUInt64>> list = varListSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(2);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszList<SszUInt64>> deserialized = varListSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(2);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void invalidSsz_firstOffsetExceedsData_shouldThrow() {
    // For variable-size elements, the first offset in SSZ cannot exceed available data
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    // Build invalid SSZ: 4-byte offset that points beyond available data
    final Bytes invalidSsz = Bytes.of(0xFF, 0x00, 0x00, 0x00);
    assertThatThrownBy(() -> varListSchema.sszDeserialize(invalidSsz))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void invalidSsz_zeroFirstOffset_shouldThrow() {
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    // An empty list must serialize to zero bytes; a zero first offset is invalid
    final Bytes zeroOffset = Bytes.of(0x00, 0x00, 0x00, 0x00);
    assertThatThrownBy(() -> varListSchema.sszDeserialize(zeroOffset))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void invalidSsz_zeroFirstOffsetWithTrailingBytes_shouldThrow() {
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    // Zero first offset followed by garbage must not decode as an empty list
    final Bytes invalidSsz = Bytes.of(0x00, 0x00, 0x00, 0x00, 0xDE, 0xAD, 0xBE, 0xEF);
    assertThatThrownBy(() -> varListSchema.sszDeserialize(invalidSsz))
        .isInstanceOf(SszDeserializeException.class);
  }

  // ===== SszLengthBounds overflow tests =====

  @Test
  void getSszLengthBounds_maxBytesShouldBePositive() {
    final SszLengthBounds bounds = UINT64_LIST_SCHEMA.getSszLengthBounds();
    assertThat(bounds.getMaxBytes()).isPositive();
  }

  @Test
  void getSszLengthBounds_isWithinBounds_shouldAcceptReasonableSizes() {
    final SszLengthBounds bounds = UINT64_LIST_SCHEMA.getSszLengthBounds();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
  }

  @Test
  void progressiveListAsFieldOfStandardContainer_boundsShouldNotOverflow() {
    // Standard container with a progressive list field + a UINT64 field
    final ContainerSchema2<SszContainer, SszUInt64, SszList<SszUInt64>> containerSchema =
        ContainerSchema2.create(
            SszPrimitiveSchemas.UINT64_SCHEMA,
            UINT64_LIST_SCHEMA,
            (schema, node) -> {
              throw new UnsupportedOperationException("not needed for bounds test");
            });

    final SszLengthBounds bounds = containerSchema.getSszLengthBounds();
    assertThat(bounds.getMaxBytes()).isPositive();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
  }

  @Test
  void progressiveListAsElementOfStandardList_boundsShouldNotOverflow() {
    // Standard list with progressive list elements
    final SszListSchema<SszList<SszUInt64>, ?> outerListSchema =
        SszListSchema.create(UINT64_LIST_SCHEMA, 100);

    final SszLengthBounds bounds = outerListSchema.getSszLengthBounds();
    assertThat(bounds.getMaxBytes()).isPositive();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
  }

  @Test
  void progressiveContainerWithProgressiveListField_nestedPropagation() {
    // Progressive container with a progressive list field, nested in a standard container
    final SszContainerSchema<SszContainer> innerSchema =
        SszContainerSchema.createProgressive(
            "Inner",
            new boolean[] {true, true},
            List.of(
                NamedSchema.of("value", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("items", UINT64_LIST_SCHEMA)));

    final SszLengthBounds innerBounds = innerSchema.getSszLengthBounds();
    assertThat(innerBounds.getMaxBytes()).isPositive();
    assertThat(innerBounds.isWithinBounds(1000)).isTrue();
  }

  // ===== Store/Load backing nodes roundtrip tests =====

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 15})
  void storeAndLoadBackingNodes_primitiveList(final int maxBranchLevelsSkipped) {
    assertStoreLoadRoundtrip(UINT64_LIST_SCHEMA, maxBranchLevelsSkipped);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 15})
  void storeAndLoadBackingNodes_compositeList(final int maxBranchLevelsSkipped) {
    final ContainerSchema2<SszContainer, SszUInt64, SszUInt64> elementSchema =
        ContainerSchema2.create(
            SszPrimitiveSchemas.UINT64_SCHEMA,
            SszPrimitiveSchemas.UINT64_SCHEMA,
            (schema, node) -> new SszContainerImpl(schema, node));

    @SuppressWarnings("unchecked")
    final SszProgressiveListSchema<SszData> listSchema =
        (SszProgressiveListSchema<SszData>)
            (SszProgressiveListSchema<?>) SszProgressiveListSchema.create(elementSchema);

    assertStoreLoadRoundtrip(listSchema, maxBranchLevelsSkipped);
  }

  @Test
  void storeAndLoadBackingNodes_allZeroByteList() {
    // Regression: a byte list where all 32 elements are 0x00 (e.g. epoch participation at genesis)
    // has exactly 1 chunk of all-zero bytes. That chunk's hash equals ZERO_TREES[1].hashTreeRoot(),
    // which caused loadProgressiveSpine to return LeafNode.EMPTY_LEAF instead of a BranchNode,
    // breaking element navigation with "Invalid root index: 2".
    final SszProgressiveListSchema<SszByte> byteListSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA);
    // 32 zero bytes fills exactly one 32-byte chunk
    final SszList<SszByte> list =
        byteListSchema.createFromElements(Collections.nCopies(32, SszByte.of((byte) 0)));

    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long rootGIndex = 34;
    byteListSchema.storeBackingNodes(nodeStore, 15, rootGIndex, list.getBackingNode());

    final TreeNode result =
        byteListSchema.loadBackingNodes(nodeStore, list.hashTreeRoot(), rootGIndex);

    assertThatTreeNode(result).isTreeEqual(list.getBackingNode());
    final SszList<SszByte> loaded = byteListSchema.createFromBackingNode(result);
    assertThat(loaded.size()).isEqualTo(32);
    assertThat(loaded.get(0).get()).isEqualTo((byte) 0);
  }

  @Test
  void storeAndLoadBackingNodes_emptyList() {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final SszList<SszUInt64> emptyList = UINT64_LIST_SCHEMA.getDefault();
    final TreeNode node = emptyList.getBackingNode();
    final long rootGIndex = 34;

    UINT64_LIST_SCHEMA.storeBackingNodes(nodeStore, 15, rootGIndex, node);
    final TreeNode result =
        UINT64_LIST_SCHEMA.loadBackingNodes(nodeStore, node.hashTreeRoot(), rootGIndex);

    assertThatTreeNode(result).isTreeEqual(node);
    assertThat(UINT64_LIST_SCHEMA.createFromBackingNode(result).size()).isZero();
  }

  @ParameterizedTest(name = "{0} elements of {1}")
  @MethodSource("partialChunkCases")
  <T extends SszData> void storeAndLoadBackingNodes_partialPrimitiveChunkSerializesCanonically(
      final int elementCount,
      final SszSchema<T> elementSchema,
      final IntFunction<T> elementFactory) {
    final SszProgressiveListSchema<T> schema = SszProgressiveListSchema.create(elementSchema);
    final SszList<T> original =
        schema.createFromElements(
            IntStream.range(0, elementCount).mapToObj(elementFactory).toList());
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final long rootGIndex = 34;

    schema.storeBackingNodes(nodeStore, 15, rootGIndex, original.getBackingNode());
    final SszList<T> restored =
        schema.createFromBackingNode(
            schema.loadBackingNodes(nodeStore, original.hashTreeRoot(), rootGIndex));

    final Bytes expected = original.sszSerialize();
    assertThat(restored.sszSerialize()).isEqualTo(expected);
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThat(restored.sszSerialize(output)).isEqualTo(expected.size());
    assertThat(Bytes.wrap(output.toByteArray())).isEqualTo(expected);
    assertThat(schema.getSszSize(restored.getBackingNode())).isEqualTo(expected.size());
    assertThat(restored.hashTreeRoot()).isEqualTo(original.hashTreeRoot());
  }

  private static Stream<Arguments> partialChunkCases() {
    // bytes: 32 elements per chunk; uint64: 4 elements per chunk
    final Stream<Arguments> byteCases =
        IntStream.rangeClosed(1, 70)
            .mapToObj(
                count ->
                    Arguments.of(
                        count,
                        SszPrimitiveSchemas.BYTE_SCHEMA,
                        (IntFunction<SszByte>) value -> SszByte.of((byte) value)));
    final Stream<Arguments> uint64Cases =
        IntStream.rangeClosed(1, 20)
            .mapToObj(
                count ->
                    Arguments.of(
                        count,
                        SszPrimitiveSchemas.UINT64_SCHEMA,
                        (IntFunction<SszUInt64>) value -> SszUInt64.of(UInt64.valueOf(value + 1))));
    return Stream.concat(byteCases, uint64Cases);
  }

  @SuppressWarnings("unchecked")
  private <T extends SszData> void assertStoreLoadRoundtrip(
      final SszProgressiveListSchema<T> schema, final int maxBranchLevelsSkipped) {
    final RandomSszDataGenerator generator = new RandomSszDataGenerator().withMaxListSize(50);
    final SszData data =
        generator
            .randomDataStream((SszSchema<SszData>) (SszSchema<?>) schema)
            .filter(item -> !item.getBackingNode().hashTreeRoot().isZero())
            .findFirst()
            .orElseThrow();

    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final TreeNode node = data.getBackingNode();
    final long rootGIndex = 34;

    schema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
    final TreeNode result = schema.loadBackingNodes(nodeStore, data.hashTreeRoot(), rootGIndex);

    // Hash equality (leaf nodes may have different byte lengths after store/load zero-padding)
    assertThat(result.hashTreeRoot()).isEqualTo(node.hashTreeRoot());
    final SszData rebuiltData = schema.createFromBackingNode(result);
    assertThat(rebuiltData).isEqualTo(data);
  }
}

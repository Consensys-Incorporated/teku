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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt16PrimitiveSchema.UINT16_SCHEMA;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.SmallTestStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.VarTestStructSchema;

public class SszGenericProgressiveContainerTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertValueCorrect(final TestDefinition testDefinition, final SszData result)
      throws IOException {
    final Map<String, String> expected = loadExpectedValues(testDefinition);
    final SszContainer container = (SszContainer) result;
    final Map<String, String> actual = formatSszContainer(container);
    assertThat(actual).isEqualTo(expected);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> loadExpectedValues(final TestDefinition testDefinition)
      throws IOException {
    final Map<String, Object> objectMap =
        TestDataUtils.loadYaml(testDefinition, "value.yaml", Map.class);
    return objectMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
  }

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String type = testName.substring(testName.indexOf('/') + 1, testName.indexOf('_'));
    return switch (type) {
      case "ProgressiveSingleFieldContainerTestStruct" ->
          // active_fields=[1], A: byte
          SszContainerSchema.createProgressive(
              "ProgressiveSingleFieldContainerTestStruct",
              new boolean[] {true},
              List.of(NamedSchema.of("A", SszPrimitiveSchemas.BYTE_SCHEMA)));
      case "ProgressiveSingleListContainerTestStruct" ->
          // active_fields=[0, 0, 0, 0, 1], C: ProgressiveBitlist
          SszContainerSchema.createProgressive(
              "ProgressiveSingleListContainerTestStruct",
              new boolean[] {false, false, false, false, true},
              List.of(NamedSchema.of("C", new SszProgressiveBitlistSchema())));
      case "ProgressiveVarTestStruct" -> createProgressiveVarTestStructSchema();
      case "ProgressiveComplexTestStruct" -> createProgressiveComplexTestStructSchema();
      default ->
          throw new UnsupportedOperationException(
              "Unsupported progressive container type: " + type);
    };
  }

  @Override
  protected Object parseString(final TestDefinition testDefinition, final String value) {
    return null;
  }

  // active_fields=[1, 0, 1, 0, 1], A: byte, B: List[uint16, 123], C: ProgressiveBitlist
  private static SszContainerSchema<?> createProgressiveVarTestStructSchema() {
    return SszContainerSchema.createProgressive(
        "ProgressiveVarTestStruct",
        new boolean[] {true, false, true, false, true},
        List.of(
            NamedSchema.of("A", SszPrimitiveSchemas.BYTE_SCHEMA),
            NamedSchema.of("B", SszListSchema.create(UINT16_SCHEMA, 123)),
            NamedSchema.of("C", new SszProgressiveBitlistSchema())));
  }

  /**
   * active_fields = [1,0,1,0,1, 0,0,0,1,0, 0,0,1,1,0, 0,0,0,0,0, 1,1] (22 positions)
   *
   * <p>Active fields: pos 0: A: byte, pos 2: B: List[uint16, 123], pos 4: C: ProgressiveBitlist,
   * pos 8: D: ProgressiveList[uint64], pos 12: E: ProgressiveList[SmallTestStruct], pos 13: F:
   * ProgressiveList[ProgressiveList[VarTestStruct]], pos 20: G:
   * List[ProgressiveSingleFieldContainerTestStruct, 10], pos 21: H:
   * ProgressiveList[ProgressiveVarTestStruct]
   */
  private static SszContainerSchema<?> createProgressiveComplexTestStructSchema() {
    final SszContainerSchema<?> singleFieldSchema =
        SszContainerSchema.createProgressive(
            "ProgressiveSingleFieldContainerTestStruct",
            new boolean[] {true},
            List.of(NamedSchema.of("A", SszPrimitiveSchemas.BYTE_SCHEMA)));

    return SszContainerSchema.createProgressive(
        "ProgressiveComplexTestStruct",
        new boolean[] {
          true, false, true, false, true, false, false, false, true, false, false, false, true,
          true, false, false, false, false, false, false, true, true
        },
        List.of(
            NamedSchema.of("A", SszPrimitiveSchemas.BYTE_SCHEMA),
            NamedSchema.of("B", SszListSchema.create(UINT16_SCHEMA, 123)),
            NamedSchema.of("C", new SszProgressiveBitlistSchema()),
            NamedSchema.of("D", SszListSchema.createProgressive(SszPrimitiveSchemas.UINT64_SCHEMA)),
            NamedSchema.of("E", SszListSchema.createProgressive(new SmallTestStructSchema())),
            NamedSchema.of(
                "F",
                SszListSchema.createProgressive(
                    SszListSchema.createProgressive(new VarTestStructSchema()))),
            NamedSchema.of("G", SszListSchema.create(singleFieldSchema, 10)),
            NamedSchema.of(
                "H", SszListSchema.createProgressive(createProgressiveVarTestStructSchema()))));
  }

  private Map<String, String> formatSszContainer(final SszContainer container) {
    return container.getSchema().getFieldNames().stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                name -> format(container.get(container.getSchema().getFieldIndex(name)))));
  }

  private String format(final Object value) {
    return switch (value) {
      case SszByte sszByte -> Integer.toString(Byte.toUnsignedInt(sszByte.get()));
      case SszBytes4 sszBytes4 ->
          Long.toString(sszBytes4.get().getWrappedBytes().toLong(ByteOrder.LITTLE_ENDIAN));
      case SszBitlist sszBits -> sszBits.sszSerialize().toHexString();
      case SszByteList sszBytes -> sszBytes.sszSerialize().toHexString();
      case SszBitvector bitvector -> bitvector.sszSerialize().toHexString();
      case SszCollection<?> list -> list.stream().map(this::format).toList().toString();
      case SszContainer sszContainer -> formatSszContainer(sszContainer).toString();
      default -> Objects.toString(value, null);
    };
  }
}

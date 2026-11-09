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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchemaTest;

public class SszUnionTest implements SszDataTestBase {

  @Override
  public Stream<SszUnion> sszData() {
    RandomSszDataGenerator randomGen = new RandomSszDataGenerator();
    return SszUnionSchemaTest.testUnionSchemas()
        .flatMap(
            unionSchema ->
                Stream.concat(
                    Stream.of(unionSchema.getDefault()),
                    IntStream.range(0, unionSchema.getTypesCount())
                        .mapToObj(
                            i ->
                                unionSchema.createFromValue(
                                    i, randomGen.randomData(unionSchema.getChildSchema(i))))));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void getSelector_matchesDataSchema(final SszUnion data) {
    int selector = data.getSelector();
    SszSchema<?> expectedValueSchema = data.getSchema().getChildSchema(selector);

    assertThat(data.getValue().getSchema()).isEqualTo(expectedValueSchema);
  }

  @Test
  void sszSerialize_noneUnionEqualsToZeroByte() {
    SszUnionSchema<?> schema =
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA));
    SszUnion union = schema.createFromValue(0, SszNone.INSTANCE);

    assertThat(union.sszSerialize()).isEqualTo(Bytes.of(0));
  }

  @Test
  void hashTreeRoot_none() {
    SszUnionSchema<?> schema =
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA));
    SszUnion union = schema.createFromValue(0, SszNone.INSTANCE);

    assertThat(union.hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }
}

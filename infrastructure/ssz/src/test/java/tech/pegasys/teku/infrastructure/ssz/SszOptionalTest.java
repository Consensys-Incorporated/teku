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

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchemaTest;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public class SszOptionalTest implements SszDataTestBase {

  @Override
  @SuppressWarnings("unchecked")
  public Stream<SszOptional<?>> sszData() {
    final RandomSszDataGenerator randomGen = new RandomSszDataGenerator();
    return SszOptionalSchemaTest.testOptionalSchemas()
        .map(
            optionalSchema ->
                ((SszOptionalSchema<SszData, ?>) optionalSchema)
                    .createFromValue(
                        Optional.of(randomGen.randomData(optionalSchema.getChildSchema()))));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void getChildSchema_matchesDataSchema(final SszOptional<?> data) {
    final SszSchema<?> expectedValueSchema = data.getSchema().getChildSchema();

    assertThat(data.getValue().orElseThrow().getSchema()).isEqualTo(expectedValueSchema);
  }

  @Test
  void sszSerialize_emptyOptionalEqualsToZeroBytes() {
    final SszOptionalSchema<?, ?> schema =
        SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA);
    final SszOptional<?> sszOptional = schema.createFromValue(Optional.empty());
    assertThat(sszOptional.sszSerialize()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void hashTreeRoot_empty() {
    final SszOptionalSchema<?, ?> schema =
        SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA);
    final SszOptional<?> sszOptional = schema.createFromValue(Optional.empty());

    assertThat(sszOptional.hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }
}

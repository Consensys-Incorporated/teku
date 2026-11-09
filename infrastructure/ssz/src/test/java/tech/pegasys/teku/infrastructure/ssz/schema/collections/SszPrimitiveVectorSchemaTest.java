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
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszPrimitiveVectorSchemaTest extends SszVectorSchemaTestBase {

  @Override
  public Stream<SszPrimitiveVectorSchema<?, ?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 2),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 31),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 33),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 63),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 65),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 2),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 31),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 32),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 33),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 63),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 64),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 65),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 2),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 15),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 16),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 17),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10));
  }

  @Test
  void sanityTest() {
    SszPrimitiveVectorSchema<UInt64, SszUInt64, ?> schema =
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    SszPrimitiveVector<UInt64, SszUInt64> defaultVector = schema.getDefault();
    assertThat(defaultVector).hasSize(10).containsOnly(SszUInt64.of(UInt64.ZERO));
    assertThat(defaultVector.asListUnboxed()).hasSize(10).containsOnly(UInt64.ZERO);

    List<UInt64> uints =
        LongStream.range(1, 11).mapToObj(UInt64::valueOf).collect(Collectors.toList());
    SszPrimitiveVector<UInt64, SszUInt64> vector1 = schema.of(uints);
    assertThat(vector1.asListUnboxed()).hasSize(10).containsSequence(uints);
  }
}

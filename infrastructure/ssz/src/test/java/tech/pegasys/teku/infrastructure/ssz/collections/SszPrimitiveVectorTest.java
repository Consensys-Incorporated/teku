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

package tech.pegasys.teku.infrastructure.ssz.collections;

import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;

public class SszPrimitiveVectorTest implements SszMutablePrimitiveCollectionTestBase {

  public Stream<SszPrimitiveVectorSchema<?, ?, ?>> sszSchemas() {
    return Stream.of(
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 15),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 16),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 17),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 15),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 16),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 17),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 3),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 4),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 5),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 15),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 31),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 32),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA, 33),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 31),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32),
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 33));
  }

  @Override
  public Stream<SszPrimitiveVector<?, ?>> sszData() {
    RandomSszDataGenerator generator = new RandomSszDataGenerator();

    return sszSchemas()
        .flatMap(schema -> Stream.of(schema.getDefault(), generator.randomData(schema)));
  }
}

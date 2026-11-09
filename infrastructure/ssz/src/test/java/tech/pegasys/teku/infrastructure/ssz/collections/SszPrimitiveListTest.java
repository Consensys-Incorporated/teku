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
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;

public class SszPrimitiveListTest implements SszMutablePrimitiveListTestBase {

  public Stream<SszPrimitiveListSchema<?, ?, ?>> sszSchemas() {
    return Stream.of(
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 15),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 16),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 17),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 300),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 15),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 16),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 17),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 300),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 31),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 33),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 300));
  }

  @Override
  public Stream<SszPrimitiveList<?, ?>> sszData() {
    RandomSszDataGenerator generator = new RandomSszDataGenerator();
    return sszSchemas()
        .flatMap(
            schema ->
                Stream.of(
                    schema.getDefault(), // empty
                    generator
                        .withMaxListSize((int) (schema.getMaxLength() / 2))
                        .randomData(schema), // half filled
                    generator
                        .withMaxListSize((int) schema.getMaxLength())
                        .randomData(schema)) // full
            );
  }
}

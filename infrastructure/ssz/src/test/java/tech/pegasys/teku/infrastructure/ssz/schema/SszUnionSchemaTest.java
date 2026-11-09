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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestByteVectorContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.VariableSizeContainer;

public class SszUnionSchemaTest extends SszSchemaTestBase {

  public static Stream<SszUnionSchema<?>> testUnionSchemas() {
    return Stream.of(
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BIT_SCHEMA)),
        SszUnionSchema.create(
            List.of(
                SszPrimitiveSchemas.NONE_SCHEMA,
                SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32))),
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.BIT_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(
            List.of(
                SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32),
                SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(
            List.of(
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 16),
                SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32))),
        SszUnionSchema.create(
            List.of(
                SszPrimitiveSchemas.BIT_SCHEMA,
                SszPrimitiveSchemas.BYTES4_SCHEMA,
                SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(
            List.of(
                SszPrimitiveSchemas.BIT_SCHEMA,
                SszPrimitiveSchemas.BYTES4_SCHEMA,
                SszPrimitiveSchemas.BIT_SCHEMA)),
        SszUnionSchema.create(List.of(SszPrimitiveSchemas.BIT_SCHEMA)),
        SszUnionSchema.create(
            List.of(SszPrimitiveSchemas.BIT_SCHEMA, VariableSizeContainer.SSZ_SCHEMA)),
        SszUnionSchema.create(
            List.of(TestByteVectorContainer.SSZ_SCHEMA, SszPrimitiveSchemas.BIT_SCHEMA)));
  }

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return testUnionSchemas();
  }
}

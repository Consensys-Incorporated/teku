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

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;

public class SszVectorSchemaTest extends SszVectorSchemaTestBase {

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return SszVectorSchemaTestBase.complexVectorSchemas();
  }

  @Test
  void create_shouldCreateSpecializedSchema() {
    Assertions.assertThat(SszVectorSchema.create(SszPrimitiveSchemas.BIT_SCHEMA, 10))
        .isInstanceOf(SszBitvectorSchema.class);
    Assertions.assertThat(SszVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10))
        .isInstanceOf(SszBytes32VectorSchema.class);
    Assertions.assertThat(SszVectorSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 10))
        .isInstanceOf(SszPrimitiveVectorSchema.class);
  }
}

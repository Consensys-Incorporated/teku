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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.custom.SszCustomListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

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
}

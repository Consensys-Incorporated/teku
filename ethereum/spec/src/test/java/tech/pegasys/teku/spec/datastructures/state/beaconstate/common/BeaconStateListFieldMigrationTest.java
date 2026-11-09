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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class BeaconStateListFieldMigrationTest {

  @Test
  void shouldRematerializeBoundedListWithProgressiveTargetSchema() {
    final SszListSchema<SszUInt64, ?> sourceSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4);
    final SszProgressiveListSchema<SszUInt64> targetSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszList<SszUInt64> source =
        sourceSchema.createFromElements(
            List.of(SszUInt64.of(UInt64.ONE), SszUInt64.of(UInt64.valueOf(2))));

    final SszList<SszUInt64> result =
        BeaconStateListFieldMigration.toTargetList(targetSchema, source);

    assertThat(result.getSchema()).isSameAs(targetSchema);
    assertThat(result.sszSerialize()).isEqualTo(source.sszSerialize());
    assertThat(result.get(0).get()).isEqualTo(UInt64.ONE);
    assertThat(result.get(1).get()).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void shouldRematerializeBoundedUInt64ListWithProgressiveTargetSchema() {
    final SszUInt64ListSchema<?> sourceSchema = SszUInt64ListSchema.create(4);
    final SszProgressiveUInt64ListSchema targetSchema = SszProgressiveUInt64ListSchema.create();
    final SszUInt64List source =
        sourceSchema.createFromElements(
            List.of(SszUInt64.of(UInt64.ONE), SszUInt64.of(UInt64.valueOf(2))));

    final SszUInt64List result =
        BeaconStateListFieldMigration.toTargetUInt64List(targetSchema, source);

    assertThat(result.getSchema()).isSameAs(targetSchema);
    assertThat(result.sszSerialize()).isEqualTo(source.sszSerialize());
    assertThat(result.get(0).get()).isEqualTo(UInt64.ONE);
    assertThat(result.get(1).get()).isEqualTo(UInt64.valueOf(2));
  }
}

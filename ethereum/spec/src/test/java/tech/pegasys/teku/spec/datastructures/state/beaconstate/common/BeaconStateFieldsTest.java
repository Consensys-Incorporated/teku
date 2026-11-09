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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateFieldsTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldNotCauseModificationsWhenCopyingCommonFields() {
    final BeaconState source = dataStructureUtil.randomBeaconState();
    final BeaconState result =
        source.updated(target -> BeaconStateFields.copyCommonFieldsFromSource(target, source));

    assertThat(result).isEqualTo(source);
  }

  @Test
  @SuppressWarnings("unchecked")
  void toProgressiveListSchema_shouldPreserveUInt8JsonArrayShape() throws JsonProcessingException {
    final SszListSchema<?, ?> boundedSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT8_SCHEMA, 4);

    final SszSchema<?> progressiveSchema = BeaconStateFields.toProgressiveListSchema(boundedSchema);

    assertThat(progressiveSchema).isInstanceOf(SszByteListSchema.class);
    assertThat(((SszListSchema<?, ?>) progressiveSchema).getElementSchema())
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);

    final SszByteListSchema<SszByteList> byteListSchema =
        (SszByteListSchema<SszByteList>) progressiveSchema;
    final SszByteList byteList =
        byteListSchema.createFromElements(
            List.of(SszByte.asUInt8(1), SszByte.asUInt8(2), SszByte.asUInt8(3)));

    assertThat(byteList.get(0).getSchema()).isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
    assertThat(JsonUtil.serialize(byteList, byteListSchema.getJsonTypeDefinition()))
        .isEqualTo("[\"1\",\"2\",\"3\"]");
  }
}

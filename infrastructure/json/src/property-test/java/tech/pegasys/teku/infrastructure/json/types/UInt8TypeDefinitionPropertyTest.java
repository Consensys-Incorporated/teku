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

package tech.pegasys.teku.infrastructure.json.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class UInt8TypeDefinitionPropertyTest {
  @Property
  void roundTrip(@ForAll final Byte value) throws JsonProcessingException {
    assertRoundTrip(value, CoreTypes.UINT8_TYPE);
  }

  @Property
  void shouldRejectInvalidRange(@ForAll final int value) throws JsonProcessingException {
    final String serialized = "\"" + value + "\"";
    if (value >= 0 && value <= 255) {
      assertThat(JsonUtil.parse(serialized, CoreTypes.UINT8_TYPE)).isEqualTo((byte) value);
    } else {
      assertThatThrownBy(() -> JsonUtil.parse(serialized, CoreTypes.UINT8_TYPE))
          .isInstanceOfAny(JsonProcessingException.class, IllegalArgumentException.class);
    }
  }
}

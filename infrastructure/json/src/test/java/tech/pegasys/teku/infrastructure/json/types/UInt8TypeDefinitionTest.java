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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UInt8TypeDefinitionTest {

  @ParameterizedTest
  @MethodSource("testValues")
  void shouldSerializeAsDecimal(final byte value, final String serialized) {
    assertThat(CoreTypes.UINT8_TYPE.serializeToString(value)).isEqualTo(serialized);
  }

  @ParameterizedTest
  @MethodSource("testValues")
  void shouldDeserializeFromDecimal(final byte value, final String serialized) {
    assertThat(CoreTypes.UINT8_TYPE.deserializeFromString(serialized)).isEqualTo(value);
  }

  @Test
  void shouldRejectNegativeInteger() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CoreTypes.UINT8_TYPE.deserializeFromString(Integer.toUnsignedString(-1)));
  }

  static Stream<Arguments> testValues() {
    return Stream.of(
        Arguments.of(Byte.MIN_VALUE, "128"),
        Arguments.of((byte) -1, "255"),
        Arguments.of((byte) 0, "0"),
        Arguments.of((byte) 1, "1"),
        Arguments.of(Byte.MAX_VALUE, "127"));
  }
}

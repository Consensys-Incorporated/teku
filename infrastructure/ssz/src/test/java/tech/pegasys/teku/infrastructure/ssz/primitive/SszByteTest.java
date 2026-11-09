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

package tech.pegasys.teku.infrastructure.ssz.primitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;

class SszByteTest {

  @Test
  void of_returnsCorrectValueForAllBytes() {
    for (int i = -128; i <= 127; i++) {
      final byte value = (byte) i;
      assertThat(SszByte.of(value).get()).isEqualTo(value);
      assertThat(SszByte.of(value).getSchema()).isEqualTo(SszPrimitiveSchemas.BYTE_SCHEMA);
    }
  }

  @Test
  void asUInt8_returnsCorrectValueForAllUnsignedBytes() {
    for (int i = 0; i <= 255; i++) {
      assertThat(SszByte.asUInt8(i).get()).isEqualTo((byte) i);
      assertThat(SszByte.asUInt8(i).getSchema()).isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
    }
  }

  @Test
  void asUInt8_rejectsValuesOutsideUnsignedByteRange() {
    assertThatThrownBy(() -> SszByte.asUInt8(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SszByte.asUInt8(256)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_internsInstancesAcrossAllValues() {
    for (int i = -128; i <= 127; i++) {
      final byte value = (byte) i;
      assertThat(SszByte.of(value)).isSameAs(SszByte.of(value));
      // int and byte overloads must resolve to the same interned instance
      assertThat(SszByte.of((int) value)).isSameAs(SszByte.of(value));
    }
  }

  @Test
  void asUInt8_internsInstancesAcrossAllValues() {
    for (int i = 0; i <= 255; i++) {
      assertThat(SszByte.asUInt8(i)).isSameAs(SszByte.asUInt8(i));
      assertThat(SszByte.asUInt8((byte) i)).isSameAs(SszByte.asUInt8(i));
    }
  }

  @Test
  void of_intOverloadTruncatesConsistentlyWithByteOverload() {
    // values outside the signed byte range must truncate to the same interned instance
    assertThat(SszByte.of(256)).isSameAs(SszByte.of((byte) 0));
    assertThat(SszByte.of(-1)).isSameAs(SszByte.of((byte) -1));
    assertThat(SszByte.of(255)).isSameAs(SszByte.of((byte) -1));
  }

  @Test
  void zero_isTheInternedZeroInstance() {
    assertThat(SszByte.ZERO).isSameAs(SszByte.of((byte) 0));
    assertThat(SszByte.ZERO.get()).isEqualTo((byte) 0);
  }

  @Test
  void byteAndUInt8InstancesAreDistinctButValueEqual() {
    // different schemas -> different interned instances ...
    assertThat(SszByte.of((byte) 5)).isNotSameAs(SszByte.asUInt8(5));
    // ... but equality is value-based (schema-independent), preserving existing semantics
    assertThat(SszByte.of((byte) 5)).isEqualTo(SszByte.asUInt8(5));
  }
}

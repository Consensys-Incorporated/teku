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

package tech.pegasys.teku.infrastructure.unsigned;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ByteUtilTest {

  @Test
  public void byteToUnsignedInt_0xff() {
    final int result = ByteUtil.toUnsignedInt((byte) -1);
    assertThat(result).isEqualTo(255);
  }

  @Test
  public void byteToUnsignedInt_0x01() {
    final int result = ByteUtil.toUnsignedInt((byte) 1);
    assertThat(result).isEqualTo(1);
  }

  @Test
  public void toByteExact_maxValue() {
    final int value = 255;

    byte byteValue = ByteUtil.toByteExact(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExactUnsigned_maxValue() {
    final int value = 255;

    byte byteValue = ByteUtil.toByteExactUnsigned(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExact_minNegativeValue() {
    final int value = -128;
    final byte byteValue = ByteUtil.toByteExact(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExactUnsigned_minNegativeValue() {
    final int value = -128;
    assertThatThrownBy(() -> ByteUtil.toByteExactUnsigned(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied int value (-128) is negative");
  }

  @Test
  public void toByteExact_0xFFasNegative() {
    final int value = -1;
    final byte byteValue = ByteUtil.toByteExact(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExactUnsigned_0xFFasNegative() {
    final int value = -1;
    assertThatThrownBy(() -> ByteUtil.toByteExactUnsigned(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied int value (-1) is negative");
  }

  @Test
  public void toByteExact_minPositiveValue() {
    final int value = 1;

    byte byteValue = ByteUtil.toByteExact(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExactUnsigned_minPositiveValue() {
    final int value = 1;

    byte byteValue = ByteUtil.toByteExactUnsigned(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExact_zero() {
    final int value = 0;

    byte byteValue = ByteUtil.toByteExact(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExactUnsigned_zero() {
    final int value = 0;

    byte byteValue = ByteUtil.toByteExactUnsigned(value);
    assertThat(byteValue).isEqualTo((byte) value);
  }

  @Test
  public void toByteExact_valueTooLarge() {
    assertThatThrownBy(() -> ByteUtil.toByteExact(256))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied int value (256) overflows byte");
  }

  @Test
  public void toByteExactUnsigned_valueTooLarge() {
    assertThatThrownBy(() -> ByteUtil.toByteExactUnsigned(256))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied int value (256) overflows byte");
  }

  @Test
  public void toByteExact_valueTooSmall() {
    assertThatThrownBy(() -> ByteUtil.toByteExact(-129))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied int value (-129) overflows byte");
  }

  @Test
  public void toByteExactUnsigned_valueTooSmall() {
    assertThatThrownBy(() -> ByteUtil.toByteExactUnsigned(-129))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

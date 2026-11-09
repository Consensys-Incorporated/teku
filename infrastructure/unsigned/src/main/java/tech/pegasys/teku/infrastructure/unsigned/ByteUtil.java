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

import static com.google.common.base.Preconditions.checkArgument;

public abstract class ByteUtil {

  /**
   * Convert a byte value to a positive integer value
   *
   * @param value A byte value
   * @return The corresponding positive integer value
   */
  public static int toUnsignedInt(final byte value) {
    final int intValue = value;
    if (intValue < 0) {
      return intValue + 256;
    } else {
      return intValue;
    }
  }

  /**
   * Cast supplied integer to a byte, throwing if value overflows byte.
   *
   * @param value An integer value to be interpreted as a byte
   * @return {@code value} safely cast as a byte
   * @throws IllegalArgumentException If value overflows byte
   */
  public static byte toByteExact(final int value) throws IllegalArgumentException {
    return castToByteExact(value);
  }

  /**
   * Cast supplied positive integer to a byte, throwing if value overflows byte.
   *
   * @param value An unsigned integer value to be interpreted as a byte
   * @return {@code value} safely cast as a byte
   * @throws IllegalArgumentException If value overflows byte or is negative
   */
  public static byte toByteExactUnsigned(final int value) throws IllegalArgumentException {
    checkArgument(value >= 0, "Supplied int value (%s) is negative", value);
    return castToByteExact(value);
  }

  static byte castToByteExact(final int value) throws IllegalArgumentException {
    checkArgument(value >= -128 && value <= 255, "Supplied int value (%s) overflows byte", value);
    return (byte) value;
  }
}

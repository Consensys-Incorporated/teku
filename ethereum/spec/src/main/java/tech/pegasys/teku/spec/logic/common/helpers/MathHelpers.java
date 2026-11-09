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

package tech.pegasys.teku.spec.logic.common.helpers;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MathHelpers {

  public static final UInt64 MAX_SQRT = UInt64.valueOf(4294967295L);

  public static long integerSquareRoot(final long n) {
    checkArgument(n >= 0, "Cannot calculate integerSquareRoot of negative number");
    return integerSquareRootInternal(n);
  }

  public static UInt64 integerSquareRoot(final UInt64 n) {
    if (n.isMaxValue()) {
      return MAX_SQRT;
    }
    return UInt64.valueOf(integerSquareRootInternal(n.longValue()));
  }

  private static long integerSquareRootInternal(final long n) {
    long x = n;
    long y = Long.divideUnsigned(x + 1, 2);
    while (Long.compareUnsigned(y, x) < 0) {
      x = y;
      final long nDividedByX = Long.divideUnsigned(n, x);
      y = Long.divideUnsigned(x + nDividedByX, 2);
    }
    return x;
  }

  public static Bytes uintTo4Bytes(final long value) {
    // little endian
    final byte[] bytes =
        new byte[] {
          (byte) (value & 0xFF),
          (byte) ((value >> 8) & 0xFF),
          (byte) ((value >> 16) & 0xFF),
          (byte) ((value >> 24) & 0xFF)
        };
    return Bytes.wrap(bytes, 0, 4);
  }

  public static Bytes uintTo8Bytes(final long value) {
    // little endian
    final byte[] bytes =
        new byte[] {
          (byte) (value & 0xFF),
          (byte) ((value >> 8) & 0xFF),
          (byte) ((value >> 16) & 0xFF),
          (byte) ((value >> 24) & 0xFF),
          (byte) ((value >> 32) & 0xFF),
          (byte) ((value >> 40) & 0xFF),
          (byte) ((value >> 48) & 0xFF),
          (byte) ((value >> 56) & 0xFF)
        };
    return Bytes.wrap(bytes, 0, 8);
  }

  public static Bytes32 uintTo32Bytes(final long value) {
    // little endian
    final byte[] bytes =
        new byte[] {
          (byte) (value & 0xFF),
          (byte) ((value >> 8) & 0xFF),
          (byte) ((value >> 16) & 0xFF),
          (byte) ((value >> 24) & 0xFF),
          (byte) ((value >> 32) & 0xFF),
          (byte) ((value >> 40) & 0xFF),
          (byte) ((value >> 48) & 0xFF),
          (byte) ((value >> 56) & 0xFF),
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0
        };
    return Bytes32.wrap(bytes);
  }

  public static Bytes uint64ToBytes(final long value) {
    return uintTo8Bytes(value);
  }

  public static Bytes uint64ToBytes(final UInt64 value) {
    return uintTo8Bytes(value.longValue());
  }

  public static Bytes32 uintToBytes32(final long value) {
    return uintTo32Bytes(value);
  }

  static Bytes32 uintToBytes32(final UInt64 value) {
    return uintToBytes32(value.longValue());
  }

  public static UInt64 bytesToUInt64(final Bytes data) {
    return UInt64.fromLongBits(data.toLong(ByteOrder.LITTLE_ENDIAN));
  }

  public static Bytes uint256ToBytes(final UInt256 number) {
    final Bytes intBytes =
        Bytes.wrap(number.toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN).toByteArray())
            .trimLeadingZeros();
    // We should keep 32 bytes
    return Bytes32.leftPad(intBytes);
  }

  public static int floorLog2(final int n) {
    checkArgument(n >= 0, "Cannot calculate floorLog2 of negative number");
    return 31 - Integer.numberOfLeadingZeros(n);
  }
}

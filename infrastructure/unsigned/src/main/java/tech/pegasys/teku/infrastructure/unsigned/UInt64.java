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

import java.math.BigInteger;
import java.util.stream.Stream;

/** An unsigned 64-bit integer. All instances are immutable. */
public final class UInt64 implements Comparable<UInt64> {

  private static final long UNSIGNED_MASK = 0x7fffffffffffffffL;
  private static final long HIGH_MASK = 0xffffffff00000000L;
  private static final long LOW_MASK = 0x00000000ffffffffL;

  public static final UInt64 ZERO = new UInt64(0);
  public static final UInt64 ONE = new UInt64(1);
  public static final UInt64 THIRTY_TWO_ETH = new UInt64(32_000_000_000L);
  public static final UInt64 MAX_VALUE = new UInt64(-1L);

  private final long value;

  private UInt64(final long value) {
    this.value = value;
  }

  /**
   * Create a UInt64 from a long value. The value is treated as signed and must be >= 0.
   *
   * @param value the signed value to convert to UInt64
   * @return UInt64 value equal to the supplied signed value
   * @throws IllegalArgumentException if the value is negative
   */
  public static UInt64 valueOf(final long value) {
    checkPositive(value);
    return fromLongBits(value);
  }

  /**
   * Parse the string value into a UInt64.
   *
   * @param value the value to parse
   * @return UInt64 parsed from value
   * @throws NumberFormatException if value is not a valid unsigned long.
   */
  public static UInt64 valueOf(final String value) {
    return fromLongBits(Long.parseUnsignedLong(value));
  }

  /**
   * Create a UInt64 from a {@link BigInteger} value. The value is treated as signed and must be >=
   * 0 and less than {@link #MAX_VALUE}.
   *
   * @param value the signed value to convert to UInt64
   * @return UInt64 value equal to the supplied signed value
   * @throws IllegalArgumentException if the value is negative or too large
   */
  public static UInt64 valueOf(final BigInteger value) {
    checkArgument(
        value.signum() >= 0 && value.bitLength() <= Long.SIZE,
        "value (%s) is outside the range for uint64",
        value);
    return fromLongBits(value.longValue());
  }

  /**
   * Create a UInt64 from an unsigned long. The value is treated as unsigned.
   *
   * @param value the unsigned value to create as a UInt64.
   * @return the created UInt64.
   */
  public static UInt64 fromLongBits(final long value) {
    if (value == 0L) {
      return ZERO;
    }
    if (value == 32_000_000_000L) {
      return THIRTY_TWO_ETH;
    }
    if (value == -1L) {
      return MAX_VALUE;
    }
    return new UInt64(value);
  }

  /**
   * Return the result of adding this value and the specified one.
   *
   * @param other the value to add. Treated as signed.
   * @return a new UInt64 equal to this value + the specified value.
   * @throws ArithmeticException if the result exceeds {@link #MAX_VALUE}
   * @throws IllegalArgumentException if the specified value is negative
   */
  public UInt64 plus(final long other) {
    checkPositive(other);
    return plus(this.value, other);
  }

  /**
   * Increment this value by one and return the result.
   *
   * @return The result of incrementing this value by 1.
   */
  public UInt64 increment() {
    return plus(value, 1);
  }

  /**
   * Decrement this value by one and return the result.
   *
   * @return The result of decrementing this value by 1.
   */
  public UInt64 decrement() {
    return minus(value, 1);
  }

  /**
   * Decrement this value by one and return the result. If trying to decrement from ZERO, still
   * returns the ZERO
   *
   * @return The result of decrementing this value by 1.
   */
  public UInt64 safeDecrement() {
    if (value == 0L) {
      return ZERO;
    }
    return minus(value, 1);
  }

  /**
   * Return the result of adding this value and the specified one.
   *
   * @param other the unsigned value to add.
   * @return a new UInt64 equal to this value + the specified value.
   * @throws ArithmeticException if the result exceeds {@link #MAX_VALUE}
   */
  public UInt64 plus(final UInt64 other) {
    return plus(value, other.value);
  }

  private UInt64 plus(final long longBits1, final long longBits2) {
    if (longBits1 != 0 && Long.compareUnsigned(longBits2, MAX_VALUE.longValue() - longBits1) > 0) {
      throw new ArithmeticException("uint64 overflow");
    }
    return fromLongBits(longBits1 + longBits2);
  }

  /**
   * Return the result of subtracting the specified value from this one.
   *
   * @param other the value to subtract, treated as signed.
   * @return a new UInt64 equal to this value minus the specified value.
   * @throws ArithmeticException if the result is less than zero.
   * @throws IllegalArgumentException if the specified value is negative
   */
  public UInt64 minus(final long other) {
    checkPositive(other);
    return minus(value, other);
  }

  /**
   * Return the result of subtracting the specified value from this one.
   *
   * @param other the value to subtract.
   * @return a new UInt64 equal to this value minus the specified value.
   * @throws ArithmeticException if the result is less than zero.
   */
  public UInt64 minus(final UInt64 other) {
    return minus(value, other.value);
  }

  private UInt64 minus(final long longBits1, final long longBits2) {
    if (Long.compareUnsigned(longBits1, longBits2) < 0) {
      throw new ArithmeticException("uint64 underflow");
    }
    return fromLongBits(longBits1 - longBits2);
  }

  public UInt64 minusMinZero(final long other) {
    return minusMinZero(valueOf(other));
  }

  public UInt64 minusMinZero(final UInt64 other) {
    return isGreaterThan(other) ? minus(other) : ZERO;
  }

  /**
   * Return the result of multiplying the specified value with this one.
   *
   * @param other the value to multiply, treated as signed.
   * @return a new UInt64 equal to this value times the specified value.
   * @throws ArithmeticException if the result is exceeds {@link #MAX_VALUE}
   * @throws IllegalArgumentException if the specified value is negative
   */
  public UInt64 times(final long other) {
    checkPositive(other);
    return times(value, other);
  }

  /**
   * Return the result of multiplying the specified value with this one.
   *
   * @param other the value to multiply.
   * @return a new UInt64 equal to this value times the specified value.
   * @throws ArithmeticException if the result is exceeds {@link #MAX_VALUE}
   */
  public UInt64 times(final UInt64 other) {
    return times(value, other.value);
  }

  /** Naive long-multiplication is quite efficient */
  private UInt64 times(final long longBits1, final long longBits2) {
    if (Long.numberOfLeadingZeros(longBits1) + Long.numberOfLeadingZeros(longBits2) >= 64) {
      return UInt64.fromLongBits(longBits1 * longBits2);
    }
    final long longBits1Hi = longBits1 >>> 32;
    final long longBits1Lo = longBits1 & LOW_MASK;
    final long longBits2Hi = longBits2 >>> 32;
    final long longBits2Lo = longBits2 & LOW_MASK;
    if (longBits1Hi * longBits2Hi != 0) {
      throw new ArithmeticException("uint64 overflow");
    }
    // One or the other of longBits1Hi and longBits2Hi is zero
    final long crossProduct =
        (longBits1Hi == 0) ? longBits1Lo * longBits2Hi : longBits1Hi * longBits2Lo;
    if ((crossProduct & HIGH_MASK) != 0) {
      throw new ArithmeticException("uint64 overflow");
    }
    return plus(crossProduct << 32, longBits1Lo * longBits2Lo);
  }

  /**
   * Return the result of dividing this value by the specified value.
   *
   * @param divisor the value to divide by, treated as signed.
   * @return a new UInt64 equal to this value divided by the specified value.
   * @throws ArithmeticException if the specified divisor is 0.
   * @throws IllegalArgumentException if the specified value is negative
   */
  public UInt64 dividedBy(final long divisor) {
    checkPositive(divisor);
    return dividedBy(value, divisor);
  }

  /**
   * Return the result of dividing this value by the specified value.
   *
   * @param divisor the value to divide by.
   * @return a new UInt64 equal to this value divided by the specified value.
   * @throws ArithmeticException if the specified divisor is 0.
   */
  public UInt64 dividedBy(final UInt64 divisor) {
    return dividedBy(value, divisor.value);
  }

  private UInt64 dividedBy(final long unsignedDividend, final long unsignedDivisor) {
    return fromLongBits(Long.divideUnsigned(unsignedDividend, unsignedDivisor));
  }

  /**
   * Returns this value modulo the specified value.
   *
   * @param divisor the divisor, treated as signed.
   * @return a new UInt64 equal to this value modulo the specified value.
   * @throws ArithmeticException if the specified divisor is 0.
   * @throws IllegalArgumentException if the specified value is negative
   */
  public UInt64 mod(final long divisor) {
    checkPositive(divisor);
    return mod(value, divisor);
  }

  /**
   * Returns this value modulo the specified value.
   *
   * @param divisor the divisor.
   * @return a new UInt64 equal to this value modulo the specified value.
   * @throws ArithmeticException if the specified divisor is 0.
   */
  public UInt64 mod(final UInt64 divisor) {
    return mod(value, divisor.value);
  }

  private UInt64 mod(final long dividendBits, final long divisorBits) {
    return fromLongBits(Long.remainderUnsigned(dividendBits, divisorBits));
  }

  /**
   * Return the larger of this value or the specified value.
   *
   * @param other the value to compare with
   * @return the larger value
   */
  public UInt64 max(final UInt64 other) {
    return compareTo(other) >= 0 ? this : other;
  }

  /**
   * Return the larger of this value or the specified value.
   *
   * @param other the value to compare with, treated as signed
   * @return the larger value
   */
  public UInt64 max(final long other) {
    checkPositive(other);
    return Long.compareUnsigned(value, other) >= 0 ? this : UInt64.valueOf(other);
  }

  /**
   * Return the smaller of this value or the specified value.
   *
   * @param other the value to compare with
   * @return the larger value
   */
  public UInt64 min(final UInt64 other) {
    return compareTo(other) >= 0 ? other : this;
  }

  /**
   * Return the smaller of this value or the specified value.
   *
   * @param other the value to compare with, treated as signed
   * @return the larger value
   */
  public UInt64 min(final long other) {
    checkPositive(other);
    return Long.compareUnsigned(value, other) <= 0 ? this : UInt64.valueOf(other);
  }

  @Override
  public int compareTo(final UInt64 o) {
    return Long.compareUnsigned(value, o.value);
  }

  public int compareTo(final long other) {
    checkPositive(other);
    return Long.compareUnsigned(value, other);
  }

  /**
   * Returns true if this value is zero.
   *
   * @return true if this value is zero.
   */
  public boolean isZero() {
    return value == 0;
  }

  /**
   * Returns true if the value is 32_000_000_000L
   *
   * @return true if the value is 32_000_000_000L
   */
  public boolean isThirtyTwoEth() {
    return value == 32_000_000_000L;
  }

  public boolean isMaxValue() {
    return value == -1L;
  }

  /**
   * Returns true if this value is strictly greater than the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is strictly greater than the specified value
   */
  public boolean isGreaterThan(final UInt64 other) {
    return compareTo(other) > 0;
  }

  /**
   * Returns true if this value is strictly greater than the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is strictly greater than the specified value
   */
  public boolean isGreaterThan(final long other) {
    return compareTo(other) > 0;
  }

  /**
   * Returns true if this value is greater than or equal to the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is greater or equal than the specified value
   */
  public boolean isGreaterThanOrEqualTo(final UInt64 other) {
    return compareTo(other) >= 0;
  }

  /**
   * Returns true if this value is greater than or equal to the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is greater or equal than the specified value
   */
  public boolean isGreaterThanOrEqualTo(final long other) {
    return compareTo(other) >= 0;
  }

  /**
   * Returns true if this value is strictly less than the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is strictly less than the specified value
   */
  public boolean isLessThan(final UInt64 other) {
    return compareTo(other) < 0;
  }

  /**
   * Returns true if this value is strictly less than the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is strictly less than the specified value
   */
  public boolean isLessThan(final long other) {
    return compareTo(other) < 0;
  }

  /**
   * Returns true if this value is less than or equal to the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is less than or equal to the specified value
   */
  public boolean isLessThanOrEqualTo(final UInt64 other) {
    return compareTo(other) <= 0;
  }

  /**
   * Returns true if this value is less than or equal to the specified value.
   *
   * @param other the value to compare to
   * @return true if this value is less than or equal to the specified value
   */
  public boolean isLessThanOrEqualTo(final long other) {
    return compareTo(other) <= 0;
  }

  /**
   * Returns the value as a long. If this value exceeds {@link Long#MAX_VALUE} the result will be
   * negative.
   *
   * @return this value as an unsigned long
   */
  public long longValue() {
    return value;
  }

  /**
   * This value as an int.
   *
   * @return this value as a signed int.
   * @throws ArithmeticException if the value is greater than {@link Integer#MAX_VALUE}
   */
  public int intValue() {
    final int intValue = Math.toIntExact(value);
    if (intValue < 0) {
      throw new ArithmeticException("integer overflow");
    }
    return intValue;
  }

  /**
   * This value as a double.
   *
   * @return this value as a double.
   */
  public double doubleValue() {
    return value;
  }

  /**
   * Returns this value as a {@link BigInteger}
   *
   * @return this value as a BigInteger
   */
  public BigInteger bigIntegerValue() {
    return toUnsignedBigInteger(value);
  }

  // From Guava UnsignedLong.bigIntegerValue(). Apache 2 license.

  private static BigInteger toUnsignedBigInteger(final long value) {
    BigInteger bigInt = BigInteger.valueOf(value & UNSIGNED_MASK);
    if (value < 0) {
      bigInt = bigInt.setBit(Long.SIZE - 1);
    }
    return bigInt;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UInt64 uInt64 = (UInt64) o;
    return value == uInt64.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(value);
  }

  private static void checkPositive(final long other) {
    checkArgument(other >= 0, "value (%s) must be >= 0", other);
  }

  public static Stream<UInt64> range(final UInt64 fromInclusive, final UInt64 toExclusive) {
    return Stream.iterate(fromInclusive, value -> value.isLessThan(toExclusive), UInt64::increment);
  }

  public static Stream<UInt64> rangeClosed(final UInt64 fromInclusive, final UInt64 toInclusive) {
    return Stream.iterate(
        fromInclusive, value -> value.isLessThanOrEqualTo(toInclusive), UInt64::increment);
  }
}

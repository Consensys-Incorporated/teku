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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class UInt64Test {
  /**
   * Square root of the maximum uint64 value. Any two values less than or equal to this can be
   * safely multiplied together without overflowing a long.
   *
   * <p>If either value is greater than this number, overflow checks must be performed.
   */
  private static final long SQRT_MAX_VALUE = 4294967295L;

  private static final long SPECIAL_CASE_MULTIPLICAND = 64L;
  private static final long MAX_SAFE_VALUE_WITH_SPECIAL_CASE_MULTIPLICAND =
      Long.divideUnsigned(-1L, SPECIAL_CASE_MULTIPLICAND);

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, 1234, Long.MAX_VALUE})
  void fromLongBits_shouldAcceptAnyLong(final long longBits) {
    final UInt64 uInt64 = UInt64.fromLongBits(longBits);
    assertThat(uInt64.longValue()).isEqualTo(longBits);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 1341341252, Integer.MAX_VALUE})
  void intValue_shouldReturnIntValueForValuesThatFitInAPositiveInt(final int value) {
    final UInt64 uInt64 = UInt64.valueOf(value);
    assertThat(uInt64.intValue()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, Integer.MAX_VALUE + 1L, Integer.MIN_VALUE})
  void intValue_shouldThrowExceptionForValuesThatDoNotFitInAPositiveInt(final long value) {
    final UInt64 uInt64 = UInt64.fromLongBits(value);
    assertThatThrownBy(uInt64::intValue).isInstanceOf(ArithmeticException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -1243, Integer.MIN_VALUE})
  void valueOfLong_shouldRejectNegativeIntegers(final int value) {
    assertThatThrownBy(() -> UInt64.valueOf(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -50, -1, 0, 1, 234, Long.MAX_VALUE})
  void valueOfString_shouldParseValidNumbers(final long value) {
    final String string = Long.toUnsignedString(value);
    assertThat(UInt64.valueOf(string)).isEqualTo(UInt64.fromLongBits(value));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "1.1", "-1", "12418258345814504501542352345"})
  void valueOfString_shouldThrowExceptionWhenValueIsInvalid(final String value) {
    assertThatThrownBy(() -> UInt64.valueOf(value)).isInstanceOf(NumberFormatException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1, 29842984L, -28428L})
  void valueOfBigInteger_shouldConvertBigIntegerInValidRange(final long longBits) {
    final BigInteger bigInteger = new BigInteger(Long.toUnsignedString(longBits));
    assertThat(UInt64.valueOf(bigInteger)).isEqualTo(UInt64.fromLongBits(longBits));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "12418258345814504501542352345"})
  void valueOfBigInteger_shouldRejectBigIntegerOutsideValueRange(final String value) {
    final BigInteger bigInteger = new BigInteger(value);
    assertThatThrownBy(() -> UInt64.valueOf(bigInteger))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, -500, 0, 1, 500, Long.MIN_VALUE, Long.MAX_VALUE})
  void bigIntegerValue_shouldCreateUnsignedBigInteger(final long value) {
    final UInt64 uInt64 = UInt64.fromLongBits(value);
    final BigInteger bigInteger = uInt64.bigIntegerValue();
    assertThat(bigInteger.longValue()).isEqualTo(uInt64.longValue());
  }

  @Test
  void toString_shouldPrintAsUnsigned() {
    assertThat(UInt64.fromLongBits(-1).toString()).isEqualTo("18446744073709551615");
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -12431, -1, 0, 1, 492842, Long.MAX_VALUE})
  void equals_shouldOnlyBeEqualWhenValuesAreTheSame(final long value) {
    assertThat(UInt64.fromLongBits(value)).isEqualTo(UInt64.fromLongBits(value));
    assertThat(UInt64.fromLongBits(value)).isNotEqualTo(UInt64.fromLongBits(value + 1));
    assertThat(UInt64.fromLongBits(value)).isNotEqualTo(UInt64.fromLongBits(value - 1));
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -12431, -1, 0, 1, 492842, Long.MAX_VALUE})
  void hashCode_shouldBeEqualWhenValuesAreEqual(final long value) {
    assertThat(UInt64.fromLongBits(value).hashCode())
        .isEqualTo(UInt64.fromLongBits(value).hashCode());
  }

  @Test
  void hashCode_shouldNotAlwaysBeEqual() {
    assertThat(UInt64.fromLongBits(1).hashCode()).isNotEqualTo(UInt64.fromLongBits(7).hashCode());
  }

  @Test
  void compareTo_shouldCompareUnsigned() {
    final Long[] inputs = {1451L, Long.MAX_VALUE, -124234L, Long.MIN_VALUE, 0L, -1L, -5L, 1L};
    final List<UInt64> sortedUInt64s =
        Stream.of(inputs).map(UInt64::fromLongBits).sorted().collect(toList());
    assertThat(sortedUInt64s)
        .containsExactly(
            UInt64.fromLongBits(0),
            UInt64.fromLongBits(1),
            UInt64.fromLongBits(1451),
            UInt64.fromLongBits(Long.MAX_VALUE),
            UInt64.fromLongBits(Long.MIN_VALUE),
            UInt64.fromLongBits(-124234L),
            UInt64.fromLongBits(-5),
            UInt64.fromLongBits(-1));
  }

  @Test
  void compareTo_shouldBeReflexive() {
    // Should be compared unsigned to -1 is bigger than 1
    final UInt64 bigger = UInt64.fromLongBits(-1);
    final UInt64 smaller = UInt64.fromLongBits(1);
    assertThat(bigger).isGreaterThan(smaller);
    assertThat(smaller).isLessThan(bigger);
  }

  @Test
  void compareTo_shouldBeEqualWhenEqual() {
    assertThat(UInt64.fromLongBits(-675)).isEqualByComparingTo(UInt64.fromLongBits(-675));
  }

  @Test
  void compareTo_withLongLessThanZero() {
    assertThatThrownBy(() -> UInt64.valueOf(10).compareTo(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be >= 0");
  }

  @Test
  void compareTo_withNonZeroLong() {
    final UInt64 value = UInt64.valueOf(10);
    assertThat(value.compareTo(10)).isEqualTo(0);
    assertThat(value.compareTo(11)).isEqualTo(-1);
    assertThat(value.compareTo(1111)).isEqualTo(-1);
    assertThat(value.compareTo(9)).isEqualTo(1);
    assertThat(value.compareTo(0)).isEqualTo(1);
  }

  @Test
  void isZero_shouldBeTrueWhenZero() {
    assertThat(UInt64.ZERO.isZero()).isTrue();
    assertThat(UInt64.valueOf(0).isZero()).isTrue();
    assertThat(UInt64.valueOf(1).minus(1).isZero()).isTrue();
  }

  @Test
  void isZero_shouldBeFalseWhenNotZero() {
    assertThat(UInt64.ONE.isZero()).isFalse();
    assertThat(UInt64.MAX_VALUE.isZero()).isFalse();
    assertThat(UInt64.ZERO.plus(1).isZero()).isFalse();
    assertThat(UInt64.valueOf(2).isZero()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("comparableNumbers")
  void greaterThan_shouldReturnTrueWhenNumberIsStrictlyGreater(
      final UInt64 smaller, final UInt64 bigger) {
    assertThat(smaller.isGreaterThan(bigger)).isFalse();
    assertThat(bigger.isGreaterThan(smaller)).isTrue();

    // Must be strictly greater than
    assertThat(smaller.isGreaterThan(smaller)).isFalse();
    assertThat(bigger.isGreaterThan(bigger)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("comparableLongValues")
  void greaterThan_shouldReturnTrueWhenNumberIsStrictlyGreater(
      final long smaller, final long bigger) {
    assertThat(UInt64.valueOf(smaller).isGreaterThan(bigger)).isFalse();
    assertThat(UInt64.valueOf(bigger).isGreaterThan(smaller)).isTrue();

    // Must be strictly greater than
    assertThat(UInt64.valueOf(smaller).isGreaterThan(smaller)).isFalse();
    assertThat(UInt64.valueOf(bigger).isGreaterThan(bigger)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("comparableNumbers")
  void greaterThanOrEqualTo_shouldReturnTrueWhenNumberIsGreaterOrEqual(
      final UInt64 smaller, final UInt64 bigger) {
    assertThat(smaller.isGreaterThanOrEqualTo(bigger)).isFalse();
    assertThat(bigger.isGreaterThanOrEqualTo(smaller)).isTrue();

    // True when equal
    assertThat(smaller.isGreaterThanOrEqualTo(smaller)).isTrue();
    assertThat(bigger.isGreaterThanOrEqualTo(bigger)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("comparableLongValues")
  void greaterThanOrEqualTo_shouldReturnTrueWhenNumberIsGreaterOrEqual(
      final long smaller, final long bigger) {
    assertThat(UInt64.valueOf(smaller).isGreaterThanOrEqualTo(bigger)).isFalse();
    assertThat(UInt64.valueOf(bigger).isGreaterThanOrEqualTo(smaller)).isTrue();

    // True when equal
    assertThat(UInt64.valueOf(smaller).isGreaterThanOrEqualTo(smaller)).isTrue();
    assertThat(UInt64.valueOf(bigger).isGreaterThanOrEqualTo(bigger)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("comparableNumbers")
  void lessThan_shouldReturnTrueWhenNumberIsStrictlyLessThan(
      final UInt64 smaller, final UInt64 bigger) {
    assertThat(smaller.isLessThan(bigger)).isTrue();
    assertThat(bigger.isLessThan(smaller)).isFalse();

    // Must be strictly greater than
    assertThat(smaller.isLessThan(smaller)).isFalse();
    assertThat(bigger.isLessThan(bigger)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("comparableLongValues")
  void lessThan_shouldReturnTrueWhenNumberIsStrictlyLessThan(
      final long smaller, final long bigger) {
    assertThat(UInt64.valueOf(smaller).isLessThan(bigger)).isTrue();
    assertThat(UInt64.valueOf(bigger).isLessThan(smaller)).isFalse();

    // Must be strictly greater than
    assertThat(UInt64.valueOf(smaller).isLessThan(smaller)).isFalse();
    assertThat(UInt64.valueOf(bigger).isLessThan(bigger)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("comparableNumbers")
  void lessThanOrEqualTo_shouldReturnTrueWhenNumberIsLessThanOrEqual(
      final UInt64 smaller, final UInt64 bigger) {
    assertThat(smaller.isLessThanOrEqualTo(bigger)).isTrue();
    assertThat(bigger.isLessThanOrEqualTo(smaller)).isFalse();

    // Must be strictly greater than
    assertThat(smaller.isLessThanOrEqualTo(smaller)).isTrue();
    assertThat(bigger.isLessThanOrEqualTo(bigger)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("comparableLongValues")
  void lessThanOrEqualTo_shouldReturnTrueWhenNumberIsLessThanOrEqual(
      final long smaller, final long bigger) {
    assertThat(UInt64.valueOf(smaller).isLessThanOrEqualTo(bigger)).isTrue();
    assertThat(UInt64.valueOf(bigger).isLessThanOrEqualTo(smaller)).isFalse();

    // Must be strictly greater than
    assertThat(UInt64.valueOf(smaller).isLessThanOrEqualTo(smaller)).isTrue();
    assertThat(UInt64.valueOf(bigger).isLessThanOrEqualTo(bigger)).isTrue();
  }

  @Test
  void constants_shouldHaveExpectedValues() {
    assertThat(UInt64.ZERO).isEqualTo(UInt64.valueOf(0));
    assertThat(UInt64.THIRTY_TWO_ETH).isEqualTo(UInt64.valueOf(32_000_000_000L));
    assertThat(UInt64.ONE).isEqualTo(UInt64.valueOf(1));
    assertThat(UInt64.MAX_VALUE).isEqualTo(UInt64.fromLongBits(-1));
  }

  @ParameterizedTest
  @MethodSource("additionNumbers")
  void plus_shouldAddWhenNotOverflowing(
      final long value1, final long value2, final long sumOfValues) {
    final UInt64 uint1 = UInt64.fromLongBits(value1);
    final UInt64 uint2 = UInt64.fromLongBits(value2);
    final UInt64 uintSum = UInt64.fromLongBits(sumOfValues);
    assertThat(uint1.plus(uint2)).isEqualTo(uintSum);
  }

  @Test
  void plus_shouldThrowArithmeticExceptionWhenResultOverflows() {
    assertThatThrownBy(() -> UInt64.MAX_VALUE.plus(1)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.ONE.plus(UInt64.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.MAX_VALUE.minus(UInt64.ONE).plus(2))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.MAX_VALUE.plus(UInt64.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void plus_shouldThrowIllegalArgumentExceptionIfNegativeLongProvided() {
    assertThatThrownBy(() -> UInt64.ONE.plus(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void increment() {
    assertThat(UInt64.ONE.increment()).isEqualTo(UInt64.valueOf(2));
    assertThat(UInt64.ONE.increment().increment()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  void increment_shouldThrowArithmeticExceptionWhenResultOverflows() {
    assertThatThrownBy(UInt64.MAX_VALUE::increment).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void decrement() {
    assertThat(UInt64.ONE.decrement()).isEqualTo(UInt64.ZERO);
    assertThat(UInt64.valueOf(3).decrement().decrement()).isEqualTo(UInt64.ONE);
  }

  @Test
  void decrement_shouldThrowArithmeticExceptionWhenResultUnderflows() {
    assertThatThrownBy(UInt64.ZERO::decrement).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void safeDecrement() {
    assertThat(UInt64.ONE.safeDecrement()).isEqualTo(UInt64.ZERO);
    assertThat(UInt64.valueOf(3).safeDecrement().safeDecrement()).isEqualTo(UInt64.ONE);
  }

  @Test
  void safeDecrement_shouldNotUnderflows() {
    assertThat(UInt64.ZERO.safeDecrement()).isEqualTo(UInt64.ZERO);
  }

  @ParameterizedTest
  @MethodSource("additionNumbers")
  void subtract_shouldSubtractWhenNotOverflowing(
      final long value1, final long value2, final long sumOfValues) {
    final UInt64 uint1 = UInt64.fromLongBits(value1);
    final UInt64 uint2 = UInt64.fromLongBits(value2);
    final UInt64 uintSum = UInt64.fromLongBits(sumOfValues);
    assertThat(uintSum.minus(uint2)).isEqualTo(uint1);
    assertThat(uintSum.minus(uint1)).isEqualTo(uint2);
  }

  @Test
  void minus_shouldThrowArithmeticExceptionWhenResultUnderflows() {
    assertThatThrownBy(() -> UInt64.ZERO.minus(UInt64.ONE)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.ZERO.minus(UInt64.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.valueOf(14521245234L).minus(UInt64.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void minus_shouldThrowIllegalArgumentExceptionWhenArgumentIsNegative() {
    assertThatThrownBy(() -> UInt64.MAX_VALUE.minus(-4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void minusMinZero_shouldReturnZeroWhenResultUnderflows() {
    assertThat(UInt64.valueOf(10).minusMinZero(11)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void minusMinZero_shouldReturnZeroWhenParamsEqual() {
    assertThat(UInt64.valueOf(10).minusMinZero(10)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void minusMinZero_shouldPerformMinusWhenResultLargerThanZero() {
    assertThat(UInt64.valueOf(10).minusMinZero(8)).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void minusMinZero_shouldThrowIllegalArgumentExceptionWhenArgumentIsNegative() {
    assertThatThrownBy(() -> UInt64.valueOf(10).minusMinZero(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("modNumbers")
  void mod_shouldCalculateRemainder(
      final long value1, final long value2, final long expectedResult) {
    final UInt64 uint1 = UInt64.fromLongBits(value1);
    final UInt64 uint2 = UInt64.fromLongBits(value2);
    final UInt64 uintExpected = UInt64.fromLongBits(expectedResult);
    assertThat(uint1.mod(uint2)).isEqualTo(uintExpected);
  }

  @Test
  void mod_shouldThrowIllegalArgumentExceptionWhenArgumentIsNegative() {
    assertThatThrownBy(() -> UInt64.ONE.mod(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mod_shouldThrowArithmeticExceptionWhenDivisorIsZero() {
    assertThatThrownBy(() -> UInt64.ONE.mod(0)).isInstanceOf(ArithmeticException.class);
  }

  @ParameterizedTest
  @MethodSource("multiplicationNumbers")
  void times_shouldMultiplyWhenResultDoesNotOverflow(
      final long value1, final long value2, final long expectedResult) {
    final UInt64 uint1 = UInt64.fromLongBits(value1);
    final UInt64 uint2 = UInt64.fromLongBits(value2);
    final UInt64 uintExpected = UInt64.fromLongBits(expectedResult);
    assertThat(uint1.times(uint2)).isEqualTo(uintExpected);
    assertThat(uint2.times(uint1)).isEqualTo(uintExpected);
  }

  @ParameterizedTest
  @MethodSource("timesOverflowCases")
  void times_shouldThrowArithmeticExceptionWhenResultOverflows(
      final long value1, final long value2) {
    assertThatThrownBy(() -> UInt64.fromLongBits(value1).times(UInt64.fromLongBits(value2)))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> UInt64.fromLongBits(value2).times(UInt64.fromLongBits(value1)))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void times_shouldThrowIllegalArgumentExceptionWhenValueIsNegative() {
    assertThatThrownBy(() -> UInt64.ONE.times(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("multiplicationNumbers")
  void dividedBy_shouldPerformIntegerDivision(
      final long value1, final long value2, final long product) {
    if (value1 != 0) {
      assertThat(UInt64.fromLongBits(product).dividedBy(UInt64.fromLongBits(value1)))
          .isEqualTo(UInt64.fromLongBits(value2));
    }
    if (value2 != 0) {
      assertThat(UInt64.fromLongBits(product).dividedBy(UInt64.fromLongBits(value2)))
          .isEqualTo(UInt64.fromLongBits(value1));
    }
  }

  @Test
  void dividedBy_shouldPerformIntegerDivisionOnOddNumbers() {
    assertThat(UInt64.valueOf(5).dividedBy(2)).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void dividedBy_shouldThrowArithmeticExceptionWhenDividingByZero() {
    assertThatThrownBy(() -> UInt64.ONE.dividedBy(0)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void dividedBy_shouldThrowIllegalArgumentExceptionWhenValueIsNegative() {
    assertThatThrownBy(() -> UInt64.ONE.dividedBy(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void max_firstValueIsLarger() {
    final UInt64 a = UInt64.valueOf(2);
    final UInt64 b = UInt64.valueOf(1);

    final UInt64 result = a.max(b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void max_secondValueIsLarger() {
    final UInt64 a = UInt64.valueOf(1);
    final UInt64 b = UInt64.valueOf(2);

    final UInt64 result = a.max(b);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void max_valuesAreEqual() {
    final UInt64 a = UInt64.valueOf(10);
    final UInt64 b = UInt64.valueOf(10);

    final UInt64 result = a.max(b);
    assertThat(result).isEqualTo(a);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void maxLong_firstValueIsLarger() {
    final UInt64 a = UInt64.valueOf(2);
    final long b = 1;

    final UInt64 result = a.max(b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void maxLong_secondValueIsLarger() {
    final UInt64 a = UInt64.valueOf(1);
    final long b = 2;

    final UInt64 result = a.max(b);
    assertThat(result).isEqualTo(UInt64.valueOf(b));
  }

  @Test
  public void maxLong_valuesAreEqual() {
    final UInt64 a = UInt64.valueOf(10);
    final long b = 10;

    final UInt64 result = a.max(b);
    assertThat(result).isSameAs(a); // Should avoid creating a new instance
    assertThat(result).isEqualTo(UInt64.valueOf(b));
  }

  @Test
  void maxLong_shouldThrowWhenValueIsNegative() {
    assertThatThrownBy(() -> UInt64.valueOf(1).max(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void min_firstValueIsLarger() {
    final UInt64 a = UInt64.valueOf(2);
    final UInt64 b = UInt64.valueOf(1);

    final UInt64 result = a.min(b);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void min_secondValueIsLarger() {
    final UInt64 a = UInt64.valueOf(1);
    final UInt64 b = UInt64.valueOf(2);

    final UInt64 result = a.min(b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void min_valuesAreEqual() {
    final UInt64 a = UInt64.valueOf(10);
    final UInt64 b = UInt64.valueOf(10);

    final UInt64 result = a.min(b);
    assertThat(result).isEqualTo(a);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void minLong_firstValueIsLarger() {
    final UInt64 a = UInt64.valueOf(2);
    final long b = 1;

    final UInt64 result = a.min(b);
    assertThat(result).isEqualTo(UInt64.valueOf(b));
  }

  @Test
  public void minLong_secondValueIsLarger() {
    final UInt64 a = UInt64.valueOf(1);
    final long b = 2;

    final UInt64 result = a.min(b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void minLong_valuesAreEqual() {
    final UInt64 a = UInt64.valueOf(10);
    final long b = 10;

    final UInt64 result = a.min(b);
    assertThat(result).isSameAs(a); // Should avoid creating a new instance
    assertThat(result).isEqualTo(UInt64.valueOf(b));
  }

  @Test
  void minLong_shouldThrowWhenValueIsNegative() {
    assertThatThrownBy(() -> UInt64.valueOf(1).min(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("rangeNumbers")
  void range_shouldCreateStreamIncludingStartAndExcludingEnd(final int from, final int to) {
    assertThat(UInt64.range(UInt64.valueOf(from), UInt64.valueOf(to)))
        .containsExactlyElementsOf(
            IntStream.range(from, to).mapToObj(UInt64::valueOf).collect(toList()));
  }

  static List<Arguments> rangeNumbers() {
    return List.of(
        Arguments.of(0, 1),
        Arguments.of(1, 5),
        Arguments.of(0, 6),
        Arguments.of(6, 3),
        Arguments.of(1, 1));
  }

  static List<Arguments> timesOverflowCases() {
    return List.of(
        Arguments.of(-1L, 2L),
        Arguments.of(Long.divideUnsigned(-1, 2) + 1, 2),
        Arguments.of(Long.MAX_VALUE, 3),
        Arguments.of(Long.MIN_VALUE, 2),
        Arguments.of(SQRT_MAX_VALUE + 1, SQRT_MAX_VALUE + 1),
        Arguments.of(SPECIAL_CASE_MULTIPLICAND, MAX_SAFE_VALUE_WITH_SPECIAL_CASE_MULTIPLICAND + 1));
  }

  static List<Arguments> additionNumbers() {
    return List.of(
        Arguments.of(1, 1, 2),
        Arguments.of(-2, 1, -1),
        Arguments.of(10, 15, 25),
        Arguments.of(-1, 0, -1),
        Arguments.of(0, 0, 0),
        Arguments.of(Long.MAX_VALUE, 1, Long.MIN_VALUE),
        Arguments.of(0x4000000000000000L, 0x4000000000000000L, 0x8000000000000000L));
  }

  static List<Arguments> modNumbers() {
    return List.of(
        Arguments.of(1, 1, 0),
        Arguments.of(0, 1, 0),
        Arguments.of(8, 16, 8),
        Arguments.of(16, 8, 0),
        Arguments.of(-1, -1, 0),
        Arguments.of(50, 106, 50),
        Arguments.of(106, 50, 6),
        Arguments.of(-3252523523L, -1, -3252523523L));
  }

  static List<Arguments> multiplicationNumbers() {
    return List.of(
        Arguments.of(0, 0, 0),
        Arguments.of(1, 0, 0),
        Arguments.of(0, 1, 0),
        Arguments.of(1, 1, 1),
        Arguments.of(2, 4, 8),
        Arguments.of(3, 4, 12),
        Arguments.of(
            SPECIAL_CASE_MULTIPLICAND,
            MAX_SAFE_VALUE_WITH_SPECIAL_CASE_MULTIPLICAND,
            Long.parseUnsignedLong("18446744073709551552")),
        Arguments.of(Integer.MAX_VALUE, 2, ((long) Integer.MAX_VALUE) * 2),
        Arguments.of(Long.MIN_VALUE, 1, Long.MIN_VALUE),
        Arguments.of(Long.MAX_VALUE, 1, Long.MAX_VALUE),
        Arguments.of(Long.MAX_VALUE, 2, -2),
        Arguments.of(Long.divideUnsigned(-1, 3), 3, -1),
        Arguments.of(
            SQRT_MAX_VALUE, SQRT_MAX_VALUE, Long.parseUnsignedLong("18446744065119617025")));
  }

  static List<Arguments> comparableNumbers() {
    // (Smaller Number, Bigger Number)
    return List.of(
        Arguments.of(UInt64.ZERO, UInt64.ONE),
        Arguments.of(UInt64.ZERO, UInt64.valueOf(Long.MAX_VALUE)),
        Arguments.of(UInt64.ZERO, UInt64.MAX_VALUE),
        Arguments.of(UInt64.fromLongBits(-2), UInt64.MAX_VALUE),
        Arguments.of(UInt64.fromLongBits(-445), UInt64.fromLongBits(-444)),
        Arguments.of(UInt64.valueOf(22244), UInt64.valueOf(22245)),
        Arguments.of(UInt64.valueOf(Long.MAX_VALUE), UInt64.fromLongBits(Long.MIN_VALUE)));
  }

  static List<Arguments> comparableLongValues() {
    // (Smaller Number, Bigger Number)
    return List.of(
        Arguments.of(0, 1),
        Arguments.of(10, 11),
        Arguments.of(100, 200),
        Arguments.of(0, Long.MAX_VALUE),
        Arguments.of(Long.MAX_VALUE - 1, Long.MAX_VALUE));
  }
}

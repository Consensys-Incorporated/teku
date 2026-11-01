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

package tech.pegasys.teku.spec.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION;

import java.math.BigInteger;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class BuilderBoostFactorEvaluatorTest {

  @ParameterizedTest
  @MethodSource("comparisonScenarios")
  void evaluatesComparison(
      final UInt256 localValue,
      final UInt256 builderValue,
      final UInt64 builderBoostFactor,
      final boolean expectedLocalWin) {
    assertThat(
            BuilderBoostFactorEvaluator.isLocalValueWinning(
                localValue, builderValue, builderBoostFactor))
        .isEqualTo(expectedLocalWin);
  }

  private static Stream<Arguments> comparisonScenarios() {
    return Stream.of(
        Arguments.of(UInt256.valueOf(90), UInt256.valueOf(100), UInt64.valueOf(90), true),
        Arguments.of(UInt256.valueOf(89), UInt256.valueOf(100), UInt64.valueOf(90), false),
        Arguments.of(UInt256.valueOf(89), UInt256.valueOf(100), UInt64.valueOf(80), true),
        Arguments.of(UInt256.ZERO, UInt256.MAX_VALUE, BUILDER_BOOST_FACTOR_PREFER_EXECUTION, true),
        Arguments.of(UInt256.MAX_VALUE, UInt256.ZERO, BUILDER_BOOST_FACTOR_PREFER_BUILDER, false),
        Arguments.of(
            UInt256.valueOf(100), UInt256.valueOf(100), BUILDER_BOOST_FACTOR_MAX_PROFIT, true));
  }

  @Test
  void computesBoostedValue() {
    final BigInteger value = BigInteger.valueOf(1000);
    assertThat(BuilderBoostFactorEvaluator.computeBoostedValue(value, UInt64.valueOf(90)))
        .isEqualTo(BigInteger.valueOf(90_000));
    assertThat(
            BuilderBoostFactorEvaluator.computeBoostedValue(
                value, BUILDER_BOOST_FACTOR_PREFER_EXECUTION))
        .isZero();
    assertThat(
            BuilderBoostFactorEvaluator.computeBoostedValue(
                value, BUILDER_BOOST_FACTOR_PREFER_BUILDER))
        .isEqualTo(value.multiply(BUILDER_BOOST_FACTOR_PREFER_BUILDER.bigIntegerValue()));
  }

  @Test
  void comparesWithoutOverflowingUInt256() {
    final UInt256 localValue = UInt256.MAX_VALUE;
    final UInt256 builderValue =
        UInt256.valueOf(BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE));

    assertThat(
            BuilderBoostFactorEvaluator.isLocalValueWinning(
                localValue, builderValue, UInt64.valueOf(200)))
        .isTrue();
  }

  @Test
  void evaluatesLargestBoostFactor() {
    final UInt64 builderBoostFactor = UInt64.MAX_VALUE.minus(UInt64.ONE);
    final UInt256 localValueAtThreshold = UInt256.valueOf(builderBoostFactor.bigIntegerValue());
    final UInt256 localValueBelowThreshold =
        UInt256.valueOf(builderBoostFactor.bigIntegerValue().subtract(BigInteger.ONE));
    final UInt256 builderValue = UInt256.valueOf(100);

    assertThat(
            BuilderBoostFactorEvaluator.isLocalValueWinning(
                localValueAtThreshold, builderValue, builderBoostFactor))
        .isTrue();
    assertThat(
            BuilderBoostFactorEvaluator.isLocalValueWinning(
                localValueBelowThreshold, builderValue, builderBoostFactor))
        .isFalse();
  }
}

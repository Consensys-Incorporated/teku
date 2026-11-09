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

package tech.pegasys.teku.spec.logic.common.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class WeakSubjectivityCalculatorTest {

  private final Spec spec = TestSpecFactory.createMainnetPhase0();

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivityPeriod(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedResult) {
    final UInt64 totalActiveValidatorBalance = avgActiveValidatorBalance.times(validatorCount);
    final WeakSubjectivityCalculator calculator =
        spec.getGenesisSpec().weakSubjectivityCalculator();
    final UInt64 result =
        calculator.computeWeakSubjectivityPeriod(
            validatorCount, totalActiveValidatorBalance, safetyDecay);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  // Parameters from the table here:
  // https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/weak-subjectivity.md#compute_weak_subjectivity_period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 32768, 504),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 65536, 752),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 131072, 1248),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 262144, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 524288, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 1048576, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 32768, 665),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 65536, 1075),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 131072, 1894),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 262144, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 524288, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 1048576, 3532));
  }
}

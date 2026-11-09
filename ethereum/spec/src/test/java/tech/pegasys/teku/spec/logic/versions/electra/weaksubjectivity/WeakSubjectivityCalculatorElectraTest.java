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

package tech.pegasys.teku.spec.logic.versions.electra.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WeakSubjectivityCalculatorElectraTest {

  private final Spec spec = TestSpecFactory.createMainnetElectra();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @ParameterizedTest(name = "totalActiveBalanceInEth: {0}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivityPeriod(
      final UInt64 totalActiveBalanceInEth, final int expectedResult) {
    final int balanceInEth = 2048;
    final UInt64 numberOfConsolidatingValidators = totalActiveBalanceInEth.dividedBy(balanceInEth);
    final BeaconStateTestBuilder stateBuilder =
        new BeaconStateTestBuilder(dataStructureUtil).slot(42);
    for (int i = 0; i < numberOfConsolidatingValidators.intValue(); i++) {
      stateBuilder.activeConsolidatingValidator(ETH_TO_GWEI.times(balanceInEth));
    }
    final BeaconState state = stateBuilder.build();
    final SpecVersion specVersion = spec.atSlot(state.getSlot());

    final WeakSubjectivityCalculator calculator = specVersion.weakSubjectivityCalculator();

    assertThat(calculator.computeWeakSubjectivityPeriod(state))
        .isEqualTo(UInt64.valueOf(expectedResult));
  }

  // Parameters from the table here:
  // https://github.com/ethereum/consensus-specs/blob/master/specs/electra/weak-subjectivity.md#modified-compute_weak_subjectivity_period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(1_048_576), 665),
        Arguments.of(UInt64.valueOf(2_097_152), 1075),
        Arguments.of(UInt64.valueOf(4_194_304), 1894),
        Arguments.of(UInt64.valueOf(8_388_608), 3532),
        Arguments.of(UInt64.valueOf(16_777_216), 3532),
        Arguments.of(UInt64.valueOf(33_554_432), 3532));
  }
}

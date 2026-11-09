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

package tech.pegasys.teku.spec.logic.versions.gloas.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WeakSubjectivityCalculatorGloasTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  // test based on https://eips.ethereum.org/EIPS/eip-8061#weak-subjectivity-period
  @Test
  public void computeWeakSubjectivityPeriod() {
    // amounts to total stake of ~ 36M ETH
    final int numberOfConsolidatingValidators = 17578;
    final BeaconStateTestBuilder stateBuilder =
        new BeaconStateTestBuilder(dataStructureUtil).slot(42);
    for (int i = 0; i < numberOfConsolidatingValidators; i++) {
      stateBuilder.activeConsolidatingValidator(ETH_TO_GWEI.times(2048));
    }
    final BeaconState state = stateBuilder.build();
    final SpecVersion specVersion = spec.atSlot(state.getSlot());

    final WeakSubjectivityCalculator calculator = specVersion.weakSubjectivityCalculator();

    // ~ 7.0 days
    final int expectedWeakSubjectivityPeriod = 1573;
    assertThat(calculator.computeWeakSubjectivityPeriod(state))
        .isEqualTo(UInt64.valueOf(expectedWeakSubjectivityPeriod));
  }
}

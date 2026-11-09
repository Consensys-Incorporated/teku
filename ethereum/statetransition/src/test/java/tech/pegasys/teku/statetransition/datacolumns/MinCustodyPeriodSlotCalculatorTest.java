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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

class MinCustodyPeriodSlotCalculatorTest {
  private final UInt64 fuluActivationEpoch = UInt64.valueOf(100);

  private final Spec spec = TestSpecFactory.createMinimalWithFuluForkEpoch(fuluActivationEpoch);
  private final int slotsPerEpoch = spec.getGenesisSpec().getSlotsPerEpoch();
  private final int minEpochsForDataColumnSidecarsRequests =
      spec.forMilestone(SpecMilestone.FULU)
          .getConfig()
          .toVersionFulu()
          .orElseThrow()
          .getMinEpochsForDataColumnSidecarsRequests();

  @Test
  void shouldClampResultToFuluActivationWhenCalculatedPeriodIsEarlier() {
    final UInt64 currentEpoch =
        fuluActivationEpoch.plus(minEpochsForDataColumnSidecarsRequests - 1);
    final UInt64 currentSlot = currentEpoch.times(slotsPerEpoch);

    final MinCustodyPeriodSlotCalculator calculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final Optional<UInt64> resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    final UInt64 expectedEpoch = fuluActivationEpoch; // 100
    assertThat(resultSlot).contains(expectedEpoch.times(slotsPerEpoch));
  }

  @Test
  void shouldReturnCalculatedPeriodWhenWellAfterFuluActivation() {
    final UInt64 currentEpoch =
        fuluActivationEpoch.plus(minEpochsForDataColumnSidecarsRequests + 10);
    final UInt64 currentSlot = currentEpoch.times(slotsPerEpoch);

    final MinCustodyPeriodSlotCalculator calculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final Optional<UInt64> resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    final UInt64 expectedEpoch = UInt64.valueOf(110);
    assertThat(resultSlot).contains(expectedEpoch.times(slotsPerEpoch));
  }

  @Test
  void shouldHandleExactBoundaryAtFuluActivation() {
    final UInt64 currentEpoch = fuluActivationEpoch;
    final UInt64 currentSlot = currentEpoch.times(slotsPerEpoch);

    final MinCustodyPeriodSlotCalculator calculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final Optional<UInt64> resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    assertThat(resultSlot).contains(fuluActivationEpoch.times(slotsPerEpoch));
  }

  @Test
  void shouldReturnEmptyWhenFuluNotInForkSchedule() {
    final Spec nonFuluSpec = TestSpecFactory.createMinimalDeneb();
    final MinCustodyPeriodSlotCalculator calculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(nonFuluSpec);

    assertThat(calculator.getMinCustodyPeriodSlot(UInt64.ZERO)).isEmpty();
    assertThat(calculator.getMinCustodyPeriodSlot(UInt64.valueOf(1000))).isEmpty();
  }

  @Test
  void shouldHandlePreFuluSlot() {
    final UInt64 currentEpoch = fuluActivationEpoch.minus(10);
    final UInt64 currentSlot = currentEpoch.times(slotsPerEpoch);

    final MinCustodyPeriodSlotCalculator calculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final Optional<UInt64> resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    assertThat(resultSlot).isEmpty();
  }
}

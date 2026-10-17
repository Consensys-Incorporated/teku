/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * EIP-8198: the inactivity penalty is scaled by the square of the previous epoch's slot duration.
 * Minimal preset with 6000 ms slots and Heze at epoch 1 switching to 5000 ms slots.
 */
class RewardsAndPenaltiesCalculatorHezeTest {

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder -> builder.hezeForkEpoch(UInt64.ONE).slotDurationMsHeze(5000));
  private final SpecVersion hezeSpec = spec.forMilestone(SpecMilestone.HEZE);
  private final SpecConfigBellatrix config = SpecConfigBellatrix.required(hezeSpec.getConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void inactivityPenaltyDenominator_shouldNotScaleWhenPreviousEpochUsedTheGenesisDuration() {
    // previous epoch 0 used 6000 ms slots
    assertThat(createCalculator(stateAtEpoch(UInt64.ONE)).getInactivityPenaltyDenominator())
        .isEqualTo(unscaledDenominator());
  }

  @Test
  void inactivityPenaltyDenominator_shouldScaleBySquareOfPreviousEpochSlotDuration() {
    // previous epoch 1 used 5000 ms slots
    assertThat(createCalculator(stateAtEpoch(UInt64.valueOf(2))).getInactivityPenaltyDenominator())
        .isEqualTo(unscaledDenominator().times(6000L * 6000L).dividedBy(5000L * 5000L));
  }

  @Test
  void epochProcessor_shouldUseHezeCalculator() {
    final BeaconState state = stateAtEpoch(UInt64.valueOf(2));
    final ValidatorStatuses validatorStatuses =
        hezeSpec.getValidatorStatusFactory().createValidatorStatuses(state);

    hezeSpec
        .getEpochProcessor()
        .getRewardAndPenaltyDeltas(
            state,
            validatorStatuses,
            calculator -> {
              assertThat(calculator).isInstanceOf(RewardsAndPenaltiesCalculatorHeze.class);
              return calculator.getDeltas();
            });
  }

  private UInt64 unscaledDenominator() {
    return config.getInactivityScoreBias().times(config.getInactivityPenaltyQuotientBellatrix());
  }

  private RewardsAndPenaltiesCalculatorHeze createCalculator(final BeaconState state) {
    return new RewardsAndPenaltiesCalculatorHeze(
        config,
        BeaconStateBellatrix.required(state),
        hezeSpec.getValidatorStatusFactory().createValidatorStatuses(state),
        (MiscHelpersAltair) hezeSpec.miscHelpers(),
        BeaconStateAccessorsHeze.required(hezeSpec.beaconStateAccessors()));
  }

  private BeaconState stateAtEpoch(final UInt64 epoch) {
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch).plus(1);
    return dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(slot));
  }
}

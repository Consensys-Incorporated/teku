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

package tech.pegasys.teku.spec.logic.versions.heze.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * EIP-8198: per-epoch rewards and churn are scaled by the slot duration. Minimal preset with 6000
 * ms slots and Heze at epoch 1 switching to 5000 ms slots, so the scaling ratio is 5/6.
 */
class BeaconStateAccessorsHezeTest {

  private static final UInt64 HEZE_EPOCH = UInt64.ONE;

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder -> builder.hezeForkEpoch(HEZE_EPOCH).slotDurationMsHeze(5000));
  private final SpecConfigHeze config =
      SpecConfigHeze.required(spec.forMilestone(SpecMilestone.HEZE).getConfig());
  private final BeaconStateAccessorsHeze accessors =
      BeaconStateAccessorsHeze.required(
          spec.forMilestone(SpecMilestone.HEZE).beaconStateAccessors());
  private final BeaconStateAccessorsGloas gloasAccessors =
      (BeaconStateAccessorsGloas) spec.forMilestone(SpecMilestone.GLOAS).beaconStateAccessors();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void getBaseRewardPerIncrement_shouldScaleToTheSlotDurationOfTheEpoch() {
    final BeaconState state = stateAtEpoch(UInt64.valueOf(2));
    final UInt64 sqrtTotalActiveBalance = integerSquareRoot(accessors.getTotalActiveBalance(state));
    final UInt64 unscaled =
        config.getEffectiveBalanceIncrement().times(config.getBaseRewardFactor());

    assertThat(accessors.getBaseRewardPerIncrement(state, UInt64.ZERO))
        .isEqualTo(unscaled.dividedBy(sqrtTotalActiveBalance));
    assertThat(accessors.getBaseRewardPerIncrement(state, HEZE_EPOCH))
        .isEqualTo(unscaled.times(5000).dividedBy(6000).dividedBy(sqrtTotalActiveBalance));
    // defaults to the current epoch
    assertThat(accessors.getBaseRewardPerIncrement(state))
        .isEqualTo(accessors.getBaseRewardPerIncrement(state, UInt64.valueOf(2)));
  }

  @Test
  void getBaseReward_shouldScaleToTheSlotDurationOfTheEpoch() {
    final BeaconState state = stateAtEpoch(UInt64.valueOf(2));
    final UInt64 increments =
        state
            .getValidators()
            .get(0)
            .getEffectiveBalance()
            .dividedBy(config.getEffectiveBalanceIncrement());

    assertThat(accessors.getBaseReward(state, 0, UInt64.ZERO))
        .isEqualTo(increments.times(accessors.getBaseRewardPerIncrement(state, UInt64.ZERO)));
    assertThat(accessors.getBaseReward(state, 0, HEZE_EPOCH))
        .isEqualTo(increments.times(accessors.getBaseRewardPerIncrement(state, HEZE_EPOCH)));
  }

  @Test
  void churnLimits_shouldScaleToTheCurrentSlotDuration() {
    final BeaconStateElectra state = BeaconStateElectra.required(stateAtEpoch(UInt64.valueOf(2)));
    final UInt64 totalActiveBalance = accessors.getTotalActiveBalance(state);
    final UInt64 balanceChurn =
        config
            .getMinPerEpochChurnLimitElectra()
            .max(totalActiveBalance.dividedBy(config.getChurnLimitQuotientGloas()));

    assertThat(accessors.getActivationChurnLimit(state))
        .isEqualTo(
            scaledAndRounded(balanceChurn.min(config.getMaxPerEpochActivationChurnLimitGloas())));
    assertThat(accessors.getExitChurnLimit(state)).isEqualTo(scaledAndRounded(balanceChurn));
    assertThat(accessors.getConsolidationChurnLimit(state))
        .isEqualTo(
            scaledAndRounded(
                totalActiveBalance.dividedBy(config.getConsolidationChurnLimitQuotient())));
  }

  @Test
  void churnLimits_shouldMatchGloasBeforeTheSlotDurationChange() {
    final BeaconStateElectra state = BeaconStateElectra.required(stateAtEpoch(UInt64.ZERO));

    assertThat(accessors.getActivationChurnLimit(state))
        .isEqualTo(gloasAccessors.getActivationChurnLimit(state));
    assertThat(accessors.getExitChurnLimit(state))
        .isEqualTo(gloasAccessors.getExitChurnLimit(state));
    assertThat(accessors.getConsolidationChurnLimit(state))
        .isEqualTo(gloasAccessors.getConsolidationChurnLimit(state));
  }

  @Test
  void hezeAtGenesis_shouldStillScale() {
    // Scaling is against SLOT_DURATION_MS, not the slot duration at genesis, so a network that
    // activates Heze at genesis with shorter slots is still scaled
    final Spec hezeAtGenesisSpec =
        TestSpecFactory.createMinimalHeze(builder -> builder.slotDurationMsHeze(5000));
    final BeaconStateAccessorsHeze hezeAtGenesisAccessors =
        BeaconStateAccessorsHeze.required(
            hezeAtGenesisSpec.getGenesisSpec().beaconStateAccessors());

    assertThat(hezeAtGenesisAccessors.hasScaledSlotDuration(UInt64.ZERO)).isTrue();
    assertThat(hezeAtGenesisAccessors.scaleBySlotDuration(UInt64.valueOf(600), UInt64.ZERO))
        .isEqualTo(UInt64.valueOf(500));
  }

  private UInt64 scaledAndRounded(final UInt64 churn) {
    final UInt64 scaled = churn.times(5000).dividedBy(6000);
    return scaled.minus(scaled.mod(config.getEffectiveBalanceIncrement()));
  }

  private BeaconState stateAtEpoch(final UInt64 epoch) {
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch).plus(1);
    return dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(slot));
  }
}

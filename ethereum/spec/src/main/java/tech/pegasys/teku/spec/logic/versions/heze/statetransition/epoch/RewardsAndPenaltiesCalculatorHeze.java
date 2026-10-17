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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch.RewardsAndPenaltiesCalculatorBellatrix;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;

public class RewardsAndPenaltiesCalculatorHeze extends RewardsAndPenaltiesCalculatorBellatrix {

  private final SpecConfigBellatrix specConfigBellatrix;
  private final BeaconStateBellatrix state;
  private final BeaconStateAccessorsHeze beaconStateAccessorsHeze;

  public RewardsAndPenaltiesCalculatorHeze(
      final SpecConfigBellatrix specConfig,
      final BeaconStateBellatrix state,
      final ValidatorStatuses validatorStatuses,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsHeze beaconStateAccessors) {
    super(specConfig, state, validatorStatuses, miscHelpers, beaconStateAccessors);
    this.specConfigBellatrix = specConfig;
    this.state = state;
    this.beaconStateAccessorsHeze = beaconStateAccessors;
  }

  /**
   * get_inactivity_penalty_deltas
   *
   * <p>EIP-8198: the penalty scales with the square of the previous epoch's slot duration, so the
   * cumulative penalty over a fixed wall-clock leak is unchanged.
   */
  @Override
  protected UInt64 getInactivityPenaltyDenominator() {
    final UInt64 previousEpoch = beaconStateAccessorsHeze.getPreviousEpoch(state);
    final long baseSlotDurationMillis = beaconStateAccessorsHeze.getBaseSlotDurationMillis();
    final long slotDurationMillis = miscHelpers.getSlotDurationMillis(previousEpoch);
    return specConfigBellatrix
        .getInactivityScoreBias()
        .times(specConfigBellatrix.getInactivityPenaltyQuotientBellatrix())
        .times(baseSlotDurationMillis * baseSlotDurationMillis)
        .dividedBy(slotDurationMillis * slotDurationMillis);
  }
}

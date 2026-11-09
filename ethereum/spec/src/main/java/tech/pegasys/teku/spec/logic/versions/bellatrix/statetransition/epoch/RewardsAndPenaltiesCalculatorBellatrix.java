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

package tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.RewardsAndPenaltiesCalculatorAltair;

public class RewardsAndPenaltiesCalculatorBellatrix extends RewardsAndPenaltiesCalculatorAltair {

  private final SpecConfigBellatrix specConfigBellatrix;

  public RewardsAndPenaltiesCalculatorBellatrix(
      final SpecConfigBellatrix specConfig,
      final BeaconStateBellatrix state,
      final ValidatorStatuses validatorStatuses,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    super(specConfig, state, validatorStatuses, miscHelpers, beaconStateAccessors);
    specConfigBellatrix = specConfig;
  }

  @Override
  protected UInt64 getInactivityPenaltyQuotient() {
    return specConfigBellatrix.getInactivityPenaltyQuotientBellatrix();
  }
}

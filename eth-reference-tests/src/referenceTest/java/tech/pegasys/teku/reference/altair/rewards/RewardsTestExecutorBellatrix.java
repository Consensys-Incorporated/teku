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

package tech.pegasys.teku.reference.altair.rewards;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.RewardsAndPenaltiesCalculatorAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch.RewardsAndPenaltiesCalculatorBellatrix;

public class RewardsTestExecutorBellatrix extends RewardsTestExecutorAltair {

  public static final ImmutableMap<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of(
          "rewards/basic",
          new RewardsTestExecutorBellatrix(),
          "rewards/leak",
          new RewardsTestExecutorBellatrix(),
          "rewards/random",
          new RewardsTestExecutorBellatrix(),
          "rewards/inactivity_scores",
          new RewardsTestExecutorBellatrix());

  @Override
  protected RewardsAndPenaltiesCalculatorAltair createRewardsAndPenaltiesCalculator(
      final BeaconState preState,
      final ValidatorStatuses validatorStatuses,
      final SpecVersion spec) {
    return new RewardsAndPenaltiesCalculatorBellatrix(
        SpecConfigBellatrix.required(spec.getConfig()),
        BeaconStateBellatrix.required(preState),
        validatorStatuses,
        (MiscHelpersAltair) spec.miscHelpers(),
        (BeaconStateAccessorsAltair) spec.beaconStateAccessors());
  }
}

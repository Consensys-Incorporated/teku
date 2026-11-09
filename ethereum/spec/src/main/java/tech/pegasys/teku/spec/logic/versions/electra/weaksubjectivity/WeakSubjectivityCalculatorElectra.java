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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;

/**
 * Computes weak-subjectivity period values according to the <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/electra/weak-subjectivity.md">Electra
 * -- Weak Subjectivity Guide</a>.
 */
public class WeakSubjectivityCalculatorElectra extends WeakSubjectivityCalculator {

  protected final BeaconStateAccessorsElectra beaconStateAccessorsElectra;

  public WeakSubjectivityCalculatorElectra(
      final SpecConfigElectra specConfig,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final MiscHelpersElectra miscHelpers) {
    super(specConfig, beaconStateAccessors, miscHelpers);
    this.beaconStateAccessorsElectra = beaconStateAccessors;
  }

  @Override
  public UInt64 computeWeakSubjectivityPeriod(final BeaconState state) {
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    return computeWeakSubjectivityPeriodFromTotalActiveBalance(
        beaconStateAccessorsElectra.getTotalActiveBalance(state),
        beaconStateAccessorsElectra.getBalanceChurnLimit(stateElectra));
  }

  protected UInt64 computeWeakSubjectivityPeriodFromTotalActiveBalance(
      final UInt64 totalActiveBalance, final UInt64 delta) {
    final UInt64 epochsForValidatorSetChurn =
        SAFETY_DECAY.times(totalActiveBalance).dividedBy(delta.times(2).times(100));
    return epochsForValidatorSetChurn.plus(specConfig.getMinValidatorWithdrawabilityDelay());
  }
}

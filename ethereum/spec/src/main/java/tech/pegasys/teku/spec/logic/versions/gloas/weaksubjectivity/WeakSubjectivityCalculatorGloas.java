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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.electra.weaksubjectivity.WeakSubjectivityCalculatorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

/**
 * Computes weak-subjectivity period values according to the <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/weak-subjectivity.md">Gloas
 * -- Weak Subjectivity Guide</a>.
 */
public class WeakSubjectivityCalculatorGloas extends WeakSubjectivityCalculatorElectra {

  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public WeakSubjectivityCalculatorGloas(
      final SpecConfigGloas specConfig,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, beaconStateAccessors, miscHelpers);
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  @Override
  public UInt64 computeWeakSubjectivityPeriod(final BeaconState state) {
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    final UInt64 delta =
        beaconStateAccessorsGloas
            .getExitChurnLimit(stateElectra)
            .times(2)
            .dividedBy(3)
            .plus(beaconStateAccessorsGloas.getActivationChurnLimit(stateElectra).dividedBy(3))
            .plus(beaconStateAccessorsGloas.getConsolidationChurnLimit(stateElectra));
    return computeWeakSubjectivityPeriodFromTotalActiveBalance(
        beaconStateAccessorsGloas.getTotalActiveBalance(state), delta);
  }
}

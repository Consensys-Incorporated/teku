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

package tech.pegasys.teku.spec.logic.versions.phase0.helpers;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class BeaconStateAccessorsPhase0 extends BeaconStateAccessors {
  public BeaconStateAccessorsPhase0(
      final SpecConfig config, final Predicates predicates, final MiscHelpers miscHelpers) {
    super(config, predicates, miscHelpers);
  }

  // Custom accessors
  @Override
  public int getPreviousEpochAttestationCapacity(final BeaconState genericState) {
    final BeaconStatePhase0 state = BeaconStatePhase0.required(genericState);
    final int absoluteMax =
        Math.toIntExact(
            state.getBeaconStateSchema().getPreviousEpochAttestationsSchema().getMaxLength());
    return absoluteMax - state.getPreviousEpochAttestations().size();
  }
}

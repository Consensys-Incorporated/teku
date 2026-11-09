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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public interface MutableBeaconStatePhase0 extends MutableBeaconState, BeaconStatePhase0 {

  static MutableBeaconStatePhase0 required(final MutableBeaconState state) {
    return state
        .toMutableVersionPhase0()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a phase0 state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  @Override
  default SszMutableList<PendingAttestation> getPreviousEpochAttestations() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS);
    return getAnyByRef(fieldIndex);
  }

  @Override
  default SszMutableList<PendingAttestation> getCurrentEpochAttestations() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS);
    return getAnyByRef(fieldIndex);
  }

  @Override
  BeaconStatePhase0 commitChanges();

  @Override
  default Optional<MutableBeaconStatePhase0> toMutableVersionPhase0() {
    return Optional.of(this);
  }
}

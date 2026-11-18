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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PQ_REGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.VALIDATOR_PQ_REGISTRATIONS;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;

public interface MutableBeaconStateEip8483 extends MutableBeaconStateGloas, BeaconStateEip8483 {
  static MutableBeaconStateEip8483 required(final MutableBeaconState state) {
    return state
        .toMutableVersionEip8483()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a EIP-8483 state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateEip8483 commitChanges();

  @Override
  default Optional<MutableBeaconStateEip8483> toMutableVersionEip8483() {
    return Optional.of(this);
  }

  @Override
  default SszMutableList<ValidatorPqRegistration> getValidatorPqRegistrations() {
    final int fieldIndex = getSchema().getFieldIndex(VALIDATOR_PQ_REGISTRATIONS);
    return getAnyByRef(fieldIndex);
  }

  default void setValidatorPqRegistrations(
      final SszList<ValidatorPqRegistration> validatorPqRegistrations) {
    final int fieldIndex = getSchema().getFieldIndex(VALIDATOR_PQ_REGISTRATIONS);
    set(fieldIndex, validatorPqRegistrations);
  }

  @Override
  default SszMutableList<BuilderPqRegistration> getBuilderPqRegistrations() {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PQ_REGISTRATIONS);
    return getAnyByRef(fieldIndex);
  }

  default void setBuilderPqRegistrations(
      final SszList<BuilderPqRegistration> builderPqRegistrations) {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PQ_REGISTRATIONS);
    set(fieldIndex, builderPqRegistrations);
  }
}

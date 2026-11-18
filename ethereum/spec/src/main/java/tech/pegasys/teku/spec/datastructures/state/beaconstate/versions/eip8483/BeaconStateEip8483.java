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

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;

public interface BeaconStateEip8483 extends BeaconStateGloas {
  static BeaconStateEip8483 required(final BeaconState state) {
    return state
        .toVersionEip8483()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a EIP-8483 state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomEip8483Fields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateEip8483 state) {
    BeaconStateGloas.describeCustomGloasFields(stringBuilder, state);
    stringBuilder.add("validator_pq_registrations", state.getValidatorPqRegistrations());
    stringBuilder.add("builder_pq_registrations", state.getBuilderPqRegistrations());
  }

  @Override
  MutableBeaconStateEip8483 createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateEip8483 updatedEip8483(
          final Mutator<MutableBeaconStateEip8483, E1, E2, E3> mutator) throws E1, E2, E3 {
    final MutableBeaconStateEip8483 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateEip8483> toVersionEip8483() {
    return Optional.of(this);
  }

  default SszList<ValidatorPqRegistration> getValidatorPqRegistrations() {
    final int index = getSchema().getFieldIndex(VALIDATOR_PQ_REGISTRATIONS);
    return getAny(index);
  }

  default SszList<BuilderPqRegistration> getBuilderPqRegistrations() {
    final int index = getSchema().getFieldIndex(BUILDER_PQ_REGISTRATIONS);
    return getAny(index);
  }
}

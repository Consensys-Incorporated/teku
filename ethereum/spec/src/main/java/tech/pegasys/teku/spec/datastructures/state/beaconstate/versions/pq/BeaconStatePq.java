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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.AGGREGATION_COMMITTEE_WINDOW;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PREREGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.VALIDATOR_PREREGISTRATIONS;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;

/**
 * The PQ state. It has no sync committees, so the Altair sync-committee getters throw; its
 * validators are {@link tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq} and
 * its builders {@link tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq}.
 */
public interface BeaconStatePq extends BeaconStateGloas {
  static BeaconStatePq required(final BeaconState state) {
    return state
        .toVersionPq()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a PQ state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomPqFields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStatePq state) {
    BeaconStateGloas.describeCustomGloasFields(stringBuilder, state);
    stringBuilder.add("validator_preregistrations", state.getValidatorPreregistrations());
    stringBuilder.add("builder_preregistrations", state.getBuilderPreregistrations());
    stringBuilder.add("aggregation_committee_window", state.getAggregationCommitteeWindow());
  }

  @Override
  MutableBeaconStatePq createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStatePq updatedPq(final Mutator<MutableBeaconStatePq, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    final MutableBeaconStatePq writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStatePq> toVersionPq() {
    return Optional.of(this);
  }

  /** PQ has no sync committees. */
  @Override
  default SyncCommittee getCurrentSyncCommittee() {
    throw new UnsupportedOperationException("The PQ state has no sync committees");
  }

  /** PQ has no sync committees. */
  @Override
  default SyncCommittee getNextSyncCommittee() {
    throw new UnsupportedOperationException("The PQ state has no sync committees");
  }

  default SszList<ValidatorPreregistration> getValidatorPreregistrations() {
    final int index = getSchema().getFieldIndex(VALIDATOR_PREREGISTRATIONS);
    return getAny(index);
  }

  default SszList<BuilderPreregistration> getBuilderPreregistrations() {
    final int index = getSchema().getFieldIndex(BUILDER_PREREGISTRATIONS);
    return getAny(index);
  }

  /** Per slot of the window, the aggregators of each beacon committee in committee order. */
  default SszVector<SszList<SszList<SszUInt64>>> getAggregationCommitteeWindow() {
    final int index = getSchema().getFieldIndex(AGGREGATION_COMMITTEE_WINDOW);
    return getAny(index);
  }
}

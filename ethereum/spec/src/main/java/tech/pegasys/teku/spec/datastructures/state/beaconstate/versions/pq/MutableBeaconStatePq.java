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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;

public interface MutableBeaconStatePq extends MutableBeaconStateGloas, BeaconStatePq {
  static MutableBeaconStatePq required(final MutableBeaconState state) {
    return state
        .toMutableVersionPq()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a PQ state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStatePq commitChanges();

  @Override
  default Optional<MutableBeaconStatePq> toMutableVersionPq() {
    return Optional.of(this);
  }

  /** PQ has no sync committees. */
  @Override
  default void setCurrentSyncCommittee(final SyncCommittee currentSyncCommittee) {
    throw new UnsupportedOperationException("The PQ state has no sync committees");
  }

  /** PQ has no sync committees. */
  @Override
  default void setNextSyncCommittee(final SyncCommittee nextSyncCommittee) {
    throw new UnsupportedOperationException("The PQ state has no sync committees");
  }

  @Override
  default SszMutableList<ValidatorPreregistration> getValidatorPreregistrations() {
    final int fieldIndex = getSchema().getFieldIndex(VALIDATOR_PREREGISTRATIONS);
    return getAnyByRef(fieldIndex);
  }

  default void setValidatorPreregistrations(
      final SszList<ValidatorPreregistration> validatorPreregistrations) {
    final int fieldIndex = getSchema().getFieldIndex(VALIDATOR_PREREGISTRATIONS);
    set(fieldIndex, validatorPreregistrations);
  }

  @Override
  default SszMutableList<BuilderPreregistration> getBuilderPreregistrations() {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PREREGISTRATIONS);
    return getAnyByRef(fieldIndex);
  }

  default void setBuilderPreregistrations(
      final SszList<BuilderPreregistration> builderPreregistrations) {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PREREGISTRATIONS);
    set(fieldIndex, builderPreregistrations);
  }

  @Override
  default SszMutableVector<SszList<SszList<SszUInt64>>> getAggregationCommitteeWindow() {
    final int fieldIndex = getSchema().getFieldIndex(AGGREGATION_COMMITTEE_WINDOW);
    return getAnyByRef(fieldIndex);
  }

  default void setAggregationCommitteeWindow(
      final SszVector<SszList<SszList<SszUInt64>>> aggregationCommitteeWindow) {
    final int fieldIndex = getSchema().getFieldIndex(AGGREGATION_COMMITTEE_WINDOW);
    set(fieldIndex, aggregationCommitteeWindow);
  }
}

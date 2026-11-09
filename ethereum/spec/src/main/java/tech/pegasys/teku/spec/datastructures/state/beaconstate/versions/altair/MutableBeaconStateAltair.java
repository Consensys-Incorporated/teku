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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public interface MutableBeaconStateAltair extends MutableBeaconState, BeaconStateAltair {

  static MutableBeaconStateAltair required(final MutableBeaconState state) {
    return state
        .toMutableVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a altair state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  default SszMutableList<SszByte> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION);
    return getAnyByRef(fieldIndex);
  }

  default void setPreviousEpochParticipation(final SszList<SszByte> newValue) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION);
    set(fieldIndex, newValue);
  }

  @Override
  default SszMutableList<SszByte> getCurrentEpochParticipation() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION);
    return getAnyByRef(fieldIndex);
  }

  default void setCurrentEpochParticipation(final SszList<SszByte> newValue) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION);
    set(fieldIndex, newValue);
  }

  @Override
  default SszMutableUInt64List getInactivityScores() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.INACTIVITY_SCORES);
    return getAnyByRef(fieldIndex);
  }

  default void setInactivityScores(final SszUInt64List newValue) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.INACTIVITY_SCORES);
    set(fieldIndex, newValue);
  }

  default void setCurrentSyncCommittee(final SyncCommittee currentSyncCommittee) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE);
    set(fieldIndex, currentSyncCommittee);
  }

  default void setNextSyncCommittee(final SyncCommittee nextSyncCommittee) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE);
    set(fieldIndex, nextSyncCommittee);
  }

  @Override
  BeaconStateAltair commitChanges();

  @Override
  default Optional<MutableBeaconStateAltair> toMutableVersionAltair() {
    return Optional.of(this);
  }
}

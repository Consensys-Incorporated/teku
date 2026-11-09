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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public interface BeaconStateAltair extends BeaconState {

  static BeaconStateAltair required(final BeaconState state) {
    return state
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an altair state but got: " + state.getClass().getSimpleName()));
  }

  // Participation
  default SszList<SszByte> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION);
    return getAny(fieldIndex);
  }

  default SszList<SszByte> getCurrentEpochParticipation() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION);
    return getAny(fieldIndex);
  }

  default SszUInt64List getInactivityScores() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.INACTIVITY_SCORES);
    return getAny(fieldIndex);
  }

  default SyncCommittee getCurrentSyncCommittee() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE);
    return getAny(fieldIndex);
  }

  default SszBytes32Vector createCurrentSyncCommitteeProof() {
    return createProof(fieldGIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE));
  }

  default SszBytes32Vector createNextSyncCommitteeProof() {
    return createProof(fieldGIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE));
  }

  default SszBytes32Vector createFinalityBranchProof() {
    return createProof(
        GIndexUtil.gIdxCompose(
            fieldGIndex(BeaconStateFields.FINALIZED_CHECKPOINT),
            Checkpoint.SSZ_SCHEMA.getChildGeneralizedIndex(
                Checkpoint.SSZ_SCHEMA.getFieldIndex("root"))));
  }

  private long fieldGIndex(final BeaconStateFields field) {
    return getSchema().getChildGeneralizedIndex(getSchema().getFieldIndex(field));
  }

  private SszBytes32Vector createProof(final long gIndex) {
    final List<Bytes32> proof = MerkleUtil.constructMerkleProof(getBackingNode(), gIndex);
    return SszBytes32VectorSchema.create(proof.size())
        .createFromElements(proof.stream().map(SszBytes32::of).toList());
  }

  default SyncCommittee getNextSyncCommittee() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE);
    return getAny(fieldIndex);
  }

  @Override
  default Optional<BeaconStateAltair> toVersionAltair() {
    return Optional.of(this);
  }

  @Override
  MutableBeaconStateAltair createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateAltair updatedAltair(final Mutator<MutableBeaconStateAltair, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStateAltair writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }
}

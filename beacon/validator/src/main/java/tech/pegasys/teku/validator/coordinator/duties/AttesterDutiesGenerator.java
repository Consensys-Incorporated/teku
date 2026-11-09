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

package tech.pegasys.teku.validator.coordinator.duties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;

public class AttesterDutiesGenerator {
  private final Spec spec;

  public AttesterDutiesGenerator(final Spec spec) {
    this.spec = spec;
  }

  public AttesterDuties getAttesterDutiesFromIndicesAndState(
      final BeaconState state,
      final UInt64 epoch,
      final IntCollection validatorIndices,
      final boolean isChainHeadOptimistic) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(spec.getCurrentEpoch(state))
            ? spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state)
            : spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
    final List<AttesterDuty> duties = createAttesterDuties(state, epoch, validatorIndices);
    return new AttesterDuties(isChainHeadOptimistic, dependentRoot, duties);
  }

  private List<AttesterDuty> createAttesterDuties(
      final BeaconState state, final UInt64 epoch, final IntCollection validatorIndices) {
    final List<AttesterDuty> attesterDutyList = new ArrayList<>();
    final BeaconStateAccessors beaconStateAccessors = spec.atEpoch(epoch).beaconStateAccessors();
    final UInt64 committeeCountPerSlot =
        beaconStateAccessors.getCommitteeCountPerSlot(state, epoch);
    final Int2ObjectMap<CommitteeAssignment> validatorIndexToCommitteeAssignmentMap =
        spec.getValidatorIndexToCommitteeAssignmentMap(state, epoch);
    for (final int validatorIndex : validatorIndices) {
      final CommitteeAssignment committeeAssignment =
          validatorIndexToCommitteeAssignmentMap.get(validatorIndex);
      if (committeeAssignment != null) {
        attesterDutyFromCommitteeAssignment(
                committeeAssignment, validatorIndex, committeeCountPerSlot, state)
            .ifPresent(attesterDutyList::add);
      }
    }
    return attesterDutyList;
  }

  private Optional<AttesterDuty> attesterDutyFromCommitteeAssignment(
      final CommitteeAssignment committeeAssignment,
      final int validatorIndex,
      final UInt64 committeeCountPerSlot,
      final BeaconState state) {
    return spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
        .map(
            publicKey ->
                new AttesterDuty(
                    publicKey,
                    validatorIndex,
                    committeeAssignment.committee().size(),
                    committeeAssignment.committeeIndex().intValue(),
                    committeeCountPerSlot.intValue(),
                    committeeAssignment.committee().indexOf(validatorIndex),
                    committeeAssignment.slot()));
  }
}

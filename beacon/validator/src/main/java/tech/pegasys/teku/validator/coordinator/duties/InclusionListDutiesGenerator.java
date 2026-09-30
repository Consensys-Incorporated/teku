/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class InclusionListDutiesGenerator {
  private final Spec spec;

  public InclusionListDutiesGenerator(final Spec spec) {
    this.spec = spec;
  }

  public InclusionListDuties getInclusionListDutiesFromIndicesAndState(
      final BeaconState state,
      final UInt64 epoch,
      final IntCollection validatorIndices,
      final boolean isChainHeadOptimistic) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(spec.getCurrentEpoch(state))
            ? spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state)
            : spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
    final List<InclusionListDuty> duties =
        createInclusionListDuties(state, epoch, validatorIndices);
    return new InclusionListDuties(isChainHeadOptimistic, dependentRoot, duties);
  }

  private List<InclusionListDuty> createInclusionListDuties(
      final BeaconState state, final UInt64 epoch, final IntCollection validatorIndices) {
    final List<InclusionListDuty> inclusionListDutyList = new ArrayList<>();
    final Int2ObjectMap<UInt64> validatorIndexToInclusionListAssignmentSlotMap =
        spec.getValidatorIndexInclusionListAssignmentSlotMap(state, epoch);

    for (final int validatorIndex : validatorIndices) {
      Optional.ofNullable(validatorIndexToInclusionListAssignmentSlotMap.get(validatorIndex))
          .flatMap(slot -> inclusionListDutyFromCommitteeAssignment(slot, validatorIndex, state))
          .ifPresent(inclusionListDutyList::add);
    }
    return inclusionListDutyList;
  }

  private Optional<InclusionListDuty> inclusionListDutyFromCommitteeAssignment(
      final UInt64 slot, final int validatorIndex, final BeaconState state) {
    return spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
        .map(publicKey -> new InclusionListDuty(publicKey, UInt64.valueOf(validatorIndex), slot));
  }
}

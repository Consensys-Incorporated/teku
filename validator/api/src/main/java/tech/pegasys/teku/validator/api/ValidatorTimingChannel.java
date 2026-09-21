/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.api;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;

public interface ValidatorTimingChannel extends VoidReturningChannelInterface {
  default void onSlot(final UInt64 slot) {}

  default void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  default void onPossibleMissedEvents() {}

  default void onValidatorsAdded() {}

  default void onBlockProductionDue(final UInt64 slot) {}

  default void onAttestationCreationDue(final UInt64 slot) {}

  default void onAttestationAggregationDue(final UInt64 slot) {}

  default void onSyncCommitteeCreationDue(final UInt64 slot) {}

  default void onContributionCreationDue(final UInt64 slot) {}

  default void onPayloadAttestationCreationDue(final UInt64 slot) {}

  default void onInclusionListCreationDue(final UInt64 slot) {}

  default void onAttesterSlashing(final AttesterSlashing attesterSlashing) {}

  default void onInclusionListDue(final UInt64 slot) {}

  default void onProposerSlashing(final ProposerSlashing proposerSlashing) {}

  default void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {}

  default void onProposerDutiesLoaded(final UInt64 epoch, final ProposerDuties proposerDuties) {}
}

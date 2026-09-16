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

package tech.pegasys.teku.spec.logic.common.operations.validation;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator;

public interface OperationValidator {
  Optional<OperationInvalidReason> validateAttestationData(
      Fork fork, BeaconState state, AttestationData data);

  Optional<OperationInvalidReason> validateAttesterSlashing(
      Fork fork, BeaconState state, AttesterSlashing attesterSlashing);

  Optional<OperationInvalidReason> validateAttesterSlashing(
      Fork fork,
      BeaconState state,
      AttesterSlashing attesterSlashing,
      AttesterSlashingValidator.SlashedIndicesCaptor slashedIndicesCaptor);

  Optional<OperationInvalidReason> validateProposerSlashing(
      Fork fork, BeaconState state, ProposerSlashing proposerSlashing);

  Optional<OperationInvalidReason> validateVoluntaryExit(
      Fork fork, BeaconState state, SignedVoluntaryExit signedExit);

  Optional<OperationInvalidReason> validateVoluntaryExitForGossip(
      Fork fork, BeaconState state, SignedVoluntaryExit signedExit);

  Optional<OperationInvalidReason> validateBlsToExecutionChange(
      Fork fork, BeaconState state, BlsToExecutionChange blsToExecutionChange);
}

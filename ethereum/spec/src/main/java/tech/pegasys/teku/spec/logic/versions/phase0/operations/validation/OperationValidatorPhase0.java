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

package tech.pegasys.teku.spec.logic.versions.phase0.operations.validation;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator.SlashedIndicesCaptor;

public class OperationValidatorPhase0 implements OperationValidator {

  private final AttestationDataValidator attestationDataValidator;
  private final AttesterSlashingValidator attesterSlashingValidator;
  private final ProposerSlashingValidator proposerSlashingValidator;
  private final VoluntaryExitValidator voluntaryExitValidator;

  public OperationValidatorPhase0(
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationDataValidator attestationDataValidator,
      final AttestationUtil attestationUtil,
      final VoluntaryExitValidator voluntaryExitValidator) {
    this.attestationDataValidator = attestationDataValidator;
    this.attesterSlashingValidator =
        new AttesterSlashingValidator(predicates, beaconStateAccessors, attestationUtil);
    this.proposerSlashingValidator =
        new ProposerSlashingValidator(predicates, beaconStateAccessors);
    this.voluntaryExitValidator = voluntaryExitValidator;
  }

  @Override
  public Optional<OperationInvalidReason> validateAttestationData(
      final Fork fork, final BeaconState state, final AttestationData data) {
    return attestationDataValidator.validate(fork, state, data);
  }

  @Override
  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final Fork fork, final BeaconState state, final AttesterSlashing attesterSlashing) {
    return attesterSlashingValidator.validate(fork, state, attesterSlashing);
  }

  @Override
  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final Fork fork,
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      final SlashedIndicesCaptor slashedIndicesCaptor) {
    return attesterSlashingValidator.validate(fork, state, attesterSlashing, slashedIndicesCaptor);
  }

  @Override
  public Optional<OperationInvalidReason> validateProposerSlashing(
      final Fork fork, final BeaconState state, final ProposerSlashing proposerSlashing) {
    return proposerSlashingValidator.validate(fork, state, proposerSlashing);
  }

  @Override
  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    return voluntaryExitValidator.validate(fork, state, signedExit);
  }

  @Override
  public Optional<OperationInvalidReason> validateVoluntaryExitForGossip(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    return voluntaryExitValidator.validateForGossip(fork, state, signedExit);
  }

  @Override
  public Optional<OperationInvalidReason> validateBlsToExecutionChange(
      final Fork fork, final BeaconState state, final BlsToExecutionChange blsToExecutionChange) {
    return Optional.of(() -> "Bls to execution changes are not valid before Capella fork");
  }
}

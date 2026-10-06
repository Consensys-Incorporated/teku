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

package tech.pegasys.teku.spec.logic.versions.electra.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator;

public class VoluntaryExitValidatorElectra extends VoluntaryExitValidator {
  private final BeaconStateAccessorsElectra stateAccessorsElectra;

  public VoluntaryExitValidatorElectra(
      final SpecConfig specConfig,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors) {
    super(specConfig, predicates, beaconStateAccessors);
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    return firstOf(
        () -> super.validate(fork, state, signedExit),
        () -> validateElectraConditions(stateElectra, signedExit));
  }

  @Override
  public Optional<OperationInvalidReason> validateForGossip(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    return firstOf(
        () -> super.validateForGossip(fork, state, signedExit),
        () -> validateElectraConditions(stateElectra, signedExit));
  }

  @VisibleForTesting
  Optional<OperationInvalidReason> validateElectraConditions(
      final BeaconStateElectra stateElectra, final SignedVoluntaryExit exit) {
    return check(
        stateAccessorsElectra
            .getPendingBalanceToWithdraw(stateElectra, exit.getValidatorId().intValue())
            .equals(UInt64.ZERO),
        VoluntaryExitValidator.ExitInvalidReason.pendingWithdrawalsInQueue());
  }
}

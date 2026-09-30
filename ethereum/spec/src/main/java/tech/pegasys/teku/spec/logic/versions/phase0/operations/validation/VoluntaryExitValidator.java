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

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationStateTransitionValidator;

public class VoluntaryExitValidator
    implements OperationStateTransitionValidator<SignedVoluntaryExit> {

  private final SpecConfig specConfig;
  private final Predicates predicates;
  private final BeaconStateAccessors beaconStateAccessors;

  public VoluntaryExitValidator(
      final SpecConfig specConfig,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    final VoluntaryExit exit = signedExit.getMessage();
    return firstOf(
        () ->
            check(
                UInt64.valueOf(state.getValidators().size())
                    .isGreaterThan(exit.getValidatorIndex()),
                ExitInvalidReason.invalidValidatorIndex()),
        () ->
            check(
                predicates.isActiveValidator(
                    getValidator(state, exit), beaconStateAccessors.getCurrentEpoch(state)),
                ExitInvalidReason.validatorInactive()),
        () ->
            check(
                getValidator(state, exit).getExitEpoch().equals(FAR_FUTURE_EPOCH),
                ExitInvalidReason.exitInitiated()),
        () ->
            check(
                beaconStateAccessors.getCurrentEpoch(state).isGreaterThanOrEqualTo(exit.getEpoch()),
                ExitInvalidReason.submittedTooEarly()),
        () -> {
          UInt64 exitEpoch =
              getValidator(state, exit)
                  .getActivationEpoch()
                  .plus(specConfig.getShardCommitteePeriod());
          return check(
              beaconStateAccessors.getCurrentEpoch(state).isGreaterThanOrEqualTo(exitEpoch),
              ExitInvalidReason.validatorTooYoung(exitEpoch));
        });
  }

  public Optional<OperationInvalidReason> validateForGossip(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    final VoluntaryExit exit = signedExit.getMessage();
    return firstOf(
        () ->
            check(
                UInt64.valueOf(state.getValidators().size())
                    .isGreaterThan(exit.getValidatorIndex()),
                ExitInvalidReason.invalidValidatorIndex()),
        () ->
            check(
                predicates.isActiveValidator(
                    getValidator(state, exit), beaconStateAccessors.getCurrentEpoch(state)),
                ExitInvalidReason.validatorInactive()),
        () -> {
          final UInt64 exitEpoch =
              getValidator(state, exit)
                  .getActivationEpoch()
                  .plus(specConfig.getShardCommitteePeriod());
          return check(
              beaconStateAccessors.getCurrentEpoch(state).isGreaterThanOrEqualTo(exitEpoch),
              ExitInvalidReason.validatorTooYoung(exitEpoch));
        });
  }

  private Validator getValidator(final BeaconState state, final VoluntaryExit exit) {
    return state.getValidators().get(exit.getValidatorIndex().intValue());
  }

  public static class ExitInvalidReason {

    public static OperationInvalidReason invalidValidatorIndex() {
      return () -> "Invalid validator index";
    }

    public static OperationInvalidReason validatorInactive() {
      return () -> "Validator is not active";
    }

    public static OperationInvalidReason exitInitiated() {
      return () -> "Validator has already initiated exit";
    }

    public static OperationInvalidReason submittedTooEarly() {
      return () -> "Specified exit epoch is still in the future";
    }

    public static OperationInvalidReason validatorTooYoung(final UInt64 exitEpoch) {
      return () -> "Validator cannot exit until epoch " + exitEpoch;
    }

    public static OperationInvalidReason invalidSignature() {
      return () -> "Signature is invalid";
    }

    public static OperationInvalidReason pendingWithdrawalsInQueue() {
      return () ->
          "Validator cannot be exited while there are pending withdrawals in the withdrawal queue";
    }
  }
}

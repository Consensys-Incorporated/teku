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

package tech.pegasys.teku.spec.logic.versions.capella.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;
import static tech.pegasys.teku.spec.logic.versions.capella.operations.validation.BlsToExecutionChangesValidator.BlsToExecutionChangeInvalidReason.invalidValidatorIndex;
import static tech.pegasys.teku.spec.logic.versions.capella.operations.validation.BlsToExecutionChangesValidator.BlsToExecutionChangeInvalidReason.publicKeyNotMatchingCredentials;
import static tech.pegasys.teku.spec.logic.versions.capella.operations.validation.BlsToExecutionChangesValidator.BlsToExecutionChangeInvalidReason.validatorWithoutWithdrawalCredentials;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationStateTransitionValidator;

public class BlsToExecutionChangesValidator
    implements OperationStateTransitionValidator<BlsToExecutionChange> {

  BlsToExecutionChangesValidator() {}

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final BlsToExecutionChange blsToExecutionChange) {
    final Optional<OperationInvalidReason> indexCheckResult =
        verifyValidatorIndex(state, blsToExecutionChange);
    if (indexCheckResult.isPresent()) {
      return indexCheckResult;
    }

    final int validatorIndex = blsToExecutionChange.getValidatorIndex().intValue();
    final Bytes32 withdrawalCredentials =
        getWithdrawalCredentialsForValidatorIndex(state, validatorIndex);

    return firstOf(
        () -> verifyWithdrawalCredentialPrefix(validatorIndex, withdrawalCredentials),
        () -> verifyBlsPubKeyMatches(blsToExecutionChange, validatorIndex, withdrawalCredentials));
  }

  private Optional<OperationInvalidReason> verifyValidatorIndex(
      final BeaconState state, final BlsToExecutionChange blsToExecutionChange) {
    final int validatorIndex = blsToExecutionChange.getValidatorIndex().intValue();
    final boolean validatorIndexWithinBounds = validatorIndex < state.getValidators().size();
    return check(validatorIndexWithinBounds, invalidValidatorIndex());
  }

  private Optional<OperationInvalidReason> verifyWithdrawalCredentialPrefix(
      final int validatorIndex, final Bytes32 withdrawalCredentials) {

    return check(
        withdrawalCredentials.get(0) == WithdrawalPrefixes.BLS_WITHDRAWAL_PREFIX.get(0),
        validatorWithoutWithdrawalCredentials(validatorIndex, withdrawalCredentials));
  }

  private Optional<OperationInvalidReason> verifyBlsPubKeyMatches(
      final BlsToExecutionChange blsToExecutionChange,
      final int validatorIndex,
      final Bytes32 withdrawalCredentials) {

    boolean matchingBlsPubKey =
        withdrawalCredentials
            .slice(1)
            .equals(
                Hash.sha256(blsToExecutionChange.getFromBlsPubkey().toBytesCompressed()).slice(1));

    return check(
        matchingBlsPubKey, publicKeyNotMatchingCredentials(validatorIndex, blsToExecutionChange));
  }

  private static Bytes32 getWithdrawalCredentialsForValidatorIndex(
      final BeaconState beaconState, final int validatorIndex) {
    return beaconState.getValidators().get(validatorIndex).getWithdrawalCredentials();
  }

  public static class BlsToExecutionChangeInvalidReason {

    public static OperationInvalidReason invalidValidatorIndex() {
      return () -> "Invalid validator index";
    }

    public static OperationInvalidReason validatorWithoutWithdrawalCredentials(
        final int validatorIndex, final Bytes32 withdrawalCredentials) {
      return () ->
          String.format(
              "Not using BLS withdrawal credentials for validator %d Credentials: %s",
              validatorIndex, withdrawalCredentials);
    }

    public static OperationInvalidReason publicKeyNotMatchingCredentials(
        final int validatorIndex, final BlsToExecutionChange operation) {
      return () ->
          String.format(
              "Validator %d public key does not match withdrawal credentials: %s",
              validatorIndex, operation.getFromBlsPubkey());
    }
  }
}

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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_BYTE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class PredicatesElectra extends Predicates {
  private final SpecConfigElectra configElectra;

  public PredicatesElectra(final SpecConfig specConfig) {
    super(specConfig);
    this.configElectra = SpecConfigElectra.required(specConfig);
  }

  public static PredicatesElectra required(final Predicates predicates) {
    return predicates
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra predicates but got "
                        + predicates.getClass().getSimpleName()));
  }

  @Override
  public Optional<PredicatesElectra> toVersionElectra() {
    return Optional.of(this);
  }

  /**
   * is_partially_withdrawable_validator
   *
   * @param validator the validator being checked
   * @param balance the validator's balance
   * @return
   */
  @Override
  public boolean isPartiallyWithdrawableValidator(final Validator validator, final UInt64 balance) {
    return hasExecutionWithdrawalCredential(validator)
        && isPartiallyWithdrawableValidatorEth1CredentialsChecked(validator, balance);
  }

  @Override
  public boolean isPartiallyWithdrawableValidatorEth1CredentialsChecked(
      final Validator validator, final UInt64 balance) {
    final UInt64 maxEffectiveBalance =
        hasCompoundingWithdrawalCredential(validator)
            ? configElectra.getMaxEffectiveBalanceElectra()
            : configElectra.getMinActivationBalance();
    final boolean hasMaxEffectiveBalance =
        validator.getEffectiveBalance().equals(maxEffectiveBalance);
    final boolean hasExcessBalance = balance.isGreaterThan(maxEffectiveBalance);

    return hasMaxEffectiveBalance && hasExcessBalance;
  }

  /**
   * is_fully_withdrawable_validator
   *
   * @param validator the validator being checked
   * @param balance the validator's balance
   * @param epoch the current epoch
   * @return if the validator is exited and withdrawable
   */
  @Override
  public boolean isFullyWithdrawableValidator(
      final Validator validator, final UInt64 balance, final UInt64 epoch) {
    return hasExecutionWithdrawalCredential(validator)
        && isFullyWithdrawableValidatorCredentialsChecked(validator, balance, epoch);
  }

  /**
   * has_execution_withdrawal_credential
   *
   * @param validator
   * @return
   */
  @Override
  public boolean hasExecutionWithdrawalCredential(final Validator validator) {
    return hasEth1WithdrawalCredential(validator) || hasCompoundingWithdrawalCredential(validator);
  }

  /**
   * has_compounding_withdrawal_credential
   *
   * @param validator
   * @return
   */
  public boolean hasCompoundingWithdrawalCredential(final Validator validator) {
    return isCompoundingWithdrawalCredential(validator.getWithdrawalCredentials());
  }

  /**
   * is_compounding_withdrawal_credential
   *
   * @param withdrawalCredentials
   * @return
   */
  public boolean isCompoundingWithdrawalCredential(final Bytes32 withdrawalCredentials) {
    return withdrawalCredentials.get(0) == COMPOUNDING_WITHDRAWAL_BYTE;
  }
}

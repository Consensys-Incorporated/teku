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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PredicatesTest {

  private Predicates predicates;
  private static final Spec SPEC = TestSpecFactory.createMainnet(SpecMilestone.CAPELLA);
  private static final SpecConfigCapella SPEC_CONFIG_CAPELLA =
      SPEC.getGenesisSpecConfig().toVersionCapella().orElseThrow();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);
  static final Bytes32 ETH_1_WITHDRAWAL_CREDENTIALS =
      DATA_STRUCTURE_UTIL.randomEth1WithdrawalCredentials();
  static final Bytes32 BLS_WITHDRAWAL_CREDENTIALS =
      DATA_STRUCTURE_UTIL.randomBlsWithdrawalCredentials();

  @BeforeEach
  public void before() {
    this.predicates = new Predicates(SPEC_CONFIG_CAPELLA);
  }

  @ParameterizedTest
  @MethodSource("getExecutionAddressUncheckedArgs")
  public void getExecutionAddressUnchecked(final Validator validator) {
    final Eth1Address executionAddress =
        Predicates.getExecutionAddressUnchecked(validator.getWithdrawalCredentials());
    assertThat(executionAddress)
        .isEqualTo(Eth1Address.fromBytes(validator.getWithdrawalCredentials().slice(12)));
  }

  private static Stream<Arguments> getExecutionAddressUncheckedArgs() {
    final Bytes32 eth1WithdrawalCredentials = DATA_STRUCTURE_UTIL.randomEth1WithdrawalCredentials();
    final Bytes32 compoundingWithdrawalCredentials =
        DATA_STRUCTURE_UTIL.randomCompoundingWithdrawalCredentials();
    // Even though it does not make sense to extract Eth1 address from blsWithdrawalCredentials, the
    // method does not check for it so it will return the last 20 bytes of "anything".
    final Bytes32 blsWithdrawalCredentials = DATA_STRUCTURE_UTIL.randomBlsWithdrawalCredentials();

    return Stream.of(
        Arguments.of(
            DATA_STRUCTURE_UTIL
                .randomValidator()
                .withWithdrawalCredentials(eth1WithdrawalCredentials)),
        Arguments.of(
            DATA_STRUCTURE_UTIL
                .randomValidator()
                .withWithdrawalCredentials(compoundingWithdrawalCredentials)),
        Arguments.of(
            DATA_STRUCTURE_UTIL
                .randomValidator()
                .withWithdrawalCredentials(blsWithdrawalCredentials)));
  }

  @Test
  public void
      hasEth1WithdrawalCredentialShouldReturnTrueForValidatorWithEth1WithdrawalCredentials() {
    final Validator validator =
        DATA_STRUCTURE_UTIL
            .randomValidator()
            .withWithdrawalCredentials(ETH_1_WITHDRAWAL_CREDENTIALS);

    assertThat(predicates.hasEth1WithdrawalCredential(validator)).isTrue();
  }

  @Test
  public void
      hasEth1WithdrawalCredentialShouldReturnFalseForValidatorWithBls1WithdrawalCredentials() {
    final Validator validator =
        DATA_STRUCTURE_UTIL.randomValidator().withWithdrawalCredentials(BLS_WITHDRAWAL_CREDENTIALS);

    assertThat(predicates.hasEth1WithdrawalCredential(validator)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("isFullyWithdrawableValidatorArgs")
  public void isFullyWithdrawableValidator(
      final String description,
      final Bytes32 withdrawalCredentials,
      final UInt64 withdrawableEpoch,
      final UInt64 balance,
      final UInt64 epoch,
      final boolean expectedValue) {

    final Validator validator =
        DATA_STRUCTURE_UTIL
            .randomValidator()
            .withWithdrawalCredentials(withdrawalCredentials)
            .withWithdrawableEpoch(withdrawableEpoch);

    assertThat(predicates.isFullyWithdrawableValidator(validator, balance, epoch))
        .isEqualTo(expectedValue);
  }

  private static Stream<Arguments> isFullyWithdrawableValidatorArgs() {
    final Bytes32 eth1WithdrawalsCredentials =
        DATA_STRUCTURE_UTIL.randomEth1WithdrawalCredentials();
    final Bytes32 blsWithdrawalsCredentials = DATA_STRUCTURE_UTIL.randomBlsWithdrawalCredentials();

    return Stream.of(
        Arguments.of(
            "Eligible validator", // description
            eth1WithdrawalsCredentials, // withdrawal credentials
            UInt64.ZERO, // withdrawable epoch
            UInt64.ONE, // balance
            UInt64.ONE, // epoch
            true), // expected result from isFullyWithdrawableValidator
        Arguments.of(
            "Ineligible validator without Eth1 credentials",
            blsWithdrawalsCredentials, // withdrawal credentials
            UInt64.ZERO,
            UInt64.ONE,
            UInt64.ONE,
            false),
        Arguments.of(
            "Eligible validator with withdrawable epoch as the current epoch",
            eth1WithdrawalsCredentials,
            UInt64.ONE, // withdrawable epoch
            UInt64.ONE,
            UInt64.ONE,
            true),
        Arguments.of(
            "Ineligible validator with withdrawable epoch in the future",
            eth1WithdrawalsCredentials,
            UInt64.valueOf(2), // withdrawable epoch
            UInt64.ONE,
            UInt64.ONE,
            false),
        Arguments.of(
            "Ineligible validator with withdrawable epoch in the far future",
            eth1WithdrawalsCredentials,
            UInt64.MAX_VALUE, // withdrawable epoch
            UInt64.ONE,
            UInt64.ONE,
            false),
        Arguments.of(
            "Ineligible validator with zero balance",
            eth1WithdrawalsCredentials,
            UInt64.ZERO,
            UInt64.ZERO, // balance
            UInt64.ONE,
            false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("isPartiallyWithdrawableValidatorArgs")
  public void isPartiallyWithdrawableValidator(
      final String description,
      final Bytes32 withdrawalCredentials,
      final UInt64 effectiveBalance,
      final UInt64 balance,
      final boolean expectedValue) {

    final Validator validator =
        DATA_STRUCTURE_UTIL
            .randomValidator()
            .withWithdrawalCredentials(withdrawalCredentials)
            .withEffectiveBalance(effectiveBalance);

    assertThat(predicates.isPartiallyWithdrawableValidator(validator, balance))
        .isEqualTo(expectedValue);
  }

  private static Stream<Arguments> isPartiallyWithdrawableValidatorArgs() {
    return Stream.of(
        Arguments.of(
            "Eligible validator", // description
            ETH_1_WITHDRAWAL_CREDENTIALS, // withdrawalCredentials
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance(), // effective balance
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance().plus(UInt64.ONE), // balance
            true), // expected result from isPartiallyWithdrawable
        Arguments.of(
            "Ineligible validator without Eth1 credentials",
            BLS_WITHDRAWAL_CREDENTIALS, // withdrawal credentials
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance(),
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance().plus(UInt64.ONE),
            false),
        Arguments.of(
            "Ineligible validator with effective balance different to MAX_EFFECTIVE_BALANCE",
            ETH_1_WITHDRAWAL_CREDENTIALS,
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance().minus(UInt64.ONE), // effective balance
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance().plus(UInt64.ONE),
            false),
        Arguments.of(
            "Ineligible validator with balance less than MAX_EFFECTIVE_BALANCE",
            ETH_1_WITHDRAWAL_CREDENTIALS,
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance(),
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance().minus(UInt64.ONE), // balance
            false),
        Arguments.of(
            "Ineligible validator with balance equals to MAX_EFFECTIVE_BALANCE",
            ETH_1_WITHDRAWAL_CREDENTIALS,
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance(),
            SPEC_CONFIG_CAPELLA.getMaxEffectiveBalance(), // balance
            false));
  }
}

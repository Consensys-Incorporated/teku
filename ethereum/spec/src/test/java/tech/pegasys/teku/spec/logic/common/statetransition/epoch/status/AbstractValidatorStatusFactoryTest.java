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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractValidatorStatusFactoryTest {

  protected final Spec spec = createSpec();
  private final AbstractValidatorStatusFactory validatorStatusFactory = createFactory();
  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private UInt64 balance(final int amount) {
    return genesisConfig.getEffectiveBalanceIncrement().times(amount);
  }

  protected abstract AbstractValidatorStatusFactory createFactory();

  protected abstract Spec createSpec();

  private final UInt64 withdrawableEpoch = dataStructureUtil.randomEpoch();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createValidatorStatus_shouldSetSlashedCorrectly(final boolean slashed) {
    final Validator validator = dataStructureUtil.randomValidator().withSlashed(slashed);
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.ZERO, UInt64.ZERO)
                .isSlashed())
        .isEqualTo(slashed);
  }

  @Test
  void createValidatorStatus_shouldBeWithdrawableWhenWithdrawalEpochBeforeCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawableEpoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(7), UInt64.valueOf(8))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void createValidatorStatus_shouldBeWithdrawableWhenWithdrawalEpochEqualToCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawableEpoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(6), UInt64.valueOf(7))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void createValidatorStatus_shouldNotBeWithdrawableWhenWithdrawalEpochAfterCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawableEpoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(5), UInt64.valueOf(6))
                .isWithdrawableInCurrentEpoch())
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 3, Integer.MAX_VALUE, Long.MAX_VALUE})
  void createValidatorStatus_shouldSetEffectiveBalanceCorrectly(final long balance) {
    final UInt64 effectiveBalance = UInt64.valueOf(balance);
    final Validator validator =
        dataStructureUtil.randomValidator().withEffectiveBalance(effectiveBalance);
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.ZERO, UInt64.ZERO)
                .getCurrentEpochEffectiveBalance())
        .isEqualTo(effectiveBalance);
  }

  @ParameterizedTest
  @MethodSource("activeEpochs")
  void shouldSetActiveInCurrentEpochCorrectly(
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 currentEpoch,
      final boolean isActive) {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withActivationEpoch(activationEpoch)
            .withExitEpoch(exitEpoch);
    final ValidatorStatus validatorStatus =
        validatorStatusFactory.createValidatorStatus(
            validator, currentEpoch.minusMinZero(1), currentEpoch);
    assertThat(validatorStatus.isActiveInCurrentEpoch()).isEqualTo(isActive);
  }

  @ParameterizedTest
  @MethodSource("activeEpochs")
  void createValidatorStatus_shouldSetActiveInPreviousEpochCorrectly(
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 previousEpoch,
      final boolean isActive) {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withActivationEpoch(activationEpoch)
            .withExitEpoch(exitEpoch);
    final ValidatorStatus validatorStatus =
        validatorStatusFactory.createValidatorStatus(
            validator, previousEpoch, previousEpoch.plus(1));
    assertThat(validatorStatus.isActiveInPreviousEpoch()).isEqualTo(isActive);
  }

  static Stream<Arguments> activeEpochs() {
    return Stream.of(
        Arguments.of(FAR_FUTURE_EPOCH, FAR_FUTURE_EPOCH, UInt64.valueOf(0), false),
        Arguments.of(UInt64.valueOf(0), FAR_FUTURE_EPOCH, UInt64.valueOf(0), true),
        Arguments.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(0), false),
        Arguments.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(1), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(0), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(1), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(2), false),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(3), false));
  }

  @Test
  void createTotalBalances_shouldAddCurrentEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), withdrawableEpoch, true, false, true),
            new ValidatorStatus(true, true, balance(5), withdrawableEpoch, true, true, false),
            new ValidatorStatus(false, false, balance(13), withdrawableEpoch, false, false, false));
    // Should include both statuses active in current epoch for a total of 12.
    assertThat(
            validatorStatusFactory.createTotalBalances(statuses).getCurrentEpochActiveValidators())
        .isEqualTo(balance(12));
  }

  @Test
  void createTotalBalances_shouldAddPreviousEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), withdrawableEpoch, false, true, true),
            new ValidatorStatus(true, true, balance(5), withdrawableEpoch, true, true, true),
            new ValidatorStatus(false, false, balance(13), withdrawableEpoch, false, false, false));
    // Should include both statuses active in previous epoch for a total of 12.
    assertThat(
            validatorStatusFactory.createTotalBalances(statuses).getPreviousEpochActiveValidators())
        .isEqualTo(balance(12));
  }

  @Test
  void createTotalBalances_shouldExcludeSlashedValidatorsFromAttestersTotals() {
    final List<ValidatorStatus> statuses =
        List.of(
            createWithAllAttesterFlags(true, 8),
            createWithAllAttesterFlags(false, 11),
            createWithAllAttesterFlags(false, 6));

    final UInt64 expectedBalance = balance(11 + 6);
    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochSourceAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochSourceAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(expectedBalance);
  }

  @Test
  void createTotalBalances_shouldCalculateCurrentEpochAttestersBalances() {
    final List<ValidatorStatus> statuses =
        List.of(
            createValidator(7)
                .updateCurrentEpochSourceAttester(true)
                .updateCurrentEpochTargetAttester(true),
            createValidator(9)
                .updateCurrentEpochSourceAttester(true)
                .updateCurrentEpochTargetAttester(true),
            createValidator(14).updateCurrentEpochSourceAttester(true),
            createValidator(17)
                .updateCurrentEpochSourceAttester(false)
                .updatePreviousEpochSourceAttester(true));

    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochSourceAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(balance(7 + 9));
  }

  @Test
  void createTotalBalances_shouldCalculatePreviousEpochAttestersBalances() {
    final List<ValidatorStatus> statuses =
        List.of(
            createValidator(7)
                .updatePreviousEpochSourceAttester(true)
                .updatePreviousEpochTargetAttester(true),
            createValidator(9)
                .updatePreviousEpochSourceAttester(true)
                .updatePreviousEpochTargetAttester(true)
                .updatePreviousEpochHeadAttester(true),
            createValidator(14).updatePreviousEpochSourceAttester(true),
            createValidator(17).updateCurrentEpochSourceAttester(true));

    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getPreviousEpochSourceAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(balance(7 + 9));
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(balance(9));
  }

  @Test
  void createTotalBalances_shouldReturnMinimumOfOneEffectiveBalanceIncrement() {
    final TotalBalances balances = validatorStatusFactory.createTotalBalances(emptyList());
    final UInt64 effectiveBalanceInc = genesisConfig.getEffectiveBalanceIncrement();

    assertThat(balances.getCurrentEpochActiveValidators()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochActiveValidators()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getCurrentEpochSourceAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochSourceAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(effectiveBalanceInc);
  }

  @Test
  public void recreateValidatorStatusesShouldAppendNewValidatorsAndKeepOrder()
      throws IllegalAccessException {
    final BeaconState state =
        dataStructureUtil
            .stateBuilder(spec.getGenesisSpec().getMilestone(), 3, 1)
            .setSlotToStartOfEpoch(UInt64.ONE)
            .build();
    // Magic to get around requiring a full state with valid previous and current epoch attestations
    // (only on Phase0)
    final ValidatorStatusFactory factory = createFactory();
    FieldUtils.writeField(factory, "attestationUtil", mock(AttestationUtil.class), true);

    final ValidatorStatuses validatorStatuses = factory.createValidatorStatuses(state);

    final UInt64 currentEpoch = spec.computeEpochAtSlot(state.getSlot());
    final Validator newValidator = dataStructureUtil.validatorBuilder().slashed(true).build();
    final ValidatorStatus newValidatorStatus =
        factory.createValidatorStatus(newValidator, currentEpoch.minusMinZero(1), currentEpoch);

    final ValidatorStatuses updatedValidatorStatuses =
        factory.recreateValidatorStatuses(validatorStatuses, List.of(newValidatorStatus));

    final ValidatorStatus[] expectedStatuses =
        Stream.concat(validatorStatuses.getStatuses().stream(), Stream.of(newValidatorStatus))
            .toArray(ValidatorStatus[]::new);

    assertThat(updatedValidatorStatuses.getStatuses()).containsExactly(expectedStatuses);
  }

  @Test
  public void shouldNotRecalculateValidatorStatusForPreviousExistingValidators()
      throws IllegalAccessException {
    final ValidatorStatusFactory factory = spy(createFactory());
    // Magic to get around requiring a full state with valid previous and current epoch attestations
    // (only on Phase0)
    FieldUtils.writeField(factory, "attestationUtil", mock(AttestationUtil.class), true);

    final int validatorCount = 10;
    final BeaconState state =
        dataStructureUtil
            .stateBuilder(spec.getGenesisSpec().getMilestone(), validatorCount, 1)
            .build();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(state.getSlot());
    final ValidatorStatuses validatorStatuses = factory.createValidatorStatuses(state);

    // Created ValidatorStatus for all validators in state
    verify(factory, times(validatorCount)).createValidatorStatus(any(), any(), any());

    final Validator newValidator = dataStructureUtil.validatorBuilder().slashed(true).build();
    final ValidatorStatus newValidatorStatus =
        factory.createValidatorStatus(newValidator, currentEpoch.minusMinZero(1), currentEpoch);
    // Created ValidatorStatus for the new validator
    verify(factory, times(validatorCount + 1)).createValidatorStatus(any(), any(), any());

    factory.recreateValidatorStatuses(validatorStatuses, List.of(newValidatorStatus));

    // Verifying that recreateValidatorStatuses does not trigger any ValidatorStatus creation
    verify(factory, times(validatorCount + 1)).createValidatorStatus(any(), any(), any());
  }

  private ValidatorStatus createValidator(final int effectiveBalance) {
    return new ValidatorStatus(
        false, false, balance(effectiveBalance), withdrawableEpoch, true, true, true);
  }

  private ValidatorStatus createWithAllAttesterFlags(
      final boolean slashed, final int effectiveBalance) {
    return new ValidatorStatus(
            slashed, true, balance(effectiveBalance), withdrawableEpoch, true, true, true)
        .updateCurrentEpochSourceAttester(true)
        .updatePreviousEpochSourceAttester(true)
        .updateCurrentEpochTargetAttester(true)
        .updatePreviousEpochTargetAttester(true)
        .updatePreviousEpochHeadAttester(true);
  }
}

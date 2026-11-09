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

package tech.pegasys.teku.spec.logic.versions.electra.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.FULL_EXIT_REQUEST_AMOUNT;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.ProcessorTestHelper;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

class ExecutionRequestsProcessorElectraTest extends ProcessorTestHelper {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetElectra();
  }

  @Test
  public void processesDepositRequests() {
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState ->
                        MutableBeaconStateElectra.required(mutableState)
                            .setDepositRequestsStartIndex(
                                SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX)));
    final int firstElectraDepositRequestIndex = preState.getValidators().size();

    final int depositRequestsCount = 3;
    final List<DepositRequest> depositRequests =
        IntStream.range(0, depositRequestsCount)
            .mapToObj(
                i ->
                    dataStructureUtil.randomDepositRequestWithValidSignature(
                        UInt64.valueOf(firstElectraDepositRequestIndex + i)))
            .toList();

    final BeaconStateElectra state =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processDepositRequests(
                            MutableBeaconStateElectra.required(mutableState), depositRequests)));

    // verify deposit_requests_start_index has been set
    assertThat(state.getDepositRequestsStartIndex())
        .isEqualTo(UInt64.valueOf(firstElectraDepositRequestIndex));
    // verify validators have been added to the state
    assertThat(state.getPendingDeposits()).hasSize(depositRequestsCount);
  }

  @Test
  public void processWithdrawalRequests_WithEmptyWithdrawalRequestsList_DoesNothing() {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState, List.of(), validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForAbsentValidator_DoesNothing() {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());
    final WithdrawalRequest withdrawalRequest = dataStructureUtil.randomWithdrawalRequest();

    // Assert the request does not correspond to an existing validator
    assertThat(
            preState.getValidators().stream()
                .filter(
                    validator ->
                        validator.getPublicKey().equals(withdrawalRequest.getValidatorPubkey())))
        .isEmpty();

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(withdrawalRequest),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processWithdrawalRequests_WithdrawalRequestForValidatorWithoutEth1Credentials_DoesNothing() {
    final Validator validator =
        dataStructureUtil.validatorBuilder().withRandomBlsWithdrawalCredentials().build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestWithWrongSourceAddress_DoesNothing() {
    final Validator validator =
        dataStructureUtil.validatorBuilder().withRandomEth1WithdrawalCredentials().build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final WithdrawalRequest withdrawalRequestWithInvalidSourceAddress =
        dataStructureUtil.withdrawalRequest(
            dataStructureUtil.randomEth1Address(), validator.getPublicKey(), UInt64.ZERO);

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(withdrawalRequestWithInvalidSourceAddress),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestForInactiveValidator_DoesNothing() {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestForValidatorAlreadyExiting_DoesNothing() {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(UInt64.valueOf(1_001))
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForValidatorNotActiveLongEnough_DoesNothing() {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.valueOf(999))
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForEligibleValidator() throws BlockProcessingException {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));
    // The validator we created was the last one added to the list of validators
    int validatorIndex = preState.getValidators().size() - 1;

    // Before processing the exit, the validator has FAR_FUTURE_EPOCH for exit_epoch and
    // withdrawable_epoch
    assertThat(preState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(preState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(
                                dataStructureUtil.withdrawalRequest(
                                    validator, FULL_EXIT_REQUEST_AMOUNT)),
                            validatorExitContextSupplier(preState))));

    // After processing the exit, the validator has exit_epoch and withdrawable_epoch set
    assertThat(postState.getValidators().get(validatorIndex).getExitEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
    assertThat(postState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
  }

  @Test
  public void processWithdrawalRequests_PartialWithdrawalRequestForEligibleValidator() {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomCompoundingWithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final SpecConfigElectra specConfigElectra =
        spec.getSpecConfig(currentEpoch).toVersionElectra().orElseThrow();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));

                      // Give the validator an excessBalance
                      int validatorIndex = mutableState.getValidators().size() - 1;
                      mutableState
                          .getBalances()
                          .set(
                              validatorIndex,
                              SszUInt64.of(
                                  specConfigElectra
                                      .getMinActivationBalance()
                                      .plus(UInt64.valueOf(123_456_789))));
                    }));
    // The validator we created was the last one added to the list of validators
    int validatorIndex = preState.getValidators().size() - 1;

    // Before processing the withdrawal, the validator has FAR_FUTURE_EPOCH for exit_epoch and
    // withdrawable_epoch
    assertThat(preState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(preState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    // Set withdrawal request amount to the validator's excess balance
    final UInt64 amount =
        preState
            .getBalances()
            .get(validatorIndex)
            .get()
            .minus(specConfigElectra.getMinActivationBalance());
    assertThat(amount).isEqualTo(UInt64.valueOf(123_456_789));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getExecutionRequestsProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator, amount)),
                            validatorExitContextSupplier(preState))));

    // After processing the request, the validator hasn't updated these
    assertThat(postState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(postState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    // The validator's balance should be equal to the minimum activation balance
    assertThat(postState.getPendingPartialWithdrawals().size()).isEqualTo(1);
    final PendingPartialWithdrawal mostRecentPendingPartialWithdrawal =
        postState
            .getPendingPartialWithdrawals()
            .get(postState.getPendingPartialWithdrawals().size() - 1);

    assertThat(mostRecentPendingPartialWithdrawal.getValidatorIndex().intValue())
        .isEqualTo(validatorIndex);
    assertThat(mostRecentPendingPartialWithdrawal.getAmount())
        .isEqualTo(UInt64.valueOf(123_456_789));
    assertThat(mostRecentPendingPartialWithdrawal.getWithdrawableEpoch())
        .isEqualTo(UInt64.valueOf(1_261));
  }

  private ExecutionRequestsProcessorElectra getExecutionRequestsProcessor(final BeaconState state) {
    return (ExecutionRequestsProcessorElectra) spec.getExecutionRequestsProcessor(state.getSlot());
  }

  private Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier(
      final BeaconState state) {
    return spec.getGenesisSpec().beaconStateMutators().createValidatorExitContextSupplier(state);
  }
}

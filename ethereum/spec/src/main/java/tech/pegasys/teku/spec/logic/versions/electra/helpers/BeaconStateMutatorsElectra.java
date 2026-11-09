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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_BYTE;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BeaconStateMutatorsElectra extends BeaconStateMutatorsBellatrix {

  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final MiscHelpersElectra miscHelpersElectra;

  private final SpecConfigElectra specConfigElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public static BeaconStateMutatorsElectra required(final BeaconStateMutators beaconStateMutators) {
    checkArgument(
        beaconStateMutators instanceof BeaconStateMutatorsElectra,
        "Expected %s but it was %s",
        BeaconStateMutatorsElectra.class,
        beaconStateMutators.getClass());
    return (BeaconStateMutatorsElectra) beaconStateMutators;
  }

  public BeaconStateMutatorsElectra(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final SchemaDefinitionsElectra schemaDefinitionsElectra) {
    super(SpecConfigBellatrix.required(specConfig), miscHelpers, beaconStateAccessors);
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.miscHelpersElectra = MiscHelpersElectra.required(miscHelpers);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
    this.schemaDefinitionsElectra = schemaDefinitionsElectra;
  }

  /**
   * compute_exit_epoch_and_update_churn
   *
   * @param state mutable beacon state
   * @param exitBalance exit balance
   * @return earliest exit epoch (updated in state)
   */
  public UInt64 computeExitEpochAndUpdateChurn(
      final MutableBeaconStateElectra state, final UInt64 exitBalance) {
    final UInt64 earliestExitEpoch =
        miscHelpers
            .computeActivationExitEpoch(stateAccessorsElectra.getCurrentEpoch(state))
            .max(state.getEarliestExitEpoch());
    final UInt64 perEpochChurn = stateAccessorsElectra.getActivationExitChurnLimit(state);
    final UInt64 exitBalanceToConsume =
        state.getEarliestExitEpoch().isLessThan(earliestExitEpoch)
            ? perEpochChurn
            : state.getExitBalanceToConsume();

    if (exitBalance.isGreaterThan(exitBalanceToConsume)) {
      final UInt64 balanceToProcess = exitBalance.minusMinZero(exitBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.minusMinZero(1).dividedBy(perEpochChurn).increment();
      state.setEarliestExitEpoch(earliestExitEpoch.plus(additionalEpochs));
      state.setExitBalanceToConsume(
          exitBalanceToConsume
              .plus(additionalEpochs.times(perEpochChurn))
              .minusMinZero(exitBalance));
    } else {
      state.setExitBalanceToConsume(exitBalanceToConsume.minusMinZero(exitBalance));
      state.setEarliestExitEpoch(earliestExitEpoch);
    }

    return state.getEarliestExitEpoch();
  }

  /** initiate_validator_exit */
  @Override
  public void initiateValidatorExit(
      final MutableBeaconState state,
      final int index,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final UInt64 exitQueueEpoch =
        computeExitEpochAndUpdateChurn(stateElectra, validator.getEffectiveBalance());

    // Set validator exit epoch and withdrawable epoch
    stateElectra
        .getValidators()
        .set(
            index,
            validator
                .withExitEpoch(exitQueueEpoch)
                .withWithdrawableEpoch(
                    exitQueueEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  /** compute_consolidation_epoch_and_update_churn */
  public UInt64 computeConsolidationEpochAndUpdateChurn(
      final MutableBeaconState state, final UInt64 consolidationBalance) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final UInt64 computedActivationExitEpoch = miscHelpersElectra.computeActivationExitEpoch(epoch);
    final UInt64 perEpochConsolidationChurn =
        stateAccessorsElectra.getConsolidationChurnLimit(stateElectra);

    UInt64 earliestConsolidationEpoch =
        stateElectra.getEarliestConsolidationEpoch().max(computedActivationExitEpoch);
    // New epoch for consolidations.
    UInt64 consolidationBalanceToConsume =
        stateElectra.getEarliestConsolidationEpoch().isLessThan(earliestConsolidationEpoch)
            ? perEpochConsolidationChurn
            : stateElectra.getConsolidationBalanceToConsume();

    // Consolidation doesn't fit in the current earliest epoch.
    if (consolidationBalance.isGreaterThan(consolidationBalanceToConsume)) {
      final UInt64 balanceToProcess =
          consolidationBalance.minusMinZero(consolidationBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.decrement().dividedBy(perEpochConsolidationChurn).increment();
      earliestConsolidationEpoch = earliestConsolidationEpoch.plus(additionalEpochs);
      consolidationBalanceToConsume =
          consolidationBalanceToConsume.plus(additionalEpochs.times(perEpochConsolidationChurn));
    }

    // Consume the balance and update state variables.
    stateElectra.setConsolidationBalanceToConsume(
        consolidationBalanceToConsume.minusMinZero(consolidationBalance));
    stateElectra.setEarliestConsolidationEpoch(earliestConsolidationEpoch);

    return stateElectra.getEarliestConsolidationEpoch();
  }

  /**
   * switch_to_compounding_validator
   *
   * @param state beaconState
   * @param index validatorIndex
   */
  public void switchToCompoundingValidator(final MutableBeaconStateElectra state, final int index) {
    final byte[] withdrawalCredentialsUpdated =
        state.getValidators().get(index).getWithdrawalCredentials().toArray();
    withdrawalCredentialsUpdated[0] = COMPOUNDING_WITHDRAWAL_BYTE;
    state
        .getValidators()
        .update(
            index,
            validator ->
                validator.withWithdrawalCredentials(Bytes32.wrap(withdrawalCredentialsUpdated)));
    queueExcessActiveBalance(state, index);
  }

  /**
   * queue_excess_active_balance
   *
   * @param state beaconState
   * @param validatorIndex validatorIndex
   */
  public void queueExcessActiveBalance(
      final MutableBeaconStateElectra state, final int validatorIndex) {
    final UInt64 balance = state.getBalances().get(validatorIndex).get();
    final UInt64 minActivationBalance = specConfigElectra.getMinActivationBalance();

    if (balance.isGreaterThan(minActivationBalance)) {
      final UInt64 excessBalance = balance.minusMinZero(minActivationBalance);
      state.getBalances().set(validatorIndex, SszUInt64.of(minActivationBalance));

      final Validator validator = state.getValidators().get(validatorIndex);
      final PendingDeposit deposit =
          schemaDefinitionsElectra
              .getPendingDepositSchema()
              .create(
                  new SszPublicKey(validator.getPublicKey()),
                  SszBytes32.of(validator.getWithdrawalCredentials()),
                  SszUInt64.of(excessBalance),
                  new SszSignature(BLSSignature.infinity()),
                  SszUInt64.of(SpecConfig.GENESIS_SLOT));

      state.getPendingDeposits().append(deposit);
    }
  }

  /**
   * queue_entire_balance_and_reset_validator
   *
   * @param state beaconState
   * @param validatorIndex validatorIndex
   */
  public void queueEntireBalanceAndResetValidator(
      final MutableBeaconStateElectra state, final int validatorIndex) {
    final UInt64 balance = state.getBalances().getElement(validatorIndex);
    state.getBalances().set(validatorIndex, SszUInt64.ZERO);
    state
        .getValidators()
        .update(
            validatorIndex,
            validator ->
                validator
                    .withEffectiveBalance(UInt64.ZERO)
                    .withActivationEligibilityEpoch(FAR_FUTURE_EPOCH));

    final Validator validator = state.getValidators().get(validatorIndex);
    final PendingDeposit deposit =
        schemaDefinitionsElectra
            .getPendingDepositSchema()
            .create(
                new SszPublicKey(validator.getPublicKey()),
                SszBytes32.of(validator.getWithdrawalCredentials()),
                SszUInt64.of(balance),
                new SszSignature(BLSSignature.infinity()),
                SszUInt64.of(SpecConfig.GENESIS_SLOT));

    state.getPendingDeposits().append(deposit);
  }

  @Override
  protected int getWhistleblowerRewardQuotient() {
    return specConfigElectra.getWhistleblowerRewardQuotientElectra();
  }

  @Override
  protected int getMinSlashingPenaltyQuotient() {
    return specConfigElectra.getMinSlashingPenaltyQuotientElectra();
  }
}

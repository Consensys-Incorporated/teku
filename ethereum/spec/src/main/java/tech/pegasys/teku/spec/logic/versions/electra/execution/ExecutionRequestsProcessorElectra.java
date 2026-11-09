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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.FULL_EXIT_REQUEST_AMOUNT;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ExecutionRequestsProcessorElectra implements ExecutionRequestsProcessor {

  private static final Logger LOG = LogManager.getLogger();

  protected final SchemaDefinitionsElectra schemaDefinitions;
  protected final MiscHelpers miscHelpers;
  protected final SpecConfigElectra specConfig;
  private final PredicatesElectra predicates;
  protected final ValidatorsUtil validatorsUtil;
  private final BeaconStateMutatorsElectra beaconStateMutators;
  private final BeaconStateAccessorsElectra beaconStateAccessors;

  public ExecutionRequestsProcessorElectra(
      final SchemaDefinitionsElectra schemaDefinitions,
      final MiscHelpersElectra miscHelpers,
      final SpecConfigElectra specConfig,
      final PredicatesElectra predicates,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final BeaconStateAccessorsElectra beaconStateAccessors) {
    this.schemaDefinitions = schemaDefinitions;
    this.miscHelpers = miscHelpers;
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.validatorsUtil = validatorsUtil;
    this.beaconStateMutators = beaconStateMutators;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  /*
   Implements process_deposit_request from consensus-specs (EIP-6110)
  */
  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final SszMutableList<PendingDeposit> pendingDeposits = stateElectra.getPendingDeposits();
    final PendingDeposit.PendingDepositSchema pendingDepositSchema =
        schemaDefinitions.getPendingDepositSchema();
    for (DepositRequest depositRequest : depositRequests) {
      if (stateElectra
          .getDepositRequestsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX)) {
        stateElectra.setDepositRequestsStartIndex(depositRequest.getIndex());
      }
      pendingDeposits.append(
          pendingDepositSchema.create(
              new SszPublicKey(depositRequest.getPubkey()),
              SszBytes32.of(depositRequest.getWithdrawalCredentials()),
              SszUInt64.of(depositRequest.getAmount()),
              new SszSignature(depositRequest.getSignature()),
              SszUInt64.of(state.getSlot())));
    }
  }

  /** Implements process_withdrawal_request from consensus-specs (EIP-7002 &amp; EIP-7251). */
  @Override
  public void processWithdrawalRequests(
      final MutableBeaconState state,
      final List<WithdrawalRequest> withdrawalRequests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final UInt64 slot = state.getSlot();
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);

    LOG.debug(
        "process_withdrawal_request: {} withdrawal request to process from block at " + "slot {}",
        withdrawalRequests.size(),
        slot);

    withdrawalRequests.forEach(
        withdrawalRequest -> {
          LOG.debug(
              "process_withdrawal_request: processing withdrawal request {}", withdrawalRequest);

          // If partial withdrawal queue is full, only full exits are processed
          final boolean isFullExitRequest =
              withdrawalRequest.getAmount().equals(FULL_EXIT_REQUEST_AMOUNT);
          final boolean partialWithdrawalsQueueFull =
              state.toVersionElectra().orElseThrow().getPendingPartialWithdrawals().size()
                  == specConfig.getPendingPartialWithdrawalsLimit();
          if (partialWithdrawalsQueueFull && !isFullExitRequest) {
            LOG.debug("process_withdrawal_request: partial withdrawal queue is full");
            return;
          }

          final Optional<Integer> maybeValidatorIndex =
              validatorsUtil.getValidatorIndex(state, withdrawalRequest.getValidatorPubkey());
          if (maybeValidatorIndex.isEmpty()) {
            LOG.debug(
                "process_withdrawal_request: no matching validator for public key {}",
                withdrawalRequest.getValidatorPubkey().toAbbreviatedString());
            return;
          }

          final int validatorIndex = maybeValidatorIndex.get();
          final Validator validator = state.getValidators().get(validatorIndex);

          // Check if validator has an execution address set
          final boolean hasExecutionAddress =
              predicates.hasExecutionWithdrawalCredential(validator);
          if (!hasExecutionAddress) {
            LOG.debug(
                "process_withdrawal_request: validator index {} does not have withdrawal credentials set",
                validatorIndex);
            return;
          }

          // Check withdrawalRequest source_address matches validator eth1 withdrawal credentials
          final Bytes20 validatorExecutionAddress =
              new Bytes20(validator.getWithdrawalCredentials().slice(12));
          final Bytes20 withdrawalRequestSourceAddress = withdrawalRequest.getSourceAddress();
          final boolean isCorrectSourceAddress =
              validatorExecutionAddress.equals(withdrawalRequestSourceAddress);
          if (!isCorrectSourceAddress) {
            LOG.debug(
                "process_withdrawal_request: WithdrawalRequest source_address {} does not match "
                    + "validator {} withdrawal credentials {}",
                withdrawalRequestSourceAddress,
                validatorIndex,
                validatorExecutionAddress);
            return;
          }

          // Check if validator is active
          final boolean isValidatorActive = predicates.isActiveValidator(validator, currentEpoch);
          if (!isValidatorActive) {
            LOG.debug("process_withdrawal_request: Validator {} is not active", validatorIndex);
            return;
          }

          // Check if validator has already initiated exit
          final boolean hasInitiatedExit = !validator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
          if (hasInitiatedExit) {
            LOG.debug(
                "process_withdrawal_request: Validator {} has already initiated exit",
                validatorIndex);
            return;
          }

          // Check if validator has been active long enough
          final boolean validatorActiveLongEnough =
              currentEpoch.isGreaterThanOrEqualTo(
                  validator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()));
          if (!validatorActiveLongEnough) {
            LOG.debug(
                "process_withdrawal_request: Validator {} is not active long enough",
                validatorIndex);
            return;
          }

          final UInt64 pendingBalanceToWithdraw =
              validatorsUtil.getPendingBalanceToWithdraw(state, validatorIndex);
          if (isFullExitRequest) {
            // Only exit validator if it has no pending withdrawals in the queue
            if (pendingBalanceToWithdraw.isZero()) {
              LOG.debug(
                  "process_withdrawal_request: Initiating exit for validator {}", validatorIndex);

              beaconStateMutators.initiateValidatorExit(
                  state, validatorIndex, validatorExitContextSupplier);
            }
            return;
          }

          final UInt64 validatorBalance = state.getBalances().get(validatorIndex).get();
          final UInt64 minActivationBalance = specConfig.getMinActivationBalance();

          final boolean hasCompoundingWithdrawalCredential =
              predicates.hasCompoundingWithdrawalCredential(validator);
          final boolean hasSufficientEffectiveBalance =
              validator.getEffectiveBalance().isGreaterThanOrEqualTo(minActivationBalance);
          final boolean hasExcessBalance =
              validatorBalance.isGreaterThan(minActivationBalance.plus(pendingBalanceToWithdraw));
          if (hasCompoundingWithdrawalCredential
              && hasSufficientEffectiveBalance
              && hasExcessBalance) {
            final UInt64 toWithdraw =
                validatorBalance
                    .minusMinZero(minActivationBalance)
                    .minusMinZero(pendingBalanceToWithdraw)
                    .min(withdrawalRequest.getAmount());
            final MutableBeaconStateElectra electraState =
                MutableBeaconStateElectra.required(state);
            final UInt64 exitQueueEpoch =
                beaconStateMutators.computeExitEpochAndUpdateChurn(electraState, toWithdraw);
            final UInt64 withdrawableEpoch =
                exitQueueEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay());

            LOG.debug(
                "process_withdrawal_request: Creating pending partial withdrawal for validator {}",
                validatorIndex);

            electraState
                .getPendingPartialWithdrawals()
                .append(
                    schemaDefinitions
                        .getPendingPartialWithdrawalSchema()
                        .create(
                            SszUInt64.of(UInt64.fromLongBits(validatorIndex)),
                            SszUInt64.of(toWithdraw),
                            SszUInt64.of(withdrawableEpoch)));
          }
        });
  }

  /**
   * Implements process_consolidation_request from consensus-spec (EIP-7251)
   *
   * @see <a
   *     href="https://github.com/ethereum/consensus-specs/blob/master/specs/electra/beacon-chain.md#new-process_consolidation_request">process_consolidation_request</a>
   */
  @Override
  public void processConsolidationRequests(
      final MutableBeaconState state, final List<ConsolidationRequest> consolidationRequests) {
    LOG.debug(
        "process_consolidation_request: {} consolidation requests to process from block at "
            + "slot {}",
        consolidationRequests.size(),
        state.getSlot());

    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    consolidationRequests.forEach(
        consolidationRequest -> processConsolidationRequest(electraState, consolidationRequest));
  }

  private void processConsolidationRequest(
      final MutableBeaconStateElectra state, final ConsolidationRequest consolidationRequest) {
    final UInt64 slot = state.getSlot();
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);

    if (isValidSwitchToCompoundingRequest(state, consolidationRequest)) {
      LOG.debug(
          "process_consolidation_request: switching validator {} to compounding address",
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      validatorsUtil
          .getValidatorIndex(state, consolidationRequest.getSourcePubkey())
          .ifPresent(
              sourceValidatorIndex ->
                  beaconStateMutators.switchToCompoundingValidator(state, sourceValidatorIndex));
      return;
    }

    // Verify that source != target, so a consolidation cannot be used as an exit
    if (consolidationRequest.getSourcePubkey().equals(consolidationRequest.getTargetPubkey())) {
      LOG.debug(
          "process_consolidation_request: source_pubkey and target_pubkey must be different (pubkey = {})",
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      return;
    }

    // If the pending consolidations queue is full, consolidation requests are ignored
    if (state.getPendingConsolidations().size() == specConfig.getPendingConsolidationsLimit()) {
      LOG.debug("process_consolidation_request: consolidation queue is full");
      return;
    }

    // If there is too little available consolidation churn limit, consolidation requests are
    // ignored
    if (beaconStateAccessors
        .getConsolidationChurnLimit(state)
        .isLessThanOrEqualTo(specConfig.getMinActivationBalance())) {
      LOG.debug("process_consolidation_request: not enough consolidation churn limit available");
      return;
    }

    // Verify source_pubkey exists
    final Optional<Integer> maybeSourceValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getSourcePubkey());
    if (maybeSourceValidatorIndex.isEmpty()) {
      LOG.debug(
          "process_consolidation_request: source_pubkey {} not found",
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      return;
    }

    // Verify target_pubkey exists
    final Optional<Integer> maybeTargetValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getTargetPubkey());
    if (maybeTargetValidatorIndex.isEmpty()) {
      LOG.debug(
          "process_consolidation_request: target_pubkey {} not found",
          consolidationRequest.getTargetPubkey().toAbbreviatedString());
      return;
    }

    final int sourceValidatorIndex = maybeSourceValidatorIndex.get();
    final Validator sourceValidator = state.getValidators().get(sourceValidatorIndex);
    final int targetValidatorIndex = maybeTargetValidatorIndex.get();
    final Validator targetValidator = state.getValidators().get(targetValidatorIndex);

    // Verify source withdrawal credentials
    final boolean sourceHasExecutionWithdrawalCredentials =
        predicates.hasExecutionWithdrawalCredential(sourceValidator);

    final Eth1Address sourceValidatorExecutionAddress =
        Predicates.getExecutionAddressUnchecked(sourceValidator.getWithdrawalCredentials());
    final boolean sourceHasCorrectCredentials =
        sourceValidatorExecutionAddress.equals(
            Eth1Address.fromBytes(consolidationRequest.getSourceAddress().getWrappedBytes()));
    if (!(sourceHasExecutionWithdrawalCredentials && sourceHasCorrectCredentials)) {
      LOG.debug("process_consolidation_request: invalid source credentials");
      return;
    }

    // Verify that target has compounding withdrawal credentials
    if (!predicates.hasCompoundingWithdrawalCredential(targetValidator)) {
      LOG.debug("process_consolidation_request: invalid target credentials");
      return;
    }

    // Verify the source and the target are active
    if (!predicates.isActiveValidator(sourceValidator, currentEpoch)) {
      LOG.debug(
          "process_consolidation_request: source validator {} is inactive", sourceValidatorIndex);
      return;
    }
    if (!predicates.isActiveValidator(targetValidator, currentEpoch)) {
      LOG.debug(
          "process_consolidation_request: target validator {} is inactive", targetValidatorIndex);
      return;
    }

    // Verify exits for source and target have not been initiated
    if (!sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug(
          "process_consolidation_request: source validator {} is exiting", sourceValidatorIndex);
      return;
    }
    if (!targetValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug(
          "process_consolidation_request: target validator {} is exiting", targetValidatorIndex);
      return;
    }

    // Verify the source has been active long enough
    if (currentEpoch.isLessThan(
        sourceValidator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()))) {
      LOG.debug("process_consolidation_request: source has not been active long enough");
      return;
    }
    // Verify the source has no pending withdrawals in the queue
    if (beaconStateAccessors
        .getPendingBalanceToWithdraw(state, sourceValidatorIndex)
        .isGreaterThan(ZERO)) {
      LOG.debug("process_consolidation_request: source has pending withdrawals in the queue");
      return;
    }

    // Initiate source validator exit and append pending consolidation
    final UInt64 exitEpoch =
        beaconStateMutators.computeConsolidationEpochAndUpdateChurn(
            state, sourceValidator.getEffectiveBalance());
    final UInt64 withdrawableEpoch =
        exitEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay());

    state
        .getValidators()
        .update(
            sourceValidatorIndex,
            v -> v.withExitEpoch(exitEpoch).withWithdrawableEpoch(withdrawableEpoch));
    LOG.debug(
        "process_consolidation_request: updated validator {} with exit_epoch = {}, withdrawable_epoch = {}",
        sourceValidatorIndex,
        exitEpoch,
        withdrawableEpoch);

    final PendingConsolidation pendingConsolidation =
        new PendingConsolidation(
            schemaDefinitions.getPendingConsolidationSchema(),
            SszUInt64.of(UInt64.valueOf(sourceValidatorIndex)),
            SszUInt64.of(UInt64.valueOf(targetValidatorIndex)));
    state.getPendingConsolidations().append(pendingConsolidation);

    LOG.debug("process_consolidation_request: created {}", pendingConsolidation);
  }

  /**
   * Implements function is_valid_switch_to_compounding_request
   *
   * @see <a
   *     href="https://github.com/ethereum/consensus-specs/blob/master/specs/electra/beacon-chain.md#new-is_valid_switch_to_compounding_request"/>
   */
  private boolean isValidSwitchToCompoundingRequest(
      final BeaconState state, final ConsolidationRequest consolidationRequest) {

    // Switch to compounding requires source and target be equal
    if (!consolidationRequest.getSourcePubkey().equals(consolidationRequest.getTargetPubkey())) {
      return false;
    }

    // Verify source_pubkey exists
    final Optional<Integer> maybeSourceValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getSourcePubkey());
    if (maybeSourceValidatorIndex.isEmpty()) {
      return false;
    }

    final int sourceValidatorIndex = maybeSourceValidatorIndex.get();
    final Validator sourceValidator = state.getValidators().get(sourceValidatorIndex);

    // Verify request has been authorized
    final Eth1Address sourceValidatorExecutionAddress =
        Predicates.getExecutionAddressUnchecked(sourceValidator.getWithdrawalCredentials());
    if (!sourceValidatorExecutionAddress.equals(
        Eth1Address.fromBytes(consolidationRequest.getSourceAddress().getWrappedBytes()))) {
      return false;
    }

    // Verify source withdrawal credentials
    if (!predicates.hasEth1WithdrawalCredential(sourceValidator)) {
      return false;
    }

    // Verify the source is active
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    if (!predicates.isActiveValidator(sourceValidator, currentEpoch)) {
      return false;
    }

    // Verify exit for source has not been initiated
    return sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
  }

  @Override
  public void processBuilderDepositRequests(
      final MutableBeaconState state, final List<BuilderDepositRequest> builderDepositRequests) {
    // NO-OP (until Gloas)
  }

  @Override
  public void processBuilderExitRequests(
      final MutableBeaconState state, final List<BuilderExitRequest> builderExitRequests) {
    // NO-OP (until Gloas)
  }
}

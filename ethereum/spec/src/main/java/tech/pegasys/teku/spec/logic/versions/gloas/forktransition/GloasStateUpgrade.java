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

package tech.pegasys.teku.spec.logic.versions.gloas.forktransition;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.spec.logic.common.helpers.Predicates.getExecutionAddressUnchecked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateListFieldMigration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GloasStateUpgrade implements StateUpgrade<BeaconStateFulu> {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigGloas specConfig;
  private final SchemaDefinitionsGloas schemaDefinitions;
  private final BeaconStateAccessorsGloas beaconStateAccessors;
  private final PredicatesGloas predicates;
  private final BeaconStateMutatorsGloas beaconStateMutators;
  private final MiscHelpersGloas miscHelpers;
  private final ValidatorsUtil validatorsUtil;

  public GloasStateUpgrade(
      final SpecConfigGloas specConfig,
      final SchemaDefinitionsGloas schemaDefinitions,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final PredicatesGloas predicates,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final MiscHelpersGloas miscHelpers,
      final ValidatorsUtil validatorsUtil) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
    this.miscHelpers = miscHelpers;
    this.validatorsUtil = validatorsUtil;
  }

  @Override
  public BeaconStateGloas upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateFulu preStateFulu = BeaconStateFulu.required(preState);

    final BeaconStateSchemaGloas targetStateSchema =
        BeaconStateSchemaGloas.required(schemaDefinitions.getBeaconStateSchema());

    return targetStateSchema
        .createEmptyWithTransitionCachesFrom(preState)
        .updatedGloas(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSourceUsingTargetSchemas(state, preState);

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getGloasForkVersion(),
                      epoch));

              // These source lists are bounded in Fulu but progressive in Gloas, so rebuild them
              // through the target (progressive) field schemas.
              state.setPreviousEpochParticipation(
                  BeaconStateListFieldMigration.rematerialize(
                      targetStateSchema,
                      BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION,
                      preStateFulu.getPreviousEpochParticipation()));
              state.setCurrentEpochParticipation(
                  BeaconStateListFieldMigration.rematerialize(
                      targetStateSchema,
                      BeaconStateFields.CURRENT_EPOCH_PARTICIPATION,
                      preStateFulu.getCurrentEpochParticipation()));
              state.setInactivityScores(
                  BeaconStateListFieldMigration.rematerializeUInt64(
                      targetStateSchema,
                      BeaconStateFields.INACTIVITY_SCORES,
                      preStateFulu.getInactivityScores()));
              state.setCurrentSyncCommittee(preStateFulu.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateFulu.getNextSyncCommittee());

              // New in Gloas
              final ExecutionPayloadHeader latestExecutionPayloadHeader =
                  preStateFulu.getLatestExecutionPayloadHeaderRequired();
              final Bytes32 latestBlockHash = latestExecutionPayloadHeader.getBlockHash();
              state.setLatestBlockHash(latestBlockHash);

              state.setNextWithdrawalIndex(preStateFulu.getNextWithdrawalIndex());
              state.setNextWithdrawalValidatorIndex(preStateFulu.getNextWithdrawalValidatorIndex());
              state.setHistoricalSummaries(preStateFulu.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateFulu.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(preStateFulu.getDepositBalanceToConsume());
              state.setExitBalanceToConsume(preStateFulu.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateFulu.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateFulu.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateFulu.getEarliestConsolidationEpoch());
              state.setPendingDeposits(
                  BeaconStateListFieldMigration.rematerialize(
                      targetStateSchema,
                      BeaconStateFields.PENDING_DEPOSITS,
                      preStateFulu.getPendingDeposits()));
              state.setPendingPartialWithdrawals(
                  BeaconStateListFieldMigration.rematerialize(
                      targetStateSchema,
                      BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS,
                      preStateFulu.getPendingPartialWithdrawals()));
              state.setPendingConsolidations(
                  BeaconStateListFieldMigration.rematerialize(
                      targetStateSchema,
                      BeaconStateFields.PENDING_CONSOLIDATIONS,
                      preStateFulu.getPendingConsolidations()));
              state.setProposerLookahead(preStateFulu.getProposerLookahead());

              // New in Gloas
              state.setBuilders(
                  BeaconStateSchemaGloas.required(state.getBeaconStateSchema())
                      .getBuildersSchema()
                      .of());
              state.setNextWithdrawalBuilderIndex(UInt64.ZERO);
              final SszBitvector executionPayloadAvailability =
                  schemaDefinitions
                      .getExecutionPayloadAvailabilitySchema()
                      .ofBits(IntStream.range(0, specConfig.getSlotsPerHistoricalRoot()).toArray());
              state.setExecutionPayloadAvailability(executionPayloadAvailability);
              final List<BuilderPendingPayment> builderPendingPayments =
                  Collections.nCopies(
                      2 * specConfig.getSlotsPerEpoch(),
                      schemaDefinitions.getBuilderPendingPaymentSchema().getDefault());
              state.setBuilderPendingPayments(
                  schemaDefinitions
                      .getBuilderPendingPaymentsSchema()
                      .createFromElements(builderPendingPayments));
              state.setBuilderPendingWithdrawals(
                  schemaDefinitions.getBuilderPendingWithdrawalsSchema().of());
              state.setLatestExecutionPayloadBid(
                  schemaDefinitions
                      .getExecutionPayloadBidSchema()
                      .create(
                          latestExecutionPayloadHeader.getParentHash(),
                          preState.getLatestBlockHeader().getParentRoot(),
                          latestBlockHash,
                          latestExecutionPayloadHeader.getPrevRandao(),
                          Bytes20.ZERO,
                          latestExecutionPayloadHeader.getGasLimit(),
                          SpecConfigGloas.BUILDER_INDEX_SELF_BUILD,
                          preState.getLatestBlockHeader().getSlot(),
                          UInt64.ZERO,
                          UInt64.ZERO,
                          schemaDefinitions.getBlobKzgCommitmentsSchema().of(),
                          schemaDefinitions
                              .getExecutionRequestsSchema()
                              .getDefault()
                              .hashTreeRoot()));
              state.setPayloadExpectedWithdrawals(
                  schemaDefinitions
                      .getExecutionPayloadSchema()
                      .getWithdrawalsSchemaRequired()
                      .of());
              state.setPtcWindow(beaconStateAccessors.initializePtcWindow(preState));

              onboardBuildersFromPendingDeposits(state);
            });
  }

  /**
   * This one-time onboarding is the only path through the validator deposit contract that creates
   * builders. From the fork onward, builders are created and topped up only via
   * `BuilderDepositRequest`.
   */
  private void onboardBuildersFromPendingDeposits(final MutableBeaconStateGloas state) {
    final long startTimeMillis = System.currentTimeMillis();
    LOG.debug(
        "Starting onboarding builders at fork from {} pending deposits",
        state.getPendingDeposits().size());
    final List<PendingDeposit> pendingDeposits = new ArrayList<>();
    final PendingValidatorTracker pendingValidators = new PendingValidatorTracker();

    for (final PendingDeposit deposit : state.getPendingDeposits()) {
      final BLSPublicKey pubkey = deposit.getPublicKey();
      // Deposits for existing validators stay in the pending queue
      if (validatorsUtil.getValidatorIndex(state, pubkey).isPresent()) {
        pendingDeposits.add(deposit);
        continue;
      }
      beaconStateAccessors
          .getBuilderIndex(state, pubkey)
          .ifPresentOrElse(
              builderIndex -> {
                final SszMutableList<Builder> builders = state.getBuilders();
                final Builder builder = builders.get(builderIndex);
                builders.set(
                    builderIndex,
                    builder.copyWithNewBalance(builder.getBalance().plus(deposit.getAmount())));
              },
              // Deposits for non-builders stay in the pending queue. If there is a valid pending
              // deposit for a new validator with this pubkey, keep this deposit in the pending
              // queue to be applied to that validator later.
              () -> {
                // Deposits without builder credentials stay in the pending queue. Such a deposit is
                // the only kind that can newly make its pubkey a pending validator, so record it
                // for a signature check that only runs if a builder deposit later needs the answer.
                if (!predicates.isBuilderWithdrawalCredential(deposit.getWithdrawalCredentials())) {
                  pendingDeposits.add(deposit);
                  pendingValidators.addPendingDeposit(deposit);
                  return;
                }
                if (pendingValidators.isPendingValidator(pubkey)) {
                  pendingDeposits.add(deposit);
                  return;
                }
                if (!miscHelpers.isValidDepositSignature(
                    pubkey,
                    deposit.getWithdrawalCredentials(),
                    deposit.getAmount(),
                    deposit.getSignature())) {
                  return;
                }
                beaconStateMutators.addBuilderToRegistry(
                    state,
                    pubkey,
                    PAYLOAD_BUILDER_VERSION,
                    getExecutionAddressUnchecked(deposit.getWithdrawalCredentials()),
                    deposit.getAmount(),
                    deposit.getSlot());
              });
    }

    state.setPendingDeposits(
        schemaDefinitions.getPendingDepositsSchema().createFromElements(pendingDeposits));
    LOG.debug(
        "Finished onboarding builders at fork. Pending deposits remaining: {}, builders: {}. Took {} ms.",
        pendingDeposits.size(),
        state.getBuilders().size(),
        System.currentTimeMillis() - startTimeMillis);
  }

  /**
   * Answers is_pending_validator for the validator deposits queued so far during builder
   * onboarding. Deposit signatures are verified lazily, only when a builder deposit asks about the
   * pubkey, and each deposit is verified at most once. The check is existential, so once one valid
   * deposit is found the pubkey stays pending without verifying its remaining deposits.
   */
  private class PendingValidatorTracker {
    private final Map<BLSPublicKey, List<PendingDeposit>> unverifiedDeposits = new HashMap<>();
    private final Set<BLSPublicKey> pendingValidatorPubkeys = new HashSet<>();

    void addPendingDeposit(final PendingDeposit deposit) {
      final BLSPublicKey pubkey = deposit.getPublicKey();
      if (!pendingValidatorPubkeys.contains(pubkey)) {
        unverifiedDeposits.computeIfAbsent(pubkey, __ -> new ArrayList<>()).add(deposit);
      }
    }

    boolean isPendingValidator(final BLSPublicKey pubkey) {
      if (pendingValidatorPubkeys.contains(pubkey)) {
        return true;
      }
      final List<PendingDeposit> deposits = unverifiedDeposits.remove(pubkey);
      if (deposits == null) {
        return false;
      }
      for (final PendingDeposit deposit : deposits) {
        if (miscHelpers.isValidDepositSignature(
            pubkey,
            deposit.getWithdrawalCredentials(),
            deposit.getAmount(),
            deposit.getSignature())) {
          pendingValidatorPubkeys.add(pubkey);
          return true;
        }
      }
      return false;
    }
  }
}

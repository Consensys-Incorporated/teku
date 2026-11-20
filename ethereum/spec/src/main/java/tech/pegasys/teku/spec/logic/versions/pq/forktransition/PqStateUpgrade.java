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

package tech.pegasys.teku.spec.logic.versions.pq.forktransition;

import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStateSchemaPq;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.statetransition.epoch.EpochProcessorPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

public class PqStateUpgrade implements StateUpgrade<BeaconStatePq> {

  private final SpecConfigPq specConfig;
  private final SchemaDefinitionsPq schemaDefinitions;
  private final BeaconStateAccessorsPq beaconStateAccessors;
  private final MiscHelpersPq miscHelpers;

  public PqStateUpgrade(
      final SpecConfigPq specConfig,
      final SchemaDefinitionsPq schemaDefinitions,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final MiscHelpersPq miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  /**
   * upgrade_to_pq: the upgrade into the PQ state. Registered validators and builders take their
   * XMSS public key (and VRF root), the others a legacy key derived from their BLS public key, and
   * are exited (validators) or made withdrawable (builders) at the fork.
   *
   * <p>The post-state is built empty, so its caches keyed by BLS public keys start empty too.
   */
  @Override
  public BeaconStatePq upgrade(final BeaconState preState) {
    final BeaconStateGloas pre = BeaconStateGloas.required(preState);
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(pre);
    // The pre-state is a Gloas state when EIP-8483 is scheduled at the same epoch as PQ, and then
    // has no registrations
    final List<ValidatorPqRegistration> validatorRegistrations = getValidatorPqRegistrations(pre);
    final List<BuilderPqRegistration> builderRegistrations = getBuilderPqRegistrations(pre);
    final BeaconStateSchemaPq stateSchema = schemaDefinitions.getPqBeaconStateSchema();
    final List<Validator> validators = computeValidators(pre, epoch, validatorRegistrations);
    final Set<Bytes48> registeredBuilders =
        builderRegistrations.stream()
            .map(registration -> registration.getBuilderPubkey().toBytesCompressed())
            .collect(Collectors.toSet());

    final BeaconStatePq post =
        BeaconStatePq.required(stateSchema.createEmpty())
            .updatedPq(
                state -> {
                  state.setGenesisTime(pre.getGenesisTime());
                  state.setGenesisValidatorsRoot(pre.getGenesisValidatorsRoot());
                  state.setSlot(pre.getSlot());
                  state.setFork(
                      new Fork(
                          pre.getFork().getCurrentVersion(), specConfig.getPqForkVersion(), epoch));
                  state.setLatestBlockHeader(pre.getLatestBlockHeader());
                  state.setBlockRoots(pre.getBlockRoots());
                  state.setStateRoots(pre.getStateRoots());
                  state.setHistoricalRoots(pre.getHistoricalRoots());
                  state.setEth1Data(pre.getEth1Data());
                  state.setEth1DataVotes(pre.getEth1DataVotes());
                  state.setEth1DepositIndex(pre.getEth1DepositIndex());
                  state.setValidators(
                      stateSchema.getValidatorsSchema().createFromElements(validators));
                  state.setBalances(pre.getBalances());
                  state.setRandaoMixes(pre.getRandaoMixes());
                  state.setSlashings(pre.getSlashings());
                  state.setPreviousEpochParticipation(pre.getPreviousEpochParticipation());
                  state.setCurrentEpochParticipation(pre.getCurrentEpochParticipation());
                  state.setJustificationBits(pre.getJustificationBits());
                  state.setPreviousJustifiedCheckpoint(pre.getPreviousJustifiedCheckpoint());
                  state.setCurrentJustifiedCheckpoint(pre.getCurrentJustifiedCheckpoint());
                  state.setFinalizedCheckpoint(pre.getFinalizedCheckpoint());
                  state.setInactivityScores(pre.getInactivityScores());
                  state.setLatestBlockHash(pre.getLatestBlockHash());
                  state.setNextWithdrawalIndex(pre.getNextWithdrawalIndex());
                  state.setNextWithdrawalValidatorIndex(pre.getNextWithdrawalValidatorIndex());
                  state.setHistoricalSummaries(pre.getHistoricalSummaries());
                  state.setDepositRequestsStartIndex(pre.getDepositRequestsStartIndex());
                  state.setDepositBalanceToConsume(pre.getDepositBalanceToConsume());
                  state.setExitBalanceToConsume(pre.getExitBalanceToConsume());
                  state.setEarliestExitEpoch(pre.getEarliestExitEpoch());
                  state.setConsolidationBalanceToConsume(pre.getConsolidationBalanceToConsume());
                  state.setEarliestConsolidationEpoch(pre.getEarliestConsolidationEpoch());
                  state.setPendingDeposits(
                      stateSchema
                          .getPendingDepositsSchema()
                          .createFromElements(computePendingDeposits(pre, validators)));
                  state.setPendingPartialWithdrawals(pre.getPendingPartialWithdrawals());
                  state.setPendingConsolidations(pre.getPendingConsolidations());
                  state.setProposerLookahead(pre.getProposerLookahead());
                  state.setBuilders(
                      stateSchema
                          .getBuildersSchema()
                          .createFromElements(computeBuilders(pre, epoch, builderRegistrations)));
                  state.setNextWithdrawalBuilderIndex(pre.getNextWithdrawalBuilderIndex());
                  state.setExecutionPayloadAvailability(pre.getExecutionPayloadAvailability());
                  state.setBuilderPendingPayments(
                      stateSchema
                          .getBuilderPendingPaymentsSchema()
                          .createFromElements(
                              computeBuilderPendingPayments(pre, registeredBuilders)));
                  state.setBuilderPendingWithdrawals(
                      stateSchema
                          .getBuilderPendingWithdrawalsSchema()
                          .createFromElements(
                              computeBuilderPendingWithdrawals(pre, registeredBuilders)));
                  state.setLatestExecutionPayloadBid(pre.getLatestExecutionPayloadBid());
                  state.setPayloadExpectedWithdrawals(pre.getPayloadExpectedWithdrawals());
                  state.setPtcWindow(pre.getPtcWindow());
                });

    return post.updatedPq(
        state -> state.setAggregationCommitteeWindow(initializeAggregationCommitteeWindow(post)));
  }

  // get_validator_pq_registrations: registrations of a legacy key are ignored
  private List<ValidatorPqRegistration> getValidatorPqRegistrations(final BeaconState pre) {
    return pre.toVersionEip8483()
        .map(
            preEip8483 -> {
              final Set<Bytes32> legacyKeys =
                  pre.getValidators().stream()
                      .map(
                          validator ->
                              miscHelpers.computeLegacyXmssPubkey(validator.getPubkeyBytes()))
                      .collect(Collectors.toSet());
              return preEip8483.getValidatorPqRegistrations().stream()
                  .filter(
                      registration ->
                          !legacyKeys.contains(Bytes32.wrap(registration.getPqData().slice(0, 32))))
                  .toList();
            })
        .orElse(List.of());
  }

  // get_builder_pq_registrations: registrations of a legacy key are ignored
  private List<BuilderPqRegistration> getBuilderPqRegistrations(final BeaconState pre) {
    return pre.toVersionEip8483()
        .map(
            preEip8483 -> {
              final Set<Bytes32> legacyKeys =
                  BeaconStateGloas.required(pre).getBuilders().stream()
                      .map(
                          builder ->
                              miscHelpers.computeLegacyXmssPubkey(
                                  builder.getPublicKey().toBytesCompressed()))
                      .collect(Collectors.toSet());
              return preEip8483.getBuilderPqRegistrations().stream()
                  .filter(registration -> !legacyKeys.contains(registration.getPqData()))
                  .toList();
            })
        .orElse(List.of());
  }

  // compute_validators
  private List<Validator> computeValidators(
      final BeaconState pre,
      final UInt64 epoch,
      final List<ValidatorPqRegistration> registrations) {
    final Map<Integer, Bytes> registered = new HashMap<>();
    // As in the spec's dict, a later registration of the same index replaces an earlier one
    registrations.forEach(
        registration ->
            registered.put(registration.getValidatorIndex().intValue(), registration.getPqData()));
    final UInt64 unregisteredWithdrawableEpoch =
        epoch.plus(specConfig.getUnregisteredValidatorWithdrawabilityDelay());
    final List<Validator> validators = new ArrayList<>(pre.getValidators().size());
    for (int index = 0; index < pre.getValidators().size(); index++) {
      final Validator validator = pre.getValidators().get(index);
      final Bytes pqData = registered.get(index);
      final Bytes32 pubkey;
      final Bytes32 vrfRoot;
      final UInt64 exitEpoch;
      final UInt64 withdrawableEpoch;
      if (pqData != null) {
        pubkey = Bytes32.wrap(pqData.slice(0, 32));
        vrfRoot = Bytes32.wrap(pqData.slice(32, 32));
        exitEpoch = validator.getExitEpoch();
        withdrawableEpoch = validator.getWithdrawableEpoch();
      } else {
        pubkey = miscHelpers.computeLegacyXmssPubkey(validator.getPubkeyBytes());
        vrfRoot = miscHelpers.computeLegacyVrfRoot(validator.getPubkeyBytes());
        exitEpoch = validator.getExitEpoch().min(epoch);
        withdrawableEpoch =
            validator.isSlashed()
                ? validator.getWithdrawableEpoch()
                : validator.getWithdrawableEpoch().min(unregisteredWithdrawableEpoch);
      }
      validators.add(
          new ValidatorPq(
              pubkey,
              vrfRoot,
              validator.getWithdrawalCredentials(),
              validator.getEffectiveBalance(),
              validator.isSlashed(),
              validator.getActivationEligibilityEpoch(),
              validator.getActivationEpoch(),
              exitEpoch,
              withdrawableEpoch));
    }
    return validators;
  }

  // compute_builders
  private List<Builder> computeBuilders(
      final BeaconStateGloas pre,
      final UInt64 epoch,
      final List<BuilderPqRegistration> registrations) {
    final Map<Bytes48, Bytes32> registered = new HashMap<>();
    registrations.forEach(
        registration ->
            registered.put(
                registration.getBuilderPubkey().toBytesCompressed(), registration.getPqData()));
    return pre.getBuilders().stream()
        .map(
            builder -> {
              final Bytes48 blsKey = builder.getPublicKey().toBytesCompressed();
              final Bytes32 registeredKey = registered.get(blsKey);
              final Bytes32 pubkey =
                  registeredKey != null
                      ? registeredKey
                      : miscHelpers.computeLegacyXmssPubkey(blsKey);
              final UInt64 withdrawableEpoch =
                  registeredKey != null
                      ? builder.getWithdrawableEpoch()
                      : builder.getWithdrawableEpoch().min(epoch);
              return (Builder)
                  new BuilderPq(
                      pubkey,
                      builder.getVersion(),
                      builder.getExecutionAddress(),
                      builder.getBalance(),
                      builder.getDepositEpoch(),
                      withdrawableEpoch);
            })
        .toList();
  }

  // compute_pending_deposits: a deposit for an existing validator is renamed to its XMSS key
  private List<PendingDeposit> computePendingDeposits(
      final BeaconStateGloas pre, final List<Validator> validators) {
    final Map<Bytes48, Integer> validatorIndices = new HashMap<>();
    for (int index = 0; index < pre.getValidators().size(); index++) {
      validatorIndices.putIfAbsent(pre.getValidators().get(index).getPubkeyBytes(), index);
    }
    final SchemaDefinitionsPq schemas = schemaDefinitions;
    return pre.getPendingDeposits().stream()
        .map(
            deposit -> {
              final Integer index = validatorIndices.get(deposit.getPubkeyBytes());
              if (index == null) {
                return deposit;
              }
              final Bytes32 xmssKey = ((ValidatorPq) validators.get(index)).getXmssPublicKey();
              return schemas
                  .getPendingDepositSchema()
                  .create(
                      new SszPublicKey(
                          Bytes48.wrap(Bytes.concatenate(XMSS_DEPOSIT_PREFIX, xmssKey))),
                      SszBytes32.of(deposit.getWithdrawalCredentials()),
                      SszUInt64.of(deposit.getAmount()),
                      new SszSignature(deposit.getSignatureBytes()),
                      SszUInt64.of(deposit.getSlot()));
            })
        .toList();
  }

  // compute_builder_pending_withdrawals: unregistered builders' payments that reached the quorum
  // are settled
  private List<BuilderPendingWithdrawal> computeBuilderPendingWithdrawals(
      final BeaconStateGloas pre, final Set<Bytes48> registeredBuilders) {
    final UInt64 quorum = beaconStateAccessors.getBuilderPaymentQuorumThreshold(pre);
    final List<BuilderPendingWithdrawal> withdrawals =
        new ArrayList<>(pre.getBuilderPendingWithdrawals().asList());
    for (final BuilderPendingPayment payment : pre.getBuilderPendingPayments()) {
      if (isUnregisteredBuilderPayment(pre, payment, registeredBuilders)
          && payment.getWeight().isGreaterThanOrEqualTo(quorum)) {
        withdrawals.add(payment.getWithdrawal());
      }
    }
    return withdrawals;
  }

  // compute_builder_pending_payments: unregistered builders' payments are cleared
  private List<BuilderPendingPayment> computeBuilderPendingPayments(
      final BeaconStateGloas pre, final Set<Bytes48> registeredBuilders) {
    final BuilderPendingPayment empty =
        schemaDefinitions.getBuilderPendingPaymentSchema().getDefault();
    return pre.getBuilderPendingPayments().stream()
        .map(
            payment ->
                isUnregisteredBuilderPayment(pre, payment, registeredBuilders) ? empty : payment)
        .toList();
  }

  private boolean isUnregisteredBuilderPayment(
      final BeaconStateGloas pre,
      final BuilderPendingPayment payment,
      final Set<Bytes48> registeredBuilders) {
    if (payment.getWithdrawal().getAmount().isZero()) {
      return false;
    }
    final Builder builder =
        pre.getBuilders().get(payment.getWithdrawal().getBuilderIndex().intValue());
    return !registeredBuilders.contains(builder.getPublicKey().toBytesCompressed());
  }

  // initialize_aggregation_committee_window
  private SszVector<SszList<SszList<SszUInt64>>> initializeAggregationCommitteeWindow(
      final BeaconStatePq state) {
    final SszVectorSchema<SszList<SszList<SszUInt64>>, ?> windowSchema =
        schemaDefinitions.getPqBeaconStateSchema().getAggregationCommitteeWindowSchema();
    final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
    final List<SszList<SszList<SszUInt64>>> window = new ArrayList<>();
    for (int i = 0; i < slotsPerEpoch; i++) {
      window.add(EpochProcessorPq.toAggregationCommittees(windowSchema, List.of()));
    }
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 endSlot =
        miscHelpers.computeStartSlotAtEpoch(
            currentEpoch.plus(specConfig.getMinSeedLookahead()).plus(1));
    for (UInt64 slot = startSlot; slot.isLessThan(endSlot); slot = slot.increment()) {
      window.add(
          EpochProcessorPq.toAggregationCommittees(
              windowSchema, beaconStateAccessors.computeAggregationCommittees(state, slot)));
    }
    return windowSchema.createFromElements(window);
  }
}

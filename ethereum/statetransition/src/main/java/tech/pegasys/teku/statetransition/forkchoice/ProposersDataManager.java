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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;

public class ProposersDataManager implements SlotEventsChannel, ValidatorIsConnectedProvider {
  private static final Logger LOG = LogManager.getLogger();
  private static final long PROPOSER_PREPARATION_EXPIRATION_EPOCHS = 3;
  private static final long VALIDATOR_REGISTRATION_EXPIRATION_EPOCHS = 2;

  private final Spec spec;
  private final EventThread eventThread;
  private final ExecutionLayerChannel executionLayerChannel;
  private final RecentChainData recentChainData;
  private final Map<UInt64, PreparedProposerInfo> preparedProposerInfoByValidatorIndex =
      new ConcurrentHashMap<>();
  private final Map<UInt64, RegisteredValidatorInfo> validatorRegistrationInfoByValidatorIndex =
      new ConcurrentHashMap<>();
  private final Optional<Eth1Address> proposerDefaultFeeRecipient;
  private final boolean forkChoiceUpdatedAlwaysSendPayloadAttribute;
  private final ProposerPreferencesManager proposerPreferencesManager;

  public ProposersDataManager(
      final EventThread eventThread,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final Optional<Eth1Address> proposerDefaultFeeRecipient,
      final boolean forkChoiceUpdatedAlwaysSendPayloadAttribute) {
    this(
        eventThread,
        spec,
        metricsSystem,
        executionLayerChannel,
        recentChainData,
        proposerDefaultFeeRecipient,
        forkChoiceUpdatedAlwaysSendPayloadAttribute,
        ProposerPreferencesManager.NOOP);
  }

  public ProposersDataManager(
      final EventThread eventThread,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final Optional<Eth1Address> proposerDefaultFeeRecipient,
      final boolean forkChoiceUpdatedAlwaysSendPayloadAttribute,
      final ProposerPreferencesManager proposerPreferencesManager) {
    final LabelledSuppliedMetric labelledGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.BEACON,
            "proposers_data",
            "Total number proposers/validators under management",
            "type");

    labelledGauge.labels(preparedProposerInfoByValidatorIndex::size, "prepared_proposers");
    labelledGauge.labels(validatorRegistrationInfoByValidatorIndex::size, "registered_validators");

    this.spec = spec;
    this.eventThread = eventThread;
    this.executionLayerChannel = executionLayerChannel;
    this.recentChainData = recentChainData;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
    this.forkChoiceUpdatedAlwaysSendPayloadAttribute = forkChoiceUpdatedAlwaysSendPayloadAttribute;
    this.proposerPreferencesManager = proposerPreferencesManager;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // do clean up in the middle of the epoch
    final int slotsPerEpoch = spec.getSlotsPerEpoch(slot);
    final UInt64 slotInCurrentEpoch = slot.mod(slotsPerEpoch).plus(UInt64.ONE);
    if (!slotInCurrentEpoch.equals(UInt64.valueOf(slotsPerEpoch).dividedBy(2))) {
      return;
    }

    // Remove expired prepared proposers
    final int preparedProposersBeforeRemoval = preparedProposerInfoByValidatorIndex.size();
    final boolean hasRemovedExpiredProposers =
        preparedProposerInfoByValidatorIndex.values().removeIf(info -> info.hasExpired(slot));
    if (hasRemovedExpiredProposers) {
      final int preparedProposersAfterRemoval =
          preparedProposersBeforeRemoval - preparedProposerInfoByValidatorIndex.size();
      VALIDATOR_LOGGER.preparedBeaconProposersExpiration(slot, preparedProposersAfterRemoval);
    }

    // Remove expired registered validators
    final int registrationsBeforeRemoval = validatorRegistrationInfoByValidatorIndex.size();
    final boolean hasRemovedExpiredRegistrations =
        validatorRegistrationInfoByValidatorIndex.values().removeIf(info -> info.hasExpired(slot));
    if (hasRemovedExpiredRegistrations) {
      final int registrationsAfterRemoval =
          registrationsBeforeRemoval - validatorRegistrationInfoByValidatorIndex.size();
      VALIDATOR_LOGGER.validatorRegistrationsExpiration(slot, registrationsAfterRemoval);
    }
  }

  // pre-Gloas
  public void updatePreparedProposersFromPrepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers,
      final UInt64 currentSlot) {
    final Stream<ValidatorIndexAndFeeRecipient> preparedProposers =
        beaconPreparableProposers.stream()
            .map(
                proposer ->
                    new ValidatorIndexAndFeeRecipient(
                        proposer.validatorIndex(), proposer.feeRecipient()));
    updatePreparedProposerCache(preparedProposers, currentSlot);
  }

  // post-Gloas
  public void updatePreparedProposersFromProposerPreferences(
      final Collection<SignedProposerPreferences> proposerPreferences, final UInt64 currentSlot) {
    final Stream<ValidatorIndexAndFeeRecipient> preparedProposers =
        proposerPreferences.stream()
            .map(
                proposerPreference ->
                    new ValidatorIndexAndFeeRecipient(
                        proposerPreference.getMessage().getValidatorIndex(),
                        proposerPreference.getMessage().getFeeRecipient()));
    updatePreparedProposerCache(preparedProposers, currentSlot);
  }

  private record ValidatorIndexAndFeeRecipient(UInt64 validatorIndex, Eth1Address feeRecipient) {}

  public SafeFuture<Void> updateValidatorRegistrations(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
      final UInt64 currentSlot) {
    return executionLayerChannel
        .builderRegisterValidators(signedValidatorRegistrations, currentSlot)
        .thenCompose(__ -> recentChainData.getBestState().orElseThrow())
        .thenAccept(
            headState ->
                updateValidatorRegistrationCache(
                    headState, signedValidatorRegistrations, currentSlot));
  }

  @Override
  public boolean isValidatorConnected(final int validatorIndex, final UInt64 currentSlot) {
    final PreparedProposerInfo info =
        preparedProposerInfoByValidatorIndex.get(UInt64.valueOf(validatorIndex));
    return info != null && !info.hasExpired(currentSlot);
  }

  @Override
  public SafeFuture<Boolean> isBlockProposerConnected(final UInt64 blockSlot) {
    final UInt64 epoch = spec.computeEpochAtSlot(blockSlot);
    return getStateInEpoch(epoch)
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return false;
              }
              return isValidatorConnected(
                  spec.getBeaconProposerIndex(maybeState.get(), blockSlot), blockSlot);
            });
  }

  private void updatePreparedProposerCache(
      final Stream<ValidatorIndexAndFeeRecipient> preparedProposers, final UInt64 currentSlot) {
    final UInt64 expirySlot =
        currentSlot.plus(
            spec.getSlotsPerEpoch(currentSlot) * PROPOSER_PREPARATION_EXPIRATION_EPOCHS);
    preparedProposers.forEach(
        proposer ->
            preparedProposerInfoByValidatorIndex.put(
                proposer.validatorIndex,
                new PreparedProposerInfo(expirySlot, proposer.feeRecipient)));
  }

  private void updateValidatorRegistrationCache(
      final BeaconState headState,
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
      final UInt64 currentSlot) {
    final UInt64 expirySlot =
        currentSlot.plus(
            spec.getSlotsPerEpoch(currentSlot) * VALIDATOR_REGISTRATION_EXPIRATION_EPOCHS);

    signedValidatorRegistrations.forEach(
        signedValidatorRegistration ->
            spec.getValidatorIndex(
                    headState, signedValidatorRegistration.getMessage().getPublicKey())
                .ifPresentOrElse(
                    index ->
                        validatorRegistrationInfoByValidatorIndex.put(
                            UInt64.valueOf(index),
                            new RegisteredValidatorInfo(expirySlot, signedValidatorRegistration)),
                    () ->
                        LOG.warn(
                            "validator index not found for public key {}",
                            signedValidatorRegistration.getMessage().getPublicKey())));
  }

  public SafeFuture<Optional<PayloadBuildingAttributes>> calculatePayloadBuildingAttributes(
      final UInt64 blockSlot,
      final boolean inSync,
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean mandatory) {
    eventThread.checkOnEventThread();
    if (!inSync) {
      // We don't produce blocks while syncing so don't bother preparing the payload
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (!forkChoiceUpdateData.hasHeadBlockHash()) {
      // No forkChoiceUpdated message will be sent so no point calculating payload attributes
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (!recentChainData.isJustifiedCheckpointFullyValidated()) {
      // If we've optimistically synced far enough that our justified checkpoint is optimistic,
      // stop producing blocks because the majority of validators see the optimistic chain as valid.
      return SafeFuture.completedFuture(Optional.empty());
    }
    final UInt64 epoch = spec.computeEpochAtSlot(blockSlot);
    final ForkChoiceState forkChoiceState = forkChoiceUpdateData.getForkChoiceState();
    final ForkChoiceNode currentHeadBlock = forkChoiceState.headBlock();
    return getStateForPayloadBuildingAttributes(blockSlot, forkChoiceState)
        .thenComposeAsync(
            maybeState ->
                calculatePayloadBuildingAttributes(
                    currentHeadBlock, blockSlot, epoch, maybeState, mandatory),
            eventThread);
  }

  /**
   * Calculate {@link PayloadBuildingAttributes} to be sent to EL if one of our configured
   * validators is due to propose a block or forkChoiceUpdatedAlwaysSendPayloadAttribute is set to
   * true
   *
   * @param mandatory force to calculate {@link PayloadBuildingAttributes} (used in rare cases,
   *     where payloadId hasn't been retrieved from EL for the block slot)
   */
  private SafeFuture<Optional<PayloadBuildingAttributes>> calculatePayloadBuildingAttributes(
      final ForkChoiceNode currentHeadBlock,
      final UInt64 blockSlot,
      final UInt64 epoch,
      final Optional<BeaconState> maybeState,
      final boolean mandatory) {
    eventThread.checkOnEventThread();
    if (maybeState.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final BeaconState state = maybeState.get();
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final PreparedProposerInfo proposerInfo =
        preparedProposerInfoByValidatorIndex.get(proposerIndex);

    if (proposerInfo == null && !(mandatory || forkChoiceUpdatedAlwaysSendPayloadAttribute)) {
      // Proposer is not one of our validators. No need to propose a block.
      return SafeFuture.completedFuture(Optional.empty());
    }

    final UInt64 timestamp = spec.computeTimeAtSlot(state, blockSlot);
    final Bytes32 random = spec.getRandaoMix(state, epoch);
    final Optional<SignedValidatorRegistration> validatorRegistration =
        Optional.ofNullable(validatorRegistrationInfoByValidatorIndex.get(proposerIndex))
            .map(RegisteredValidatorInfo::getSignedValidatorRegistration);

    final Eth1Address feeRecipient = getFeeRecipient(proposerInfo, blockSlot);
    final UInt64 targetGasLimit =
        getTargetGasLimit(blockSlot, proposerIndex, validatorRegistration);

    return getPayloadAttributeWithdrawals(currentHeadBlock, state)
        .thenApplyAsync(
            withdrawals ->
                Optional.of(
                    new PayloadBuildingAttributes(
                        proposerIndex,
                        blockSlot,
                        timestamp,
                        random,
                        feeRecipient,
                        targetGasLimit,
                        validatorRegistration,
                        withdrawals,
                        currentHeadBlock)),
            eventThread);
  }

  private SafeFuture<Optional<List<Withdrawal>>> getPayloadAttributeWithdrawals(
      final ForkChoiceNode currentHeadBlock, final BeaconState state) {
    if (!requiresGloasParentExecutionRequests(currentHeadBlock)) {
      return SafeFuture.of(
          () ->
              spec.getPayloadAttributeWithdrawals(
                  state, currentHeadBlock.payloadStatus(), Optional.empty()));
    }
    return retrieveParentExecutionRequests(currentHeadBlock.blockRoot(), state.getSlot())
        .thenApply(
            parentExecutionRequests ->
                spec.getPayloadAttributeWithdrawals(
                    state, currentHeadBlock.payloadStatus(), Optional.of(parentExecutionRequests)));
  }

  private boolean requiresGloasParentExecutionRequests(final ForkChoiceNode currentHeadBlock) {
    return currentHeadBlock.payloadStatus().equals(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  private SafeFuture<ExecutionRequests> retrieveParentExecutionRequests(
      final Bytes32 blockRoot, final UInt64 blockSlot) {
    return recentChainData
        .retrieveSignedBlindedExecutionPayloadByBlockRoot(blockRoot)
        .thenApply(
            executionPayload ->
                executionPayload
                    .map(SignedBlindedExecutionPayloadEnvelope::getExecutionRequests)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Execution Requests for parent root %s are not available during payload attribute calculation for slot %s",
                                    blockRoot, blockSlot))));
  }

  private SafeFuture<Optional<BeaconState>> getStateForPayloadBuildingAttributes(
      final UInt64 blockSlot, final ForkChoiceState forkChoiceState) {
    return recentChainData.retrieveBlockState(
        new SlotAndBlockRoot(blockSlot, forkChoiceState.headBlock().blockRoot()));
  }

  @VisibleForTesting
  UInt64 getTargetGasLimit(
      final UInt64 blockSlot,
      final UInt64 proposerIndex,
      final Optional<SignedValidatorRegistration> validatorRegistration) {
    // post-Gloas, we use signed proposer preferences
    return proposerPreferencesManager
        .getProposerPreferences(blockSlot)
        .filter(
            proposerPreferences -> proposerPreferences.getValidatorIndex().equals(proposerIndex))
        .map(ProposerPreferences::getTargetGasLimit)
        // pre-Gloas, we use validator registrations
        .or(
            () ->
                validatorRegistration.map(registration -> registration.getMessage().getGasLimit()))
        .orElse(UInt64.ZERO);
  }

  // this function MUST return a fee recipient.
  private Eth1Address getFeeRecipient(
      final PreparedProposerInfo preparedProposerInfo, final UInt64 blockSlot) {
    if (preparedProposerInfo != null) {
      return preparedProposerInfo.getFeeRecipient();
    }
    if (proposerDefaultFeeRecipient.isPresent()) {
      VALIDATOR_LOGGER.executionPayloadPreparedUsingBeaconDefaultFeeRecipient(blockSlot);
      return proposerDefaultFeeRecipient.get();
    }
    VALIDATOR_LOGGER.executionPayloadPreparedUsingBurnAddressForFeeRecipient(blockSlot);
    return Eth1Address.ZERO;
  }

  private SafeFuture<Optional<BeaconState>> getStateInEpoch(final UInt64 requiredEpoch) {
    final Optional<ChainHead> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final ChainHead head = chainHead.get();
    if (spec.computeEpochAtSlot(head.getSlot()).equals(requiredEpoch)) {
      return head.getState().thenApply(Optional::of);
    } else {
      return recentChainData.retrieveBlockState(
          new SlotAndBlockRoot(spec.computeStartSlotAtEpoch(requiredEpoch), head.getRoot()));
    }
  }

  public Map<UInt64, PreparedProposerInfo> getPreparedProposerInfo() {
    return preparedProposerInfoByValidatorIndex;
  }

  public Map<UInt64, RegisteredValidatorInfo> getValidatorRegistrationInfo() {
    return validatorRegistrationInfoByValidatorIndex;
  }

  public boolean isProposerDefaultFeeRecipientDefined() {
    return proposerDefaultFeeRecipient.isPresent();
  }
}

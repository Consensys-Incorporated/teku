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

package tech.pegasys.teku.validator.coordinator;

import static java.util.stream.Collectors.toMap;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getRootCauseMessage;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.infrastructure.metrics.Validator.DutyType.ATTESTATION_PRODUCTION;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils.startTimer;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.EQUIVOCATION;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.GOSSIP;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBidOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.duties.AttesterDutiesGenerator;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.publisher.BlockPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;

public class ValidatorApiHandler implements ValidatorApiChannel, SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * Number of epochs ahead of the current head that duties can be requested. This provides some
   * tolerance for validator clients clocks being slightly ahead while still limiting the number of
   * empty slots that may need to be processed when calculating duties.
   */
  private static final int DUTY_EPOCH_TOLERANCE = 1;

  private final Map<UInt64, SafeFuture<Optional<BlockContainerAndMetaData>>>
      blockProductionBySlotCache = new ConcurrentHashMap<>();
  private final Map<UInt64, BlockProductionPreparationContext>
      blockProductionPreparationContextBySlotCache = new ConcurrentHashMap<>();

  private final BlockProductionAndPublishingPerformanceFactory
      blockProductionAndPublishingPerformanceFactory;
  private final ChainDataProvider chainDataProvider;
  private final NodeDataProvider nodeDataProvider;
  private final NetworkDataProvider networkDataProvider;
  protected final CombinedChainDataClient combinedChainDataClient;
  private final SyncStateProvider syncStateProvider;
  private final BlockFactory blockFactory;
  private final AggregatingAttestationPool attestationPool;
  private final AttestationManager attestationManager;
  private final AttestationTopicSubscriber attestationTopicSubscriber;
  private final ActiveValidatorTracker activeValidatorTracker;
  private final DutyMetrics dutyMetrics;
  private final PerformanceTracker performanceTracker;
  protected final Spec spec;
  protected final ForkChoiceTrigger forkChoiceTrigger;
  private final SyncCommitteeMessagePool syncCommitteeMessagePool;
  private final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager;
  private final SyncCommitteeContributionPool syncCommitteeContributionPool;
  private final ProposersDataManager proposersDataManager;
  private final BlockPublisher blockPublisher;
  private final PayloadAttestationPool payloadAttestationPool;
  private final DataAvailabilitySampler dataAvailabilitySampler;
  private final ExecutionPayloadManager executionPayloadManager;
  private final ExecutionPayloadFactory executionPayloadFactory;
  private final ExecutionPayloadPublisher executionPayloadPublisher;
  private final ExecutionPayloadBidManager executionPayloadBidManager;
  private final ProposerPreferencesManager proposerPreferencesManager;
  private final ExecutionProofManager executionProofManager;

  private final AttesterDutiesGenerator attesterDutiesGenerator;

  public ValidatorApiHandler(
      final ChainDataProvider chainDataProvider,
      final NodeDataProvider nodeDataProvider,
      final NetworkDataProvider networkDataProvider,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncStateProvider syncStateProvider,
      final BlockFactory blockFactory,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final AttestationTopicSubscriber attestationTopicSubscriber,
      final ActiveValidatorTracker activeValidatorTracker,
      final DutyMetrics dutyMetrics,
      final PerformanceTracker performanceTracker,
      final Spec spec,
      final ForkChoiceTrigger forkChoiceTrigger,
      final ProposersDataManager proposersDataManager,
      final SyncCommitteeMessagePool syncCommitteeMessagePool,
      final SyncCommitteeContributionPool syncCommitteeContributionPool,
      final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager,
      final BlockProductionAndPublishingPerformanceFactory
          blockProductionAndPublishingPerformanceFactory,
      final BlockPublisher blockPublisher,
      final PayloadAttestationPool payloadAttestationPool,
      final DataAvailabilitySampler dataAvailabilitySampler,
      final ExecutionPayloadManager executionPayloadManager,
      final ExecutionPayloadFactory executionPayloadFactory,
      final ExecutionPayloadPublisher executionPayloadPublisher,
      final ExecutionPayloadBidManager executionPayloadBidManager,
      final ProposerPreferencesManager proposerPreferencesManager,
      final ExecutionProofManager executionProofManager) {
    this.blockProductionAndPublishingPerformanceFactory =
        blockProductionAndPublishingPerformanceFactory;
    this.chainDataProvider = chainDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.networkDataProvider = networkDataProvider;
    this.combinedChainDataClient = combinedChainDataClient;
    this.syncStateProvider = syncStateProvider;
    this.blockFactory = blockFactory;
    this.attestationPool = attestationPool;
    this.attestationManager = attestationManager;
    this.attestationTopicSubscriber = attestationTopicSubscriber;
    this.activeValidatorTracker = activeValidatorTracker;
    this.dutyMetrics = dutyMetrics;
    this.performanceTracker = performanceTracker;
    this.spec = spec;
    this.forkChoiceTrigger = forkChoiceTrigger;
    this.syncCommitteeMessagePool = syncCommitteeMessagePool;
    this.syncCommitteeContributionPool = syncCommitteeContributionPool;
    this.syncCommitteeSubscriptionManager = syncCommitteeSubscriptionManager;
    this.proposersDataManager = proposersDataManager;
    this.blockPublisher = blockPublisher;
    this.payloadAttestationPool = payloadAttestationPool;
    this.dataAvailabilitySampler = dataAvailabilitySampler;
    this.executionPayloadManager = executionPayloadManager;
    this.executionPayloadFactory = executionPayloadFactory;
    this.executionPayloadPublisher = executionPayloadPublisher;
    this.executionPayloadBidManager = executionPayloadBidManager;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.executionProofManager = executionProofManager;
    this.attesterDutiesGenerator = new AttesterDutiesGenerator(spec);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    blockProductionPreparationContextBySlotCache
        .keySet()
        .removeIf(cachedSlot -> cachedSlot.isLessThan(slot));
    blockProductionBySlotCache.keySet().removeIf(cachedSlot -> cachedSlot.isLessThan(slot));
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return SafeFuture.completedFuture(combinedChainDataClient.getGenesisData());
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return combinedChainDataClient
        .getBestState()
        .orElseGet(
            () ->
                SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available")))
        .thenApply(
            state -> {
              @SuppressWarnings("UseFastutil")
              final Map<BLSPublicKey, Integer> results = new HashMap<>();
              publicKeys.forEach(
                  publicKey ->
                      spec.getValidatorIndex(state, publicKey)
                          .ifPresent(index -> results.put(publicKey, index)));
              return results;
            });
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {

    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    if (epoch.isGreaterThan(
        combinedChainDataClient
            .getCurrentEpoch()
            .plus(spec.getSpecConfig(epoch).getMinSeedLookahead() + DUTY_EPOCH_TOLERANCE))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Attestation duties were requested %s epochs ahead, only 1 epoch in future is supported.",
                  epoch.minus(combinedChainDataClient.getCurrentEpoch()).toString())));
    }
    // what state can we use? If the current or next epoch, we can use the best state,
    // which would guarantee no state regeneration
    final UInt64 slot = spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch);

    LOG.trace("Retrieving attestation duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(
            optionalState ->
                optionalState.map(
                    state ->
                        attesterDutiesGenerator.getAttesterDutiesFromIndicesAndState(
                            state,
                            epoch,
                            validatorIndices,
                            combinedChainDataClient.isChainHeadOptimistic())));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    final SpecVersion specVersion = spec.atEpoch(epoch);

    return getStateForCommitteeDuties(specVersion, epoch)
        .thenApply(
            maybeState ->
                Optional.of(
                    getSyncCommitteeDutiesFromIndicesAndState(
                        maybeState, epoch, validatorIndices)));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(
      final UInt64 epoch, final boolean isFuluCompatible) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    final int tolerance = spec.getSpecConfig(epoch).getMinSeedLookahead() + DUTY_EPOCH_TOLERANCE;
    LOG.trace("Proposer duty tolerance is {} epochs", tolerance);
    if (epoch.isGreaterThan(currentEpoch.plus(tolerance))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Proposer duties were requested %s epochs ahead, only 1 epoch in future is supported.",
                  epoch.minus(currentEpoch).toString())));
    }

    final UInt64 stateSlot =
        spec.atEpoch(epoch)
            .getBlockProposalUtil()
            .getStateSlotForProposerDuties(spec, currentEpoch, epoch);
    LOG.debug(
        "Retrieving proposer duties for epoch {}, current epoch {}, state query slot {}",
        epoch,
        currentEpoch,
        stateSlot);
    return combinedChainDataClient
        .getStateAtSlotExact(stateSlot)
        .thenApply(
            maybeState ->
                maybeState.map(
                    state -> getProposerDutiesFromState(state, epoch, isFuluCompatible)));
  }

  @Override
  public SafeFuture<Optional<PtcDuties>> getPtcDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    if (epoch.isGreaterThan(
        combinedChainDataClient
            .getCurrentEpoch()
            .plus(spec.getSpecConfig(epoch).getMinSeedLookahead() + DUTY_EPOCH_TOLERANCE))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Ptc duties were requested %s epochs ahead, PTC committee selection is only stable "
                      + "within the context of the current and next epochs in the lookahead.",
                  epoch.minus(combinedChainDataClient.getCurrentEpoch()).toString())));
    }
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch.minusMinZero(1));
    LOG.trace("Retrieving ptc duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(
            maybeState ->
                maybeState.map(
                    state -> getPtcDutiesFromIndicesAndState(state, epoch, validatorIndices)));
  }

  private PtcDuties getPtcDutiesFromIndicesAndState(
      final BeaconState state, final UInt64 epoch, final IntCollection validatorIndices) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(spec.getCurrentEpoch(state))
            ? spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state)
            : spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
    final List<PtcDuty> duties = new ArrayList<>();
    final Int2ObjectMap<UInt64> validatorIndexToPtcAssignmentMap =
        spec.getValidatorIndexToPtcAssignmentMap(state, epoch);
    validatorIndices.forEach(
        i -> {
          final UInt64 ptcDutySlot = validatorIndexToPtcAssignmentMap.get(i);
          final UInt64 validatorIndex = UInt64.valueOf(i);
          if (ptcDutySlot != null) {
            spec.getValidatorPubKey(state, validatorIndex)
                .ifPresent(
                    publicKey -> duties.add(new PtcDuty(publicKey, validatorIndex, ptcDutySlot)));
          }
        });
    return new PtcDuties(combinedChainDataClient.isChainHeadOptimistic(), dependentRoot, duties);
  }

  @Override
  public SafeFuture<Optional<PeerCount>> getPeerCount() {
    return SafeFuture.completedFuture(Optional.of(networkDataProvider.getPeerCount()));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, StateValidatorData>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return isSyncActive()
        ? SafeFuture.completedFuture(Optional.empty())
        : chainDataProvider
            .getStateValidators(
                "head",
                validatorIdentifiers.stream().map(BLSPublicKey::toString).toList(),
                new HashSet<>())
            .thenApply(
                (maybeList) ->
                    maybeList.map(
                        list ->
                            list.getData().stream()
                                .collect(
                                    toMap(StateValidatorData::getPublicKey, Function.identity()))));
  }

  /**
   * Block would be produced only once per slot. Any additional calls to this method for the same
   * slot would return the same {@link SafeFuture} as the first one. The only exception is when the
   * block production fails. In this case, the next call would attempt to produce the block again.
   */
  @Override
  public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig) {
    return blockProductionBySlotCache
        .computeIfAbsent(
            slot,
            __ ->
                createUnsignedBlockInternal(
                    slot, randaoReveal, graffiti, includePayload, builderConfig))
        .whenException(
            __ -> {
              // allow further block production attempts for this slot
              blockProductionBySlotCache.remove(slot);
            });
  }

  public void onBlockProductionPreparationDue(final UInt64 slot) {
    if (isSyncActive()) {
      return;
    }
    prepareBlockProductionInternal(slot);
  }

  private BlockProductionPreparationContext prepareBlockProductionInternal(final UInt64 slot) {
    return blockProductionPreparationContextBySlotCache.computeIfAbsent(
        slot,
        __ -> {
          LOG.info("Preparing block production for slot {}", slot);
          final BlockProductionPerformance productionPerformance =
              blockProductionAndPublishingPerformanceFactory.createForProduction(slot);
          final SafeFuture<ChainHead> maybeChainHead =
              forkChoiceTrigger.prepareForBlockProduction(slot, productionPerformance);

          final SafeFuture<BeaconState> stateFuture =
              maybeChainHead
                  .thenCompose(
                      chainHead ->
                          combinedChainDataClient.getStateForBlockProduction(chainHead, slot))
                  .thenPeek(___ -> productionPerformance.getState());

          return new BlockProductionPreparationContext(
              stateFuture, maybeChainHead, productionPerformance);
        });
  }

  private SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlockInternal(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig) {
    LOG.info("Creating unsigned block for slot {}", slot);
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(slot));
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }

    final BlockProductionPreparationContext blockProductionPreparationContext =
        prepareBlockProductionInternal(slot);

    blockProductionPreparationContext.blockProductionPerformance.validatorBlockRequested();

    return blockProductionPreparationContext
        .toBlockProductionContext(spec, slot, randaoReveal, graffiti, includePayload, builderConfig)
        .thenCompose(this::createBlock)
        .thenPeek(
            maybeBlock ->
                maybeBlock.ifPresent(
                    block ->
                        performanceTracker.saveProducedBlock(
                            block.blockContainer().getBlock().getSlotAndBlockRoot())))
        .alwaysRun(blockProductionPreparationContext.blockProductionPerformance::complete);
  }

  private SafeFuture<Optional<BlockContainerAndMetaData>> createBlock(
      final BlockProductionContext blockProductionContext) {
    LOG.debug(
        "parent block {}:({})",
        blockProductionContext.parentRoot(),
        blockProductionContext.proposalSlot());
    if (combinedChainDataClient.isOptimisticBlock(blockProductionContext.parentRoot())) {
      LOG.warn(
          "Unable to produce block at slot {} because parent has optimistically validated payload",
          blockProductionContext.proposalSlot());
      throw new NodeSyncingException();
    }
    return blockFactory.createUnsignedBlock(blockProductionContext).thenApply(Optional::of);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }

    final UInt64 currentSlot = combinedChainDataClient.getCurrentSlot();
    if (slot.isGreaterThan(currentSlot)) {
      // Avoid creating attestations in the future as that may cause fork choice to run too soon
      // and then not re-run when it is actually due.  It's also dangerous for validators to create
      // attestations in the future.  Since attestations are due either when the block is imported
      // or 4 seconds into the slot, there's some tolerance for clock skew already built in.
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              "Cannot create attestation for future slot. Requested "
                  + slot
                  + " but current slot is "
                  + currentSlot));
    }
    // we are in an async context, don't follow the AutoClose pattern
    final OperationTimer.TimingContext context =
        startTimer(
            dutyMetrics.getValidatorDutyMetric(),
            ATTESTATION_PRODUCTION.getName(),
            CREATE.getName());

    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 minQuerySlot = spec.computeStartSlotAtEpoch(epoch);

    final SafeFuture<Optional<AttestationData>> result =
        forkChoiceTrigger
            .prepareForAttestationProduction(slot)
            .thenCompose(
                __ ->
                    combinedChainDataClient
                        .getSignedBlockAndStateInEffectAtSlot(slot)
                        .thenCompose(
                            maybeBlockAndState -> {
                              if (maybeBlockAndState.isEmpty()) {
                                return SafeFuture.completedFuture(Optional.empty());
                              }
                              final SignedBlockAndState blockAndState = maybeBlockAndState.get();
                              final BeaconBlock block = blockAndState.getBlock().getMessage();

                              // The head block must not be optimistically synced.
                              if (combinedChainDataClient.isOptimisticBlock(block.getRoot())) {
                                return NodeSyncingException.failedFuture();
                              }
                              if (blockAndState.getSlot().compareTo(minQuerySlot) < 0) {
                                // The current effective block is too far in the past - so roll the
                                // state forward to the current epoch. Ensures we have the latest
                                // justified checkpoint
                                return combinedChainDataClient
                                    .getCheckpointState(epoch, blockAndState)
                                    .thenApply(
                                        checkpointState ->
                                            Optional.of(
                                                createAttestationData(
                                                    block,
                                                    checkpointState.getState(),
                                                    slot,
                                                    computeCommitteeIndexForAttestation(
                                                        slot, block, committeeIndex))));
                              } else {
                                final AttestationData attestationData =
                                    createAttestationData(
                                        block,
                                        blockAndState.getState(),
                                        slot,
                                        computeCommitteeIndexForAttestation(
                                            slot, block, committeeIndex));
                                return SafeFuture.completedFuture(Optional.of(attestationData));
                              }
                            }));
    result.always(context::stopTimer);
    return result;
  }

  private int computeCommitteeIndexForAttestation(
      final UInt64 slot, final BeaconBlock block, final int committeeIndex) {
    return spec.atSlot(slot)
        .getForkChoiceUtil()
        .computeCommitteeIndexForAttestation(
            slot, block, committeeIndex, combinedChainDataClient.getStore());
  }

  private AttestationData createAttestationData(
      final BeaconBlock block,
      final BeaconState state,
      final UInt64 slot,
      final int committeeIndex) {
    final UInt64 committeeIndexUnsigned = UInt64.valueOf(committeeIndex);
    // attestation validation
    spec.atSlot(slot)
        .getAttestationUtil()
        .validateCommitteeIndexValue(committeeIndexUnsigned)
        .getReason()
        .ifPresent(
            reason -> {
              throw new IllegalArgumentException(reason);
            });
    return spec.getGenericAttestationData(slot, state, block, committeeIndexUnsigned);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return SafeFuture.completedFuture(
        attestationPool
            .createAggregateFor(attestationHashTreeRoot, committeeIndex)
            .filter(attestation -> attestation.getData().getSlot().equals(slot)));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return SafeFuture.completedFuture(
        syncCommitteeMessagePool.createContribution(slot, beaconBlockRoot, subcommitteeIndex));
  }

  @Override
  public SafeFuture<Optional<PayloadAttestationData>> createPayloadAttestationData(
      final UInt64 slot) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return combinedChainDataClient
        .getBlockAtSlotExact(slot)
        .thenApply(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return Optional.empty();
              }
              final SignedBeaconBlock block = maybeBlock.get();
              final boolean payloadPresent =
                  executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(block.getRoot());
              final boolean blobDataAvailable = dataAvailabilitySampler.isDataAvailable(block);
              final PayloadAttestationData payloadAttestationData =
                  SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
                      .getPayloadAttestationDataSchema()
                      .create(block.getRoot(), slot, payloadPresent, blobDataAvailable);
              return Optional.of(payloadAttestationData);
            });
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return SafeFuture.fromRunnable(() -> processCommitteeSubscriptionRequests(requests));
  }

  private void processCommitteeSubscriptionRequests(
      final List<CommitteeSubscriptionRequest> requests) {
    requests.forEach(
        request -> {
          // The old subscription API can't provide the validator ID so until it can be removed,
          // don't track validators from those calls - they should use the old API to subscribe to
          // persistent subnets.
          if (request.getValidatorIndex() != UNKNOWN_VALIDATOR_ID) {
            activeValidatorTracker.onCommitteeSubscriptionRequest(
                request.getValidatorIndex(), request.getSlot());
          }

          if (request.isAggregator()) {
            attestationTopicSubscriber.subscribeToCommitteeForAggregation(
                request.getCommitteeIndex(), request.getCommitteesAtSlot(), request.getSlot());
          }
        });
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return SafeFuture.fromRunnable(() -> processSyncCommitteeSubnetSubscriptions(subscriptions));
  }

  private void processSyncCommitteeSubnetSubscriptions(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    for (final SyncCommitteeSubnetSubscription subscription : subscriptions) {
      // untilEpoch is exclusive, so it will unsubscribe at the first slot of the specified index
      final UInt64 untilEpoch = subscription.untilEpoch();
      final UInt64 unsubscribeSlot = spec.computeStartSlotAtEpoch(untilEpoch);
      final SyncCommitteeUtil syncCommitteeUtil =
          spec.getSyncCommitteeUtilRequired(spec.computeStartSlotAtEpoch(untilEpoch));
      final IntSet syncCommitteeIndices = subscription.syncCommitteeIndices();
      performanceTracker.saveExpectedSyncCommitteeParticipant(
          subscription.validatorIndex(), syncCommitteeIndices, untilEpoch.decrement());
      syncCommitteeUtil
          .getSyncSubcommittees(syncCommitteeIndices)
          .forEach(index -> syncCommitteeSubscriptionManager.subscribe(index, unsubscribeSlot));
    }
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return SafeFuture.fromRunnable(
        () -> attestationTopicSubscriber.subscribeToPersistentSubnets(subnetSubscriptions));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return SafeFuture.collectAll(attestations.stream().map(this::processAttestation))
        .thenApply(this::convertInternalValidationResults);
  }

  private SafeFuture<InternalValidationResult> processAttestation(final Attestation attestation) {
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.fromValidator(spec, attestation);
    return attestationManager
        .addAttestation(validatableAttestation, Optional.empty())
        .thenPeek(
            result -> {
              if (!result.isReject()) {
                // When saving the attestation in performance tracker, we want to make sure we save
                // the converted attestation.
                // The conversion happens during processing and is saved in the validatable
                // attestation.
                // In some cases we could still have a non-converted SingleAttestation with a
                // SAVE_FOR_FUTURE or IGNORE validation result. It will be converted by the
                // PerformanceTracker
                final Attestation convertedAttestation = validatableAttestation.getAttestation();
                dutyMetrics.onAttestationPublished(convertedAttestation.getData().getSlot());
                performanceTracker.saveProducedAttestation(convertedAttestation);
              } else {
                VALIDATOR_LOGGER.producedInvalidAttestation(
                    attestation.getData().getSlot(),
                    result.getDescription().orElse("Unknown reason"));
              }
            })
        .exceptionally(
            error -> {
              LOG.error(
                  "Failed to send signed attestation for slot {}, block {}",
                  attestation.getData().getSlot(),
                  attestation.getData().getBeaconBlockRoot(),
                  error);
              return InternalValidationResult.reject(
                  "Failed to send signed attestation for slot %s, block %s: %s",
                  attestation.getData().getSlot(),
                  attestation.getData().getBeaconBlockRoot(),
                  getMessageOrSimpleName(error));
            });
  }

  private List<SubmitDataError> convertInternalValidationResults(
      final List<InternalValidationResult> results) {
    final List<SubmitDataError> errorList = new ArrayList<>();
    for (int index = 0; index < results.size(); index++) {
      final InternalValidationResult result = results.get(index);
      if (result.isReject()) {
        errorList.add(
            new SubmitDataError(
                UInt64.valueOf(index), result.getDescription().orElse("Unknown reason")));
      }
    }
    return errorList;
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return SafeFuture.collectAll(aggregateAndProofs.stream().map(this::processAggregateAndProof))
        .thenApply(this::convertInternalValidationResults);
  }

  private SafeFuture<InternalValidationResult> processAggregateAndProof(
      final SignedAggregateAndProof aggregateAndProof) {
    return attestationManager
        .addAggregate(
            ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof),
            Optional.empty())
        .thenPeek(
            result -> {
              if (result.isReject()) {
                VALIDATOR_LOGGER.producedInvalidAggregate(
                    aggregateAndProof.getMessage().getAggregate().getData().getSlot(),
                    result.getDescription().orElse("Unknown reason"));
              }
            });
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer maybeBlindedBlockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final BlockPublishingPerformance blockPublishingPerformance =
        blockProductionAndPublishingPerformanceFactory.createForPublishing(
            maybeBlindedBlockContainer.getSlot());

    final boolean isLocallyCreated = isLocallyCreatedBlock(maybeBlindedBlockContainer);

    LOG.info("starting to publish block at slot {}", maybeBlindedBlockContainer.getSlot());

    return blockPublisher
        .sendSignedBlock(
            maybeBlindedBlockContainer,
            // do only EQUIVOCATION validation when GOSSIP validation has been requested and the
            // block has been locally created
            broadcastValidationLevel == GOSSIP && isLocallyCreated
                ? EQUIVOCATION
                : broadcastValidationLevel,
            blockPublishingPerformance)
        .exceptionally(
            ex -> {
              final String reason = getRootCauseMessage(ex);
              return SendSignedBlockResult.rejected(reason);
            })
        .alwaysRun(blockPublishingPerformance::complete)
        .thenPeek(
            __ -> {
              if (isLocallyCreated) {
                executionProofManager
                    .generateProofs(maybeBlindedBlockContainer)
                    .finish(error -> LOG.debug("failed to generate execution proofs", error));
              }
            });
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {

    final List<SafeFuture<InternalValidationResult>> addedMessages =
        syncCommitteeMessages.stream()
            .map(ValidatableSyncCommitteeMessage::fromValidator)
            .map(this::processSyncCommitteeMessage)
            .toList();

    return SafeFuture.collectAll(addedMessages.stream())
        .thenApply(this::getSendSyncCommitteesResultFromFutures);
  }

  private SafeFuture<InternalValidationResult> processSyncCommitteeMessage(
      final ValidatableSyncCommitteeMessage message) {
    return syncCommitteeMessagePool
        .addLocal(message)
        .thenPeek(
            result -> {
              if (result.isAccept() || result.isSaveForFuture()) {
                performanceTracker.saveProducedSyncCommitteeMessage(message.getMessage());
              }
            });
  }

  private List<SubmitDataError> getSendSyncCommitteesResultFromFutures(
      final List<InternalValidationResult> internalValidationResults) {
    final List<SubmitDataError> errorList = new ArrayList<>();
    for (int index = 0; index < internalValidationResults.size(); index++) {
      final Optional<SubmitDataError> maybeError =
          fromInternalValidationResult(internalValidationResults.get(index), index);
      maybeError.ifPresent(errorList::add);
    }
    return errorList;
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> aggregates) {
    return SafeFuture.collectAll(aggregates.stream().map(syncCommitteeContributionPool::addLocal))
        .thenAccept(
            results -> {
              final List<String> errorMessages =
                  results.stream()
                      .filter(InternalValidationResult::isReject)
                      .flatMap(result -> result.getDescription().stream())
                      .toList();
              if (!errorMessages.isEmpty()) {
                throw new IllegalArgumentException(
                    "Invalid contribution and proofs: ;" + String.join(";", errorMessages));
              }
            });
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendPayloadAttestationMessages(
      final List<PayloadAttestationMessage> payloadAttestationMessages) {
    return SafeFuture.collectAll(
            payloadAttestationMessages.stream().map(payloadAttestationPool::addLocal))
        .thenApply(this::convertInternalValidationResults);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedProposerPreferences(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    return SafeFuture.collectAll(
            signedProposerPreferences.stream().map(proposerPreferencesManager::addLocal))
        .thenApply(this::convertInternalValidationResults)
        .thenPeek(
            __ ->
                proposersDataManager.updatePreparedProposersFromProposerPreferences(
                    signedProposerPreferences, combinedChainDataClient.getCurrentSlot()));
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return SafeFuture.fromRunnable(
        () ->
            proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
                beaconPreparableProposers, combinedChainDataClient.getCurrentSlot()));
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return proposersDataManager.updateValidatorRegistrations(
        validatorRegistrations, combinedChainDataClient.getCurrentSlot());
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return nodeDataProvider.getValidatorLiveness(
        validatorIndices, epoch, chainDataProvider.getCurrentEpoch());
  }

  @Override
  public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> requests) {
    throw new UnsupportedOperationException("This method is not implemented by the Beacon Node");
  }

  @Override
  public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> requests) {
    throw new UnsupportedOperationException("This method is not implemented by the Beacon Node");
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadBid>> createUnsignedExecutionPayloadBid(
      final UInt64 slot, final UInt64 builderIndex) {
    throw new UnsupportedOperationException("This method is not implemented by the Beacon Node");
  }

  @Override
  public SafeFuture<Void> publishSignedExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    return executionPayloadBidManager
        .validateAndAddBid(signedExecutionPayloadBid, RemoteBidOrigin.BUILDER)
        .thenAccept(
            result -> {
              if (!result.isAccept()) {
                throw new IllegalArgumentException(
                    "Invalid execution payload bid: "
                        + result.getDescription().orElse("unknown reason"));
              }
            });
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return combinedChainDataClient
        .getBlockAndStateInEffectAtSlot(slot)
        .thenCompose(
            maybeBlockAndState -> {
              if (maybeBlockAndState.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
              }
              final BeaconBlockAndState blockAndState = maybeBlockAndState.get();
              if (!blockAndState.getRoot().equals(beaconBlockRoot)) {
                LOG.warn(
                    "Unable to produce execution payload for slot {} and block {} because the block for this slot according to the BN is {}",
                    slot,
                    beaconBlockRoot,
                    blockAndState.getRoot());
                return CompletableFuture.completedFuture(Optional.empty());
              }
              LOG.info(
                  "Producing unsigned execution payload for slot {} and block {}",
                  slot,
                  beaconBlockRoot);
              if (combinedChainDataClient.isOptimisticBlock(blockAndState.getParentRoot())) {
                LOG.warn(
                    "Unable to produce execution payload for slot {} and block {} because parent has optimistically validated payload",
                    slot,
                    blockAndState.getRoot().toUnprefixedHexString());
                return NodeSyncingException.failedFuture();
              }
              return executionPayloadFactory
                  .createUnsignedExecutionPayload(blockAndState)
                  .thenApply(Optional::of);
            });
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return executionPayloadPublisher
        .publishSignedExecutionPayload(signedExecutionPayload, broadcastValidationLevel)
        .exceptionally(
            ex -> {
              final String reason = getRootCauseMessage(ex);
              return PublishSignedExecutionPayloadResult.rejected(
                  signedExecutionPayload.getBeaconBlockRoot(), reason);
            });
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return executionPayloadPublisher
        .publishSignedExecutionPayload(
            signedExecutionPayloadEnvelopeContents, broadcastValidationLevel)
        .exceptionally(
            ex -> {
              final String reason = getRootCauseMessage(ex);
              return PublishSignedExecutionPayloadResult.rejected(
                  signedExecutionPayloadEnvelopeContents.getBeaconBlockRoot(), reason);
            });
  }

  private Optional<SubmitDataError> fromInternalValidationResult(
      final InternalValidationResult internalValidationResult, final int resultIndex) {
    if (!internalValidationResult.isReject()) {
      return Optional.empty();
    }
    return Optional.of(
        new SubmitDataError(
            UInt64.valueOf(resultIndex),
            internalValidationResult.getDescription().orElse("Rejected")));
  }

  @VisibleForTesting
  boolean isSyncActive() {
    return !syncStateProvider.getCurrentSyncState().isInSync();
  }

  private ProposerDuties getProposerDutiesFromState(
      final BeaconState state, final UInt64 epoch, final boolean isFuluCompatible) {
    final List<ProposerDuty> result = getProposalSlotsForEpoch(state, epoch);
    if (spec.atEpoch(epoch).getMilestone().isLessThan(SpecMilestone.FULU)) {
      return new ProposerDuties(
          spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state),
          result,
          combinedChainDataClient.isChainHeadOptimistic());
    }
    final UInt64 currentEpoch = spec.getCurrentEpoch(state);
    final boolean greaterThanCurrentEpoch = epoch.isGreaterThan(currentEpoch);
    final BlockProposalUtil blockProposalUtil;
    if (isFuluCompatible) {
      blockProposalUtil = spec.atEpoch(epoch).getBlockProposalUtil();
    } else {
      blockProposalUtil = spec.forMilestone(SpecMilestone.PHASE0).getBlockProposalUtil();
    }
    final Bytes32 dependentRoot =
        blockProposalUtil.getBlockProposalDependentRoot(
            combinedChainDataClient.getBestBlockRoot().orElseThrow(),
            spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state),
            spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state),
            spec.computeEpochAtSlot(state.getSlot()),
            epoch);
    LOG.trace(
        "state epoch {}, duties epoch {}, greaterThanCurrentEpoch {}, dependentRoot {}",
        currentEpoch,
        epoch,
        greaterThanCurrentEpoch,
        dependentRoot);
    return new ProposerDuties(
        dependentRoot, result, combinedChainDataClient.isChainHeadOptimistic());
  }

  private SafeFuture<Optional<BeaconState>> getStateForCommitteeDuties(
      final SpecVersion specVersion, final UInt64 epoch) {
    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil = specVersion.getSyncCommitteeUtil();
    if (maybeSyncCommitteeUtil.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();
    final Optional<SafeFuture<BeaconState>> maybeBestState = combinedChainDataClient.getBestState();
    if (maybeBestState.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return maybeBestState
        .get()
        .thenCompose(
            bestState -> {
              if (syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(bestState, epoch)) {
                return SafeFuture.completedFuture(Optional.of(bestState));
              }

              final UInt64 maxQueryableEpoch =
                  syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(
                      combinedChainDataClient.getCurrentEpoch());
              if (maxQueryableEpoch.isLessThan(epoch)) {
                final Optional<UInt64> networkCurrentSlot =
                    chainDataProvider.getNetworkCurrentSlot();
                return SafeFuture.failedFuture(
                    new IllegalArgumentException(
                        String.format(
                            "Cannot calculate sync committee duties for epoch %s because it is not within the current or next sync committee periods (node current epoch %s, computed current slot %s)",
                            epoch, combinedChainDataClient.getCurrentEpoch(), networkCurrentSlot)));
              }

              final UInt64 requiredEpoch;
              final UInt64 stateEpoch = spec.getCurrentEpoch(bestState);
              if (epoch.isGreaterThan(stateEpoch)) {
                // Use the earliest possible epoch since we'll need to process empty slots
                requiredEpoch = syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch);
              } else {
                // Use the latest possible epoch since it's most likely to still be in memory
                requiredEpoch =
                    syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(epoch);
              }
              return combinedChainDataClient.getStateAtSlotExact(
                  spec.computeStartSlotAtEpoch(requiredEpoch));
            });
  }

  private SyncCommitteeDuties getSyncCommitteeDutiesFromIndicesAndState(
      final Optional<BeaconState> maybeState,
      final UInt64 epoch,
      final IntCollection validatorIndices) {
    if (maybeState.isEmpty()) {
      return new SyncCommitteeDuties(combinedChainDataClient.isChainHeadOptimistic(), List.of());
    }
    final BeaconState state = maybeState.get();
    return new SyncCommitteeDuties(
        combinedChainDataClient.isChainHeadOptimistic(),
        validatorIndices
            .intStream()
            .mapToObj(validatorIndex -> getSyncCommitteeDuty(state, epoch, validatorIndex))
            .flatMap(Optional::stream)
            .toList());
  }

  private Optional<SyncCommitteeDuty> getSyncCommitteeDuty(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    final Optional<SyncCommitteeUtil> syncCommitteeUtil =
        spec.atEpoch(epoch).getSyncCommitteeUtil();
    final IntSet duties =
        syncCommitteeUtil
            .map(util -> util.getCommitteeIndices(state, epoch, UInt64.valueOf(validatorIndex)))
            .orElse(IntSets.emptySet());

    if (duties.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new SyncCommitteeDuty(
            state.getValidators().get(validatorIndex).getPublicKey(), validatorIndex, duties));
  }

  private List<ProposerDuty> getProposalSlotsForEpoch(final BeaconState state, final UInt64 epoch) {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 startSlot = epochStartSlot.max(GENESIS_SLOT.increment());
    final UInt64 endSlot = epochStartSlot.plus(spec.slotsPerEpoch(epoch));
    final List<ProposerDuty> proposerSlots = new ArrayList<>();
    for (UInt64 slot = startSlot; slot.compareTo(endSlot) < 0; slot = slot.plus(UInt64.ONE)) {
      final int proposerIndex = spec.getBeaconProposerIndex(state, slot);
      final BLSPublicKey publicKey =
          spec.getValidatorPubKey(state, UInt64.valueOf(proposerIndex)).orElseThrow();
      proposerSlots.add(new ProposerDuty(publicKey, proposerIndex, slot));
    }
    return proposerSlots;
  }

  private boolean isLocallyCreatedBlock(final SignedBlockContainer signedBlockContainer) {
    final SafeFuture<Optional<BlockContainerAndMetaData>> localBlockProduction =
        blockProductionBySlotCache.get(signedBlockContainer.getSlot());
    if (localBlockProduction == null || !localBlockProduction.isCompletedNormally()) {
      return false;
    }
    return localBlockProduction
        .getImmediately()
        .map(
            blockContainerAndMetaData ->
                blockContainerAndMetaData
                    .blockContainer()
                    .getRoot()
                    .equals(signedBlockContainer.getRoot()))
        .orElse(false);
  }

  private record BlockProductionPreparationContext(
      SafeFuture<BeaconState> stateFuture,
      SafeFuture<ChainHead> chainHeadFuture,
      BlockProductionPerformance blockProductionPerformance) {

    SafeFuture<BlockProductionContext> toBlockProductionContext(
        final Spec spec,
        final UInt64 proposalSlot,
        final BLSSignature randaoReveal,
        final Optional<Bytes32> graffiti,
        final boolean includePayload,
        final Optional<BuilderConfig> builderConfig) {
      return stateFuture.thenCombine(
          chainHeadFuture,
          (state, chainHead) ->
              BlockProductionContext.create(
                  spec,
                  proposalSlot,
                  state,
                  chainHead,
                  randaoReveal,
                  graffiti,
                  includePayload,
                  builderConfig,
                  blockProductionPerformance));
    }
  }
}

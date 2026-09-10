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

package tech.pegasys.teku.validator.remote;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.BlockRootAndBuilderIndex;
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
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconNodeRequestLabels;

public class FailoverValidatorApiHandler implements ValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();

  static final String REMOTE_BEACON_NODES_REQUESTS_COUNTER_NAME =
      "remote_beacon_nodes_requests_total";

  private final Map<SlotAndBlockRoot, ValidatorApiChannel> blockCreatorCache =
      LimitedMap.createSynchronizedLRU(2);
  private final Map<UInt64, ValidatorApiChannel> executionPayloadBidCreatorCache =
      LimitedMap.createSynchronizedLRU(2);
  private final Map<BlockRootAndBuilderIndex, ValidatorApiChannel>
      executionPayloadEnvelopeCreatorCache = LimitedMap.createSynchronizedLRU(2);

  private final BeaconNodeReadinessManager beaconNodeReadinessManager;
  private final RemoteValidatorApiChannel primaryDelegate;
  private final List<? extends RemoteValidatorApiChannel> failoverDelegates;
  private final boolean failoversSendSubnetSubscriptions;
  private final boolean failoversPublishSignedDuties;
  private final LabelledMetric<Counter> failoverBeaconNodesRequestsCounter;

  public FailoverValidatorApiHandler(
      final BeaconNodeReadinessManager beaconNodeReadinessManager,
      final RemoteValidatorApiChannel primaryDelegate,
      final List<? extends RemoteValidatorApiChannel> failoverDelegates,
      final boolean failoversSendSubnetSubscriptions,
      final boolean failoversPublishSignedDuties,
      final MetricsSystem metricsSystem) {
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
    this.primaryDelegate = primaryDelegate;
    this.failoverDelegates = failoverDelegates;
    this.failoversSendSubnetSubscriptions = failoversSendSubnetSubscriptions;
    this.failoversPublishSignedDuties = failoversPublishSignedDuties;
    failoverBeaconNodesRequestsCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            REMOTE_BEACON_NODES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests sent to the configured Beacon Nodes endpoint(s)",
            "endpoint",
            "method",
            "outcome");
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return tryRequestUntilSuccess(
        ValidatorApiChannel::getGenesisData, BeaconNodeRequestLabels.GET_GENESIS_METHOD);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorIndices(publicKeys),
        BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, StateValidatorData>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorStatuses(validatorIdentifiers),
        BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(
      final UInt64 epoch, final boolean isFuluCompatible) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getProposerDuties(epoch, isFuluCompatible),
        BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD);
  }

  @Override
  public SafeFuture<Optional<PtcDuties>> getPtcDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getPtcDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_PTC_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<PeerCount>> getPeerCount() {
    return tryRequestUntilSuccess(
        ValidatorApiChannel::getPeerCount, BeaconNodeRequestLabels.GET_PEER_COUNT_METHOD);
  }

  @Override
  public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig) {
    final ValidatorApiChannelRequest<Optional<BlockContainerAndMetaData>> request =
        apiChannel ->
            apiChannel
                .createUnsignedBlock(slot, randaoReveal, graffiti, includePayload, builderConfig)
                .thenPeek(
                    maybeBlockContainerAndMetaData -> {
                      if (failoverDelegates.isEmpty()) {
                        return;
                      }
                      maybeBlockContainerAndMetaData.ifPresent(
                          blockContainerAndMetaData -> {
                            final BeaconBlock block =
                                blockContainerAndMetaData.blockContainer().getBlock();
                            // we cache the BN which created the block in following cases:
                            // pre-Gloas -> block is blinded
                            // post-Gloas -> payload is self-built
                            if (block.isBlinded() || isPayloadSelfBuilt(block)) {
                              blockCreatorCache.put(block.getSlotAndBlockRoot(), apiChannel);
                            }
                          });
                    });
    return tryRequestUntilSuccess(request, BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD);
  }

  private boolean isPayloadSelfBuilt(final BeaconBlock block) {
    return block
        .getBody()
        .getOptionalSignedExecutionPayloadBid()
        .map(bid -> bid.getMessage().getBuilderIndex().equals(BUILDER_INDEX_SELF_BUILD))
        .orElse(false);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAttestationData(slot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAggregate(slot, attestationHashTreeRoot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return tryRequestUntilSuccess(
        apiChannel ->
            apiChannel.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD);
  }

  @Override
  public SafeFuture<Optional<PayloadAttestationData>> createPayloadAttestationData(
      final UInt64 slot) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createPayloadAttestationData(slot),
        BeaconNodeRequestLabels.CREATE_PAYLOAD_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToBeaconCommittee(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToSyncCommitteeSubnets(subscriptions),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToPersistentSubnets(subnetSubscriptions),
        BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedAttestations(attestations),
        BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendAggregateAndProofs(aggregateAndProofs),
        BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final SlotAndBlockRoot slotAndBlockRoot = blockContainer.getSignedBlock().getSlotAndBlockRoot();
    // when block is blinded, we need to send it only to the BN which would be able to unblind it
    if (blockContainer.isBlinded() && blockCreatorCache.containsKey(slotAndBlockRoot)) {
      final ValidatorApiChannel blockCreatorApiChannel = blockCreatorCache.remove(slotAndBlockRoot);
      LOG.info(
          "Block for slot {} and root {} was blinded and will only be sent to the beacon node which created it.",
          slotAndBlockRoot.getSlot(),
          slotAndBlockRoot.getBlockRoot().toHexString());
      return blockCreatorApiChannel.sendSignedBlock(blockContainer, broadcastValidationLevel);
    }
    return relayRequest(
        apiChannel -> apiChannel.sendSignedBlock(blockContainer, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return relayRequest(
        apiChannel -> apiChannel.sendSyncCommitteeMessages(syncCommitteeMessages),
        BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedContributionAndProofs(signedContributionAndProofs),
        BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendPayloadAttestationMessages(
      final List<PayloadAttestationMessage> payloadAttestationMessages) {
    return relayRequest(
        apiChannel -> apiChannel.sendPayloadAttestationMessages(payloadAttestationMessages),
        BeaconNodeRequestLabels.SEND_PAYLOAD_ATTESTATION_MESSAGES_METHOD,
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedProposerPreferences(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedProposerPreferences(signedProposerPreferences),
        BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD);
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return relayRequest(
        apiChannel -> apiChannel.prepareBeaconProposer(beaconPreparableProposers),
        BeaconNodeRequestLabels.PREPARE_BEACON_PROPOSERS_METHOD);
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return relayRequest(
        apiChannel -> apiChannel.registerValidators(validatorRegistrations),
        BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD);
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorsLiveness(validatorIndices, epoch),
        BeaconNodeRequestLabels.GET_VALIDATORS_LIVENESS);
  }

  @Override
  public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> requests) {
    return relayRequest(
        apiChannel -> apiChannel.getBeaconCommitteeSelectionProof(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SELECTIONS);
  }

  @Override
  public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> requests) {
    return relayRequest(
        apiChannel -> apiChannel.getSyncCommitteeSelectionProof(requests),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SELECTIONS);
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadBid>> createUnsignedExecutionPayloadBid(
      final UInt64 slot, final UInt64 builderIndex) {
    return tryRequestUntilSuccess(
        apiChannel ->
            apiChannel
                .createUnsignedExecutionPayloadBid(slot, builderIndex)
                .thenPeek(
                    bid -> {
                      if (!failoverDelegates.isEmpty() && bid.isPresent()) {
                        executionPayloadBidCreatorCache.put(slot, apiChannel);
                      }
                    }),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_EXECUTION_PAYLOAD_BID_METHOD);
  }

  @Override
  public SafeFuture<Void> publishSignedExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    return relayRequest(
        apiChannel -> apiChannel.publishSignedExecutionPayloadBid(signedExecutionPayloadBid),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_BID_METHOD);
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    // in case one of the BNs have created the bid (when Teku VC is configured to be a builder), we
    // want to point only to this BN
    if (executionPayloadBidCreatorCache.containsKey(slot)) {
      LOG.info(
          "Execution payload for slot {} and block {} would be created only by the beacon node which created the bid.",
          slot,
          beaconBlockRoot);
      return createUnsignedExecutionPayload(
          executionPayloadBidCreatorCache.remove(slot), slot, beaconBlockRoot);
    }
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(slot, beaconBlockRoot);
    // in case the payload is self-built, we want to point only to the BN which created the block
    if (blockCreatorCache.containsKey(slotAndBlockRoot)) {
      return createUnsignedExecutionPayload(
          blockCreatorCache.remove(slotAndBlockRoot), slot, beaconBlockRoot);
    }
    return tryRequestUntilSuccess(
        apiChannel -> createUnsignedExecutionPayload(apiChannel, slot, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_EXECUTION_PAYLOAD_METHOD);
  }

  private SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
      final ValidatorApiChannel apiChannel, final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return apiChannel
        .createUnsignedExecutionPayload(slot, beaconBlockRoot)
        .thenPeek(
            maybeExecutionPayloadEnvelope -> {
              if (!failoverDelegates.isEmpty()) {
                maybeExecutionPayloadEnvelope.ifPresent(
                    executionPayloadEnvelope ->
                        executionPayloadEnvelopeCreatorCache.put(
                            executionPayloadEnvelope.getBlockRootAndBuilderIndex(), apiChannel));
              }
            });
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final BlockRootAndBuilderIndex blockRootAndBuilderIndex =
        signedExecutionPayload.getBlockRootAndBuilderIndex();
    if (executionPayloadEnvelopeCreatorCache.containsKey(blockRootAndBuilderIndex)) {
      final ValidatorApiChannel executionPayloadCreatorApiChannel =
          executionPayloadEnvelopeCreatorCache.remove(blockRootAndBuilderIndex);
      LOG.info(
          "Execution payload for block root {} and builder index {} will only be sent to the beacon node which created it.",
          blockRootAndBuilderIndex.blockRoot().toHexString(),
          blockRootAndBuilderIndex.builderIndex());
      return executionPayloadCreatorApiChannel.publishSignedExecutionPayload(
          signedExecutionPayload, broadcastValidationLevel);
    }
    return relayRequest(
        apiChannel ->
            apiChannel.publishSignedExecutionPayload(
                signedExecutionPayload, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return relayRequest(
        apiChannel ->
            apiChannel.publishSignedExecutionPayload(
                signedExecutionPayloadEnvelopeContents, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_METHOD);
  }

  private <T> SafeFuture<T> relayRequest(
      final ValidatorApiChannelRequest<T> request, final String method) {
    return relayRequest(request, method, true);
  }

  /**
   * Relays the given request to the primary Beacon Node along with all failover Beacon Node
   * endpoints if relayRequestToFailovers flag is true. If there are failovers configured, the
   * request to the primary Beacon Node will be skipped if the {@link BeaconNodeReadinessManager}
   * marked it as NOT ready.The returned {@link SafeFuture} will complete with the response from the
   * primary Beacon Node or in case in failure or if the primary node is NOT ready, it will complete
   * with the first successful response from a failover node. The returned {@link SafeFuture} will
   * only complete exceptionally when the request to the primary Beacon Node and all the requests to
   * the failover nodes fail. In this case, the returned {@link SafeFuture} will complete
   * exceptionally with a {@link FailoverRequestException}.
   */
  private <T> SafeFuture<T> relayRequest(
      final ValidatorApiChannelRequest<T> request,
      final String method,
      final boolean relayRequestToFailovers) {
    if (failoverDelegates.isEmpty() || !relayRequestToFailovers) {
      return runPrimaryRequest(request, method);
    }
    final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions = new ConcurrentHashMap<>();
    final List<SafeFuture<T>> failoverResponses =
        failoverDelegates.stream()
            .filter(beaconNodeReadinessManager::isReady)
            .map(
                failover ->
                    runRequest(failover, request, method)
                        .catchAndRethrow(throwable -> capturedExceptions.put(failover, throwable)))
            .toList();
    if (!beaconNodeReadinessManager.isReady(primaryDelegate)) {
      LOG.debug(
          "Remote request ({}) will NOT be sent to the primary Beacon Node {} because it is NOT ready. Will try to use a response from a failover.",
          method,
          primaryDelegate.getEndpoint());
      return getFirstSuccessfulResponseFromFailovers(failoverResponses, method, capturedExceptions);
    }
    return runPrimaryRequest(request, method)
        .exceptionallyCompose(
            primaryThrowable -> {
              capturedExceptions.put(primaryDelegate, primaryThrowable);
              LOG.debug(
                  "Remote request ({}) which is sent to all configured Beacon Node endpoints failed on the primary Beacon Node {}. Will try to use a response from a failover.",
                  method,
                  primaryDelegate.getEndpoint());
              return getFirstSuccessfulResponseFromFailovers(
                  failoverResponses, method, capturedExceptions);
            });
  }

  private <T> SafeFuture<T> getFirstSuccessfulResponseFromFailovers(
      final List<SafeFuture<T>> failoverResponses,
      final String method,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    return SafeFuture.firstSuccess(failoverResponses)
        .exceptionallyCompose(
            __ -> {
              final FailoverRequestException failoverRequestException =
                  new FailoverRequestException(method, capturedExceptions);
              return SafeFuture.failedFuture(failoverRequestException);
            })
        .thenPeek(
            __ ->
                LOG.debug(
                    "Received a successful response from a failover for remote request ({})",
                    method));
  }

  /**
   * Tries the given request first with the primary Beacon Node. If there are failovers configured,
   * the request to the primary Beacon Node will be skipped if the {@link
   * BeaconNodeReadinessManager} marked it as NOT ready. If the request to the primary Beacon Node
   * fails, the request will be retried against each failover Beacon Node in order of readiness
   * determined by the {@link BeaconNodeReadinessManager} until there is a successful response. In
   * case all the requests fail, the returned {@link SafeFuture} will complete exceptionally with a
   * {@link FailoverRequestException}.
   */
  private <T> SafeFuture<T> tryRequestUntilSuccess(
      final ValidatorApiChannelRequest<T> request, final String method) {
    if (failoverDelegates.isEmpty()) {
      return runPrimaryRequest(request, method);
    }
    if (!beaconNodeReadinessManager.isReady(primaryDelegate)) {
      LOG.debug(
          "Remote request ({}) will NOT be sent to the primary Beacon Node {} because it is NOT ready. Will try sending the request to one of the configured failovers.",
          method,
          primaryDelegate.getEndpoint());
      return makeRequestToFailoversUntilSuccess(request, method, new HashMap<>());
    }
    return runPrimaryRequest(request, method)
        .exceptionallyCompose(
            throwable -> {
              LOG.debug(
                  "Remote request ({}) to the primary Beacon Node {} failed. Will try sending the request to one of the configured failovers.",
                  method,
                  primaryDelegate.getEndpoint());
              final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions = new HashMap<>();
              capturedExceptions.put(primaryDelegate, throwable);
              return makeRequestToFailoversUntilSuccess(request, method, capturedExceptions);
            });
  }

  private <T> SafeFuture<T> makeRequestToFailoversUntilSuccess(
      final ValidatorApiChannelRequest<T> request,
      final String method,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    final Iterator<? extends RemoteValidatorApiChannel> failoverDelegates =
        beaconNodeReadinessManager.getFailoversInOrderOfReadiness();
    return makeRequestToFailoversUntilSuccess(
        failoverDelegates.next(), failoverDelegates, request, method, capturedExceptions);
  }

  private <T> SafeFuture<T> makeRequestToFailoversUntilSuccess(
      final RemoteValidatorApiChannel currentFailoverDelegate,
      final Iterator<? extends RemoteValidatorApiChannel> failoverDelegates,
      final ValidatorApiChannelRequest<T> request,
      final String method,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    final SafeFuture<T> response = runRequest(currentFailoverDelegate, request, method);
    return response
        .exceptionallyCompose(
            throwable -> {
              capturedExceptions.put(currentFailoverDelegate, throwable);
              if (!failoverDelegates.hasNext()) {
                final FailoverRequestException failoverRequestException =
                    new FailoverRequestException(method, capturedExceptions);
                return SafeFuture.failedFuture(failoverRequestException);
              }
              final RemoteValidatorApiChannel nextFailoverDelegate = failoverDelegates.next();
              LOG.debug(
                  "Remote request ({}) to a failover Beacon Node {} failed. Will try sending the request to another failover {}",
                  method,
                  currentFailoverDelegate.getEndpoint(),
                  nextFailoverDelegate.getEndpoint());
              return makeRequestToFailoversUntilSuccess(
                  nextFailoverDelegate, failoverDelegates, request, method, capturedExceptions);
            })
        .thenPeek(
            __ ->
                LOG.debug(
                    "Remote request ({}) succeeded using a failover Beacon Node {}",
                    method,
                    currentFailoverDelegate.getEndpoint()));
  }

  private <T> SafeFuture<T> runPrimaryRequest(
      final ValidatorApiChannelRequest<T> request, final String method) {
    return runRequest(primaryDelegate, request, method);
  }

  private <T> SafeFuture<T> runRequest(
      final RemoteValidatorApiChannel delegate,
      final ValidatorApiChannelRequest<T> request,
      final String method) {
    LOG.trace("runRequest {} to {}", method, delegate.getEndpoint());
    return request
        .run(delegate)
        .handleComposed(
            (response, throwable) -> {
              if (throwable != null) {
                LOG.debug("Request ({}) to {} failed", method, delegate.getEndpoint(), throwable);
                recordFailedRequest(delegate, method);
                return SafeFuture.failedFuture(throwable);
              }
              recordSuccessfulRequest(delegate, method);
              return SafeFuture.completedFuture(response);
            });
  }

  private void recordSuccessfulRequest(
      final RemoteValidatorApiChannel failover, final String method) {
    recordRequest(failover, method, RequestOutcome.SUCCESS);
  }

  private void recordFailedRequest(final RemoteValidatorApiChannel failover, final String method) {
    recordRequest(failover, method, RequestOutcome.ERROR);
  }

  private void recordRequest(
      final RemoteValidatorApiChannel failover, final String method, final RequestOutcome outcome) {
    failoverBeaconNodesRequestsCounter
        .labels(failover.getEndpoint().toString(), method, outcome.displayName)
        .inc();
  }

  @VisibleForTesting
  @FunctionalInterface
  interface ValidatorApiChannelRequest<T> {
    SafeFuture<T> run(final ValidatorApiChannel apiChannel);
  }

  enum RequestOutcome {
    SUCCESS("success"),
    ERROR("error");

    private final String displayName;

    RequestOutcome(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}

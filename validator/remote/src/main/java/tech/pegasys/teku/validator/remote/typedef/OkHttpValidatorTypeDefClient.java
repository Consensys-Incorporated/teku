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

package tech.pegasys.teku.validator.remote.typedef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.handlers.BeaconCommitteeSelectionsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateAggregateAttestationRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateAttestationDataRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreatePayloadAttestationDataRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateSyncCommitteeContributionRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetExecutionPayloadEnvelopeRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetPeerCountRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetProposerDutiesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetProposerDutiesV2Request;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetStateValidatorsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetSyncingStatusRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostAttesterDutiesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostPtcDutiesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostSyncDutiesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PrepareBeaconProposersRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.ProduceBlockRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PublishSignedExecutionPayloadRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendAggregateAndProofsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendContributionAndProofsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendPayloadAttestationMessagesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSignedAttestationsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSignedBlockRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSignedProposerPreferencesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSubscribeToSyncCommitteeSubnetsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSyncCommitteeMessagesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendValidatorLivenessRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SubscribeToBeaconCommitteeRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SubscribeToPersistentSubnetsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SyncCommitteeSelectionsRequest;

public class OkHttpValidatorTypeDefClient extends OkHttpValidatorMinimalTypeDefClient {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final boolean preferSszBlockEncoding;
  private final SchemaDefinitionCache schemaDefinitionCache;
  private final boolean attestationsV2ApisEnabled;

  public OkHttpValidatorTypeDefClient(
      final OkHttpClient okHttpClient,
      final HttpUrl baseEndpoint,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final boolean attestationsV2ApisEnabled) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
    schemaDefinitionCache = new SchemaDefinitionCache(spec);
    this.preferSszBlockEncoding = preferSszBlockEncoding;
    this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
  }

  public SyncingStatus getSyncingStatus() {
    final GetSyncingStatusRequest getSyncingStatusRequest =
        new GetSyncingStatusRequest(getBaseEndpoint(), getOkHttpClient());
    return getSyncingStatusRequest.submit();
  }

  public Optional<ProposerDuties> getProposerDuties(final UInt64 epoch) {
    final GetProposerDutiesRequest getProposerDutiesRequest =
        new GetProposerDutiesRequest(getBaseEndpoint(), getOkHttpClient());
    return getProposerDutiesRequest.submit(epoch);
  }

  public Optional<ProposerDuties> getProposerDutiesV2(final UInt64 epoch) {
    final GetProposerDutiesV2Request getProposerDutiesV2Request =
        new GetProposerDutiesV2Request(getBaseEndpoint(), getOkHttpClient());
    return getProposerDutiesV2Request.submit(epoch);
  }

  public Optional<PeerCount> getPeerCount() {
    final GetPeerCountRequest getPeerCountRequest =
        new GetPeerCountRequest(getBaseEndpoint(), getOkHttpClient());
    return getPeerCountRequest.submit();
  }

  public Optional<List<StateValidatorData>> getStateValidators(final List<String> validatorIds) {
    final GetStateValidatorsRequest getStateValidatorsRequest =
        new GetStateValidatorsRequest(getBaseEndpoint(), getOkHttpClient());
    return getStateValidatorsRequest.submit(validatorIds).map(ObjectAndMetaData::getData);
  }

  public Optional<SyncCommitteeDuties> postSyncDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    final PostSyncDutiesRequest postSyncDutiesRequest =
        new PostSyncDutiesRequest(getBaseEndpoint(), getOkHttpClient());
    return postSyncDutiesRequest.submit(epoch, validatorIndices);
  }

  public Optional<AttesterDuties> postAttesterDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    final PostAttesterDutiesRequest postAttesterDutiesRequest =
        new PostAttesterDutiesRequest(getBaseEndpoint(), getOkHttpClient());
    return postAttesterDutiesRequest.submit(epoch, validatorIndices);
  }

  public Optional<PtcDuties> postPtcDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    final PostPtcDutiesRequest postPtcDutiesRequest =
        new PostPtcDutiesRequest(getBaseEndpoint(), getOkHttpClient());
    return postPtcDutiesRequest.submit(epoch, validatorIndices);
  }

  public SendSignedBlockResult sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final SendSignedBlockRequest sendSignedBlockRequest =
        new SendSignedBlockRequest(
            spec, getBaseEndpoint(), getOkHttpClient(), preferSszBlockEncoding);
    return sendSignedBlockRequest.submit(blockContainer, broadcastValidationLevel);
  }

  public Optional<BlockContainerAndMetaData> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> maybeBuilderConfig) {
    final ProduceBlockRequest produceBlockRequest =
        new ProduceBlockRequest(
            getBaseEndpoint(),
            getOkHttpClient(),
            schemaDefinitionCache,
            slot,
            preferSszBlockEncoding);
    final SpecMilestone milestone = schemaDefinitionCache.milestoneAtSlot(slot);
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      // BuilderConfig is required for block v4
      final BuilderConfig builderConfig =
          maybeBuilderConfig.orElseThrow(
              () ->
                  new NoSuchElementException(
                      "BuilderConfig is expected to have been provided for a block v4 request"));
      return produceBlockRequest.submitV4(
          randaoReveal, graffiti, includePayload, builderConfig, milestone);
    }
    return produceBlockRequest.submitV3(
        randaoReveal, graffiti, maybeBuilderConfig.map(BuilderConfig::getBuilderBoostFactor));
  }

  public void registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    final RegisterValidatorsRequest registerValidatorsRequest =
        new RegisterValidatorsRequest(getBaseEndpoint(), getOkHttpClient(), false);
    registerValidatorsRequest.submit(validatorRegistrations);
  }

  public Optional<AttestationData> createAttestationData(
      final UInt64 slot, final int committeeIndex) {

    final CreateAttestationDataRequest createAttestationDataRequest =
        new CreateAttestationDataRequest(getBaseEndpoint(), getOkHttpClient());
    return createAttestationDataRequest.submit(slot, committeeIndex);
  }

  public Optional<PayloadAttestationData> createPayloadAttestationData(final UInt64 slot) {
    final CreatePayloadAttestationDataRequest createPayloadAttestationDataRequest =
        new CreatePayloadAttestationDataRequest(getBaseEndpoint(), getOkHttpClient(), spec);
    return createPayloadAttestationDataRequest.submit(slot);
  }

  public Optional<ExecutionPayloadEnvelope> getExecutionPayloadEnvelope(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return new GetExecutionPayloadEnvelopeRequest(getBaseEndpoint(), getOkHttpClient(), spec)
        .submit(slot, beaconBlockRoot);
  }

  public Optional<List<BeaconCommitteeSelectionProof>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> validatorsPartialProofs) {

    final BeaconCommitteeSelectionsRequest beaconCommitteeSelectionsRequest =
        new BeaconCommitteeSelectionsRequest(getBaseEndpoint(), getOkHttpClient());
    return beaconCommitteeSelectionsRequest.submit(validatorsPartialProofs);
  }

  public Optional<List<SyncCommitteeSelectionProof>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> validatorsPartialProofs) {

    final SyncCommitteeSelectionsRequest syncCommitteeSelectionsRequest =
        new SyncCommitteeSelectionsRequest(getBaseEndpoint(), getOkHttpClient());
    return syncCommitteeSelectionsRequest.submit(validatorsPartialProofs);
  }

  public void subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    LOG.debug("Subscribing to sync committee subnets {}", subscriptions);

    final SendSubscribeToSyncCommitteeSubnetsRequest subscribeToSyncCommitteeSubnetsRequest =
        new SendSubscribeToSyncCommitteeSubnetsRequest(getBaseEndpoint(), getOkHttpClient());
    subscribeToSyncCommitteeSubnetsRequest.submit(subscriptions);
  }

  public Optional<SyncCommitteeContribution> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    LOG.debug(
        "Create Sync committee contribution for slot {}, subcommitteeIndex {}, root {}",
        slot,
        subcommitteeIndex,
        beaconBlockRoot);

    final CreateSyncCommitteeContributionRequest createSyncCommitteeContributionRequest =
        new CreateSyncCommitteeContributionRequest(getBaseEndpoint(), getOkHttpClient(), spec);
    return createSyncCommitteeContributionRequest.submit(slot, subcommitteeIndex, beaconBlockRoot);
  }

  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    final SubscribeToPersistentSubnetsRequest subscribeToPersistentSubnetsRequest =
        new SubscribeToPersistentSubnetsRequest(getBaseEndpoint(), getOkHttpClient());
    subscribeToPersistentSubnetsRequest.submit(new ArrayList<>(subnetSubscriptions));
  }

  public void sendContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {

    final SendContributionAndProofsRequest sendContributionAndProofsRequest =
        new SendContributionAndProofsRequest(getBaseEndpoint(), getOkHttpClient());
    sendContributionAndProofsRequest.submit(signedContributionAndProofs);
  }

  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> subscriptions) {
    final SubscribeToBeaconCommitteeRequest subscribeToBeaconCommitteeRequest =
        new SubscribeToBeaconCommitteeRequest(getBaseEndpoint(), getOkHttpClient());
    subscribeToBeaconCommitteeRequest.submit(subscriptions);
  }

  public void prepareBeaconProposer(
      final List<BeaconPreparableProposer> beaconPreparableProposers) {
    final PrepareBeaconProposersRequest prepareBeaconProposersRequest =
        new PrepareBeaconProposersRequest(getBaseEndpoint(), getOkHttpClient());
    prepareBeaconProposersRequest.submit(beaconPreparableProposers);
  }

  public Optional<ObjectAndMetaData<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    final CreateAggregateAttestationRequest createAggregateAttestationRequest =
        new CreateAggregateAttestationRequest(
            getBaseEndpoint(),
            getOkHttpClient(),
            schemaDefinitionCache,
            slot,
            attestationHashTreeRoot,
            committeeIndex,
            attestationsV2ApisEnabled,
            spec);
    return createAggregateAttestationRequest.submit();
  }

  public Optional<List<ValidatorLivenessAtEpoch>> sendValidatorsLiveness(
      final UInt64 epoch, final List<UInt64> validatorIndices) {
    final SendValidatorLivenessRequest sendValidatorLivenessRequest =
        new SendValidatorLivenessRequest(getBaseEndpoint(), getOkHttpClient());
    return sendValidatorLivenessRequest.submit(epoch, validatorIndices);
  }

  public List<SubmitDataError> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    final SendSyncCommitteeMessagesRequest sendSyncCommitteeMessagesRequest =
        new SendSyncCommitteeMessagesRequest(getBaseEndpoint(), getOkHttpClient());
    return sendSyncCommitteeMessagesRequest.submit(syncCommitteeMessages);
  }

  public List<SubmitDataError> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    final SendAggregateAndProofsRequest sendAggregateAndProofsRequest =
        new SendAggregateAndProofsRequest(
            getBaseEndpoint(), getOkHttpClient(), attestationsV2ApisEnabled, spec);
    return sendAggregateAndProofsRequest.submit(aggregateAndProofs);
  }

  public List<SubmitDataError> sendSignedAttestations(final List<Attestation> attestations) {
    final SendSignedAttestationsRequest sendSignedAttestationsRequest =
        new SendSignedAttestationsRequest(
            getBaseEndpoint(), getOkHttpClient(), attestationsV2ApisEnabled, spec);
    return sendSignedAttestationsRequest.submit(attestations);
  }

  public List<SubmitDataError> sendPayloadAttestationMessages(
      final List<PayloadAttestationMessage> payloadAttestationMessages) {
    final SendPayloadAttestationMessagesRequest sendPayloadAttestationMessagesRequest =
        new SendPayloadAttestationMessagesRequest(getBaseEndpoint(), getOkHttpClient());
    return sendPayloadAttestationMessagesRequest.submit(payloadAttestationMessages);
  }

  public List<SubmitDataError> sendSignedProposerPreferences(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    final SendSignedProposerPreferencesRequest sendSignedProposerPreferencesRequest =
        new SendSignedProposerPreferencesRequest(getBaseEndpoint(), getOkHttpClient());
    return sendSignedProposerPreferencesRequest.submit(signedProposerPreferences);
  }

  public PublishSignedExecutionPayloadResult publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final PublishSignedExecutionPayloadRequest publishSignedExecutionPayloadRequest =
        new PublishSignedExecutionPayloadRequest(spec, getBaseEndpoint(), getOkHttpClient());
    return publishSignedExecutionPayloadRequest.submit(
        signedExecutionPayload, broadcastValidationLevel);
  }

  public PublishSignedExecutionPayloadResult publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final PublishSignedExecutionPayloadRequest publishSignedExecutionPayloadRequest =
        new PublishSignedExecutionPayloadRequest(spec, getBaseEndpoint(), getOkHttpClient());
    return publishSignedExecutionPayloadRequest.submit(
        signedExecutionPayloadEnvelopeContents, broadcastValidationLevel);
  }
}

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

package tech.pegasys.teku.validator.beaconnode.metrics;

import static tech.pegasys.teku.infrastructure.metrics.Validator.DutyType.ATTESTATION_PRODUCTION;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils.startTimer;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.SEND;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
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
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
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
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class MetricRecordingValidatorApiChannel implements ValidatorApiChannel {

  static final String BEACON_NODE_REQUESTS_COUNTER_NAME = "beacon_node_requests_total";

  private final ValidatorApiChannel delegate;
  private final LabelledMetric<Counter> beaconNodeRequestsCounter;
  private final LabelledMetric<OperationTimer> dutyTimer;

  public MetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final ValidatorApiChannel delegate) {
    this.delegate = delegate;
    beaconNodeRequestsCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            BEACON_NODE_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests sent to the beacon node",
            "method",
            "outcome");
    dutyTimer = ValidatorDutyMetricUtils.createValidatorDutyMetric(metricsSystem);
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return countOptionalDataRequest(
        delegate.getGenesisData(), BeaconNodeRequestLabels.GET_GENESIS_METHOD);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return countDataRequest(
        delegate.getValidatorIndices(publicKeys),
        BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, StateValidatorData>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return countOptionalDataRequest(
        delegate.getValidatorStatuses(validatorIdentifiers),
        BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return countOptionalDataRequest(
        delegate.getAttestationDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return countOptionalDataRequest(
        delegate.getSyncCommitteeDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(
      final UInt64 epoch, final boolean isFuluCompatible) {
    return countOptionalDataRequest(
        delegate.getProposerDuties(epoch, isFuluCompatible),
        BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD);
  }

  @Override
  public SafeFuture<Optional<PtcDuties>> getPtcDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return countOptionalDataRequest(
        delegate.getPtcDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_PTC_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<PeerCount>> getPeerCount() {
    return countOptionalDataRequest(
        delegate.getPeerCount(), BeaconNodeRequestLabels.GET_PEER_COUNT_METHOD);
  }

  @Override
  public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig) {
    return countOptionalDataRequest(
        delegate.createUnsignedBlock(slot, randaoReveal, graffiti, includePayload, builderConfig),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return countOptionalDataRequest(
        delegate.createAttestationData(slot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    return countOptionalDataRequest(
        delegate.createAggregate(slot, attestationHashTreeRoot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return countOptionalDataRequest(
        delegate.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD);
  }

  @Override
  public SafeFuture<Optional<PayloadAttestationData>> createPayloadAttestationData(
      final UInt64 slot) {
    return countOptionalDataRequest(
        delegate.createPayloadAttestationData(slot),
        BeaconNodeRequestLabels.CREATE_PAYLOAD_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return countDataRequest(
        delegate.subscribeToBeaconCommittee(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return countDataRequest(
        delegate.subscribeToSyncCommitteeSubnets(subscriptions),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return countDataRequest(
        delegate.subscribeToPersistentSubnets(subnetSubscriptions),
        BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    // we are in an async context, don't follow the AutoClose pattern
    final OperationTimer.TimingContext context =
        startTimer(dutyTimer, ATTESTATION_PRODUCTION.getName(), SEND.getName());
    final SafeFuture<List<SubmitDataError>> request =
        delegate.sendSignedAttestations(attestations).alwaysRun(context::stopTimer);
    return countSendRequest(request, BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return countSendRequest(
        delegate.sendAggregateAndProofs(aggregateAndProofs),
        BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return countDataRequest(
        delegate.sendSignedBlock(blockContainer, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return countSendRequest(
        delegate.sendSyncCommitteeMessages(syncCommitteeMessages),
        BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return countDataRequest(
        delegate.sendSignedContributionAndProofs(signedContributionAndProofs),
        BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendPayloadAttestationMessages(
      final List<PayloadAttestationMessage> payloadAttestationMessages) {
    return countSendRequest(
        delegate.sendPayloadAttestationMessages(payloadAttestationMessages),
        BeaconNodeRequestLabels.SEND_PAYLOAD_ATTESTATION_MESSAGES_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedProposerPreferences(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    return countSendRequest(
        delegate.sendSignedProposerPreferences(signedProposerPreferences),
        BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD);
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return countDataRequest(
        delegate.prepareBeaconProposer(beaconPreparableProposers),
        BeaconNodeRequestLabels.PREPARE_BEACON_PROPOSERS_METHOD);
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return countDataRequest(
        delegate.registerValidators(validatorRegistrations),
        BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD);
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return countOptionalDataRequest(
        delegate.getValidatorsLiveness(validatorIndices, epoch),
        BeaconNodeRequestLabels.GET_VALIDATORS_LIVENESS);
  }

  @Override
  public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> requests) {
    return countOptionalDataRequest(
        delegate.getBeaconCommitteeSelectionProof(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SELECTIONS);
  }

  @Override
  public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> requests) {
    return countOptionalDataRequest(
        delegate.getSyncCommitteeSelectionProof(requests),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SELECTIONS);
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadBid>> createUnsignedExecutionPayloadBid(
      final UInt64 slot, final UInt64 builderIndex) {
    return countOptionalDataRequest(
        delegate.createUnsignedExecutionPayloadBid(slot, builderIndex),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_EXECUTION_PAYLOAD_BID_METHOD);
  }

  @Override
  public SafeFuture<Void> publishSignedExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    return countDataRequest(
        delegate.publishSignedExecutionPayloadBid(signedExecutionPayloadBid),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_BID_METHOD);
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return countOptionalDataRequest(
        delegate.createUnsignedExecutionPayload(slot, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_EXECUTION_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return countDataRequest(
        delegate.publishSignedExecutionPayload(signedExecutionPayload, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return countDataRequest(
        delegate.publishSignedExecutionPayload(
            signedExecutionPayloadEnvelopeContents, broadcastValidationLevel),
        BeaconNodeRequestLabels.PUBLISH_EXECUTION_PAYLOAD_METHOD);
  }

  private <T> SafeFuture<T> countDataRequest(
      final SafeFuture<T> request, final String requestName) {
    return request
        .catchAndRethrow(__ -> recordError(requestName))
        .thenPeek(__ -> recordSuccess(requestName));
  }

  private <T> SafeFuture<Optional<T>> countOptionalDataRequest(
      final SafeFuture<Optional<T>> request, final String requestName) {
    return request
        .catchAndRethrow(__ -> recordError(requestName))
        .thenPeek(
            result ->
                result.ifPresentOrElse(
                    __ -> recordSuccess(requestName), () -> recordDataUnavailable(requestName)));
  }

  private <T> SafeFuture<List<T>> countSendRequest(
      final SafeFuture<List<T>> request, final String requestName) {
    return request
        .catchAndRethrow(__ -> recordError(requestName))
        .thenPeek(
            result -> {
              if (result.isEmpty()) {
                recordSuccess(requestName);
              } else {
                recordError(requestName);
              }
            });
  }

  private void recordSuccess(final String requestName) {
    recordRequest(requestName, RequestOutcome.SUCCESS);
  }

  private void recordError(final String requestName) {
    recordRequest(requestName, RequestOutcome.ERROR);
  }

  private void recordDataUnavailable(final String requestName) {
    recordRequest(requestName, RequestOutcome.DATA_UNAVAILABLE);
  }

  private void recordRequest(final String name, final RequestOutcome outcome) {
    beaconNodeRequestsCounter.labels(name, outcome.displayName).inc();
  }

  enum RequestOutcome {
    SUCCESS("success"),
    ERROR("error"),
    DATA_UNAVAILABLE("data_unavailable");

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

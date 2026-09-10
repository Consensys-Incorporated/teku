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

package tech.pegasys.teku.validator.api;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
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

public interface ValidatorApiChannel extends BuilderApiChannel, ChannelInterface {
  ValidatorApiChannel NOOP =
      new ValidatorApiChannel() {
        @Override
        public SafeFuture<Optional<GenesisData>> getGenesisData() {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
            final Collection<BLSPublicKey> publicKeys) {
          return SafeFuture.completedFuture(Map.of());
        }

        @Override
        public SafeFuture<Optional<Map<BLSPublicKey, StateValidatorData>>> getValidatorStatuses(
            final Collection<BLSPublicKey> validatorIdentifiers) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
            final UInt64 epoch, final IntCollection validatorIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
            final UInt64 epoch, final IntCollection validatorIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<ProposerDuties>> getProposerDuties(
            final UInt64 epoch, final boolean isFuluCompatible) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<PtcDuties>> getPtcDuties(
            final UInt64 epoch, final IntCollection validatorIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<PeerCount>> getPeerCount() {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
            final UInt64 slot,
            final BLSSignature randaoReveal,
            final Optional<Bytes32> graffiti,
            final boolean includePayload,
            final Optional<BuilderConfig> builderConfig) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<AttestationData>> createAttestationData(
            final UInt64 slot, final int committeeIndex) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<Attestation>> createAggregate(
            final UInt64 slot,
            final Bytes32 attestationHashTreeRoot,
            final Optional<UInt64> committeeIndex) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
            final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<PayloadAttestationData>> createPayloadAttestationData(
            final UInt64 slot) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Void> subscribeToBeaconCommittee(
            final List<CommitteeSubscriptionRequest> requests) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
            final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> subscribeToPersistentSubnets(
            final Set<SubnetSubscription> subnetSubscriptions) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
            final List<Attestation> attestations) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
            final List<SignedAggregateAndProof> aggregateAndProofs) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<SendSignedBlockResult> sendSignedBlock(
            final SignedBlockContainer blockContainer,
            final BroadcastValidationLevel broadcastValidationLevel) {
          return SafeFuture.completedFuture(SendSignedBlockResult.rejected("NO OP Implementation"));
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
            final List<SyncCommitteeMessage> syncCommitteeMessages) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<Void> sendSignedContributionAndProofs(
            final Collection<SignedContributionAndProof> signedContributionAndProofs) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendPayloadAttestationMessages(
            final List<PayloadAttestationMessage> payloadAttestationMessages) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendSignedProposerPreferences(
            final List<SignedProposerPreferences> signedProposerPreferences) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<Void> prepareBeaconProposer(
            final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> registerValidators(
            final SszList<SignedValidatorRegistration> validatorRegistrations) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
            final List<UInt64> validatorIndices, final UInt64 epoch) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>>
            getBeaconCommitteeSelectionProof(final List<BeaconCommitteeSelectionProof> requests) {
          return SafeFuture.completedFuture(Optional.of(requests));
        }

        @Override
        public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>>
            getSyncCommitteeSelectionProof(final List<SyncCommitteeSelectionProof> requests) {
          return SafeFuture.completedFuture(Optional.of(requests));
        }

        @Override
        public SafeFuture<Optional<ExecutionPayloadBid>> createUnsignedExecutionPayloadBid(
            final UInt64 slot, final UInt64 builderIndex) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Void> publishSignedExecutionPayloadBid(
            final SignedExecutionPayloadBid signedExecutionPayloadBid) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
            final UInt64 slot, final Bytes32 beaconBlockRoot) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(
              PublishSignedExecutionPayloadResult.success(
                  signedExecutionPayload.getBeaconBlockRoot()));
        }

        @Override
        public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
            final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(
              PublishSignedExecutionPayloadResult.success(
                  signedExecutionPayloadEnvelopeContents.getBeaconBlockRoot()));
        }
      };

  int UNKNOWN_VALIDATOR_ID = -1;

  SafeFuture<Optional<GenesisData>> getGenesisData();

  SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<Map<BLSPublicKey, StateValidatorData>>> getValidatorStatuses(
      Collection<BLSPublicKey> validatorIdentifiers);

  SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      UInt64 epoch, IntCollection validatorIndices);

  SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      UInt64 epoch, IntCollection validatorIndices);

  SafeFuture<Optional<ProposerDuties>> getProposerDuties(UInt64 epoch, boolean isFuluCompatible);

  SafeFuture<Optional<PtcDuties>> getPtcDuties(UInt64 epoch, IntCollection validatorIndices);

  SafeFuture<Optional<PeerCount>> getPeerCount();

  // used to maintain backwards compatibility with milestones prior to Gloas
  default SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    return createUnsignedBlock(
        slot,
        randaoReveal,
        graffiti,
        false,
        requestedBuilderBoostFactor.map(BuilderConfig::withBuilderBoostFactor));
  }

  SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      UInt64 slot,
      BLSSignature randaoReveal,
      Optional<Bytes32> graffiti,
      boolean includePayload,
      Optional<BuilderConfig> builderConfig);

  SafeFuture<Optional<AttestationData>> createAttestationData(UInt64 slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(
      UInt64 slot, Bytes32 attestationHashTreeRoot, Optional<UInt64> committeeIndex);

  SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      UInt64 slot, int subcommitteeIndex, Bytes32 beaconBlockRoot);

  SafeFuture<Optional<PayloadAttestationData>> createPayloadAttestationData(UInt64 slot);

  SafeFuture<Void> subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests);

  SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions);

  SafeFuture<Void> subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations);

  SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      List<SignedAggregateAndProof> aggregateAndProofs);

  SafeFuture<SendSignedBlockResult> sendSignedBlock(
      SignedBlockContainer blockContainer, BroadcastValidationLevel broadcastValidationLevel);

  SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  SafeFuture<Void> sendSignedContributionAndProofs(
      Collection<SignedContributionAndProof> signedContributionAndProofs);

  SafeFuture<List<SubmitDataError>> sendPayloadAttestationMessages(
      List<PayloadAttestationMessage> payloadAttestationMessages);

  SafeFuture<List<SubmitDataError>> sendSignedProposerPreferences(
      List<SignedProposerPreferences> signedProposerPreferences);

  SafeFuture<Void> prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers);

  /**
   * Note that only registrations for active or pending validators must be sent to the builder
   * network. Registrations for unknown or exited validators must be filtered out and not sent to
   * the builder network.
   *
   * <p>Method expects already filtered input.
   *
   * <p>See <a
   * href="https://github.com/ethereum/beacon-APIs/blob/master/apis/validator/register_validator.yaml">validator
   * registration endpoint spec</a>
   */
  SafeFuture<Void> registerValidators(SszList<SignedValidatorRegistration> validatorRegistrations);

  SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      List<UInt64> validatorIndices, UInt64 epoch);

  SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      List<BeaconCommitteeSelectionProof> requests);

  SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      List<SyncCommitteeSelectionProof> requests);
}

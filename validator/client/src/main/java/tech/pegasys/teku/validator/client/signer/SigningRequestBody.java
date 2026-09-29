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

package tech.pegasys.teku.validator.client.signer;

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.enumOf;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.FORK_INFO;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.signer.AggregationSlotWrapper;
import tech.pegasys.teku.validator.api.signer.BlockWrapper;
import tech.pegasys.teku.validator.api.signer.RandaoRevealWrapper;
import tech.pegasys.teku.validator.api.signer.SignType;
import tech.pegasys.teku.validator.api.signer.SyncAggregatorSelectionDataWrapper;
import tech.pegasys.teku.validator.api.signer.SyncCommitteeMessageWrapper;
import tech.pegasys.teku.validator.api.signer.VersionedWrapper;

public record SigningRequestBody(Bytes signingRoot, SignType type, Map<String, Object> metadata) {
  private static final StringValueTypeDefinition<Bytes> BYTES_TYPE =
      DeserializableTypeDefinition.string(Bytes.class)
          .formatter(Bytes::toHexString)
          .parser(Bytes::fromHexString)
          .format("byte")
          .build();

  public SerializableTypeDefinition<SigningRequestBody> getJsonTypeDefinition(
      final SchemaDefinitions schemaDefinitions) {
    return SerializableTypeDefinition.object(SigningRequestBody.class)
        .withField("signingRoot", BYTES_TYPE, SigningRequestBody::signingRoot)
        .withField("type", enumOf(SignType.class), SigningRequestBody::type)
        .withOptionalField(
            SignType.VOLUNTARY_EXIT.getName(),
            VoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition(),
            SigningRequestBody::getVoluntaryExit)
        .withOptionalField(
            SignType.AGGREGATION_SLOT.getName(),
            AggregationSlotWrapper.getJsonTypeDefinition(),
            SigningRequestBody::getAggregationSlot)
        .withOptionalField(
            FORK_INFO, ForkInfo.getJsonTypeDefinition(), SigningRequestBody::getForkInfo)
        .withOptionalField(
            SignType.ATTESTATION.getName(),
            AttestationData.SSZ_SCHEMA.getJsonTypeDefinition(),
            SigningRequestBody::getAttestationData)
        .withOptionalField(
            SignType.SYNC_COMMITTEE_MESSAGE.getName(),
            SyncCommitteeMessageWrapper.getJsonTypeDefinition(),
            SigningRequestBody::getSyncCommitteeMessage)
        .withOptionalField(
            SignType.SYNC_AGGREGATOR_SELECTION_DATA.getName(),
            SyncAggregatorSelectionDataWrapper.getJsonTypefinition(),
            SigningRequestBody::getSyncAggregateSelectionData)
        .withOptionalField(
            SignType.BEACON_BLOCK.getName(),
            getBlockWrapper().map(BlockWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getBlockWrapper)
        .withOptionalField(
            SignType.VALIDATOR_REGISTRATION.getName(),
            ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.getJsonTypeDefinition(),
            SigningRequestBody::getValidatorRegistration)
        .withOptionalField(
            SignType.CONTRIBUTION_AND_PROOF.getName(),
            getContributionAndProof().map(z -> z.getSchema().getJsonTypeDefinition()).orElse(null),
            SigningRequestBody::getContributionAndProof)
        .withOptionalField(
            SignType.AGGREGATE_AND_PROOF.getName(),
            schemaDefinitions.getAggregateAndProofSchema().getJsonTypeDefinition(),
            SigningRequestBody::getAggregateAndProof)
        .withOptionalField(
            SignType.BLOCK.getName(),
            schemaDefinitions.getBeaconBlockSchema().getJsonTypeDefinition(),
            SigningRequestBody::getBlock)
        .withOptionalField(
            SignType.RANDAO_REVEAL.getName(),
            RandaoRevealWrapper.getJsonTypeDefinition(),
            SigningRequestBody::getRandaoReveal)
        .withOptionalField(
            SignType.EXECUTION_PAYLOAD_BID.getName(),
            getExecutionPayloadBid().map(VersionedWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getExecutionPayloadBid)
        .withOptionalField(
            SignType.EXECUTION_PAYLOAD_ENVELOPE.getName(),
            getExecutionPayloadEnvelope().map(VersionedWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getExecutionPayloadEnvelope)
        .withOptionalField(
            SignType.PAYLOAD_ATTESTATION_MESSAGE.getName(),
            getPayloadAttestationData().map(VersionedWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getPayloadAttestationData)
        .withOptionalField(
            SignType.PROPOSER_PREFERENCES.getName(),
            getProposerPreferences().map(VersionedWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getProposerPreferences)
        .withOptionalField(
            SignType.BUILDER_REQUEST_AUTH.getName(),
            getBuilderRequestAuth().map(VersionedWrapper::getJsonTypeDefinition).orElse(null),
            SigningRequestBody::getBuilderRequestAuth)
        .build();
  }

  private Optional<ForkInfo> getForkInfo() {
    return Optional.ofNullable((ForkInfo) metadata.get(FORK_INFO));
  }

  private Optional<VoluntaryExit> getVoluntaryExit() {
    return Optional.ofNullable((VoluntaryExit) metadata.get(SignType.VOLUNTARY_EXIT.getName()));
  }

  private Optional<ContributionAndProof> getContributionAndProof() {
    return Optional.ofNullable(
        (ContributionAndProof) metadata.get(SignType.CONTRIBUTION_AND_PROOF.getName()));
  }

  private Optional<AttestationData> getAttestationData() {
    return Optional.ofNullable((AttestationData) metadata.get(SignType.ATTESTATION.getName()));
  }

  private Optional<AggregateAndProof> getAggregateAndProof() {
    return Optional.ofNullable(
        (AggregateAndProof) metadata.get(SignType.AGGREGATE_AND_PROOF.getName()));
  }

  private Optional<BeaconBlock> getBlock() {
    return Optional.ofNullable((BeaconBlock) metadata.get(SignType.BLOCK.getName()));
  }

  private Optional<BlockWrapper> getBlockWrapper() {
    return Optional.ofNullable((BlockWrapper) metadata.get(SignType.BEACON_BLOCK.getName()));
  }

  private Optional<SyncCommitteeMessageWrapper> getSyncCommitteeMessage() {
    return Optional.ofNullable(
        (SyncCommitteeMessageWrapper) metadata.get(SignType.SYNC_COMMITTEE_MESSAGE.getName()));
  }

  private Optional<SyncAggregatorSelectionDataWrapper> getSyncAggregateSelectionData() {
    return Optional.ofNullable(
        (SyncAggregatorSelectionDataWrapper)
            metadata.get(SignType.SYNC_AGGREGATOR_SELECTION_DATA.getName()));
  }

  private Optional<ValidatorRegistration> getValidatorRegistration() {
    return Optional.ofNullable(
        (ValidatorRegistration) metadata.get(SignType.VALIDATOR_REGISTRATION.getName()));
  }

  private Optional<RandaoRevealWrapper> getRandaoReveal() {
    return Optional.ofNullable(
        (RandaoRevealWrapper) metadata.get(SignType.RANDAO_REVEAL.getName()));
  }

  private Optional<AggregationSlotWrapper> getAggregationSlot() {
    return Optional.ofNullable(
        (AggregationSlotWrapper) metadata.get(SignType.AGGREGATION_SLOT.getName()));
  }

  @SuppressWarnings("unchecked")
  private Optional<VersionedWrapper<ExecutionPayloadBid>> getExecutionPayloadBid() {
    return Optional.ofNullable(
        (VersionedWrapper<ExecutionPayloadBid>)
            metadata.get(SignType.EXECUTION_PAYLOAD_BID.getName()));
  }

  @SuppressWarnings("unchecked")
  private Optional<VersionedWrapper<ExecutionPayloadEnvelope>> getExecutionPayloadEnvelope() {
    return Optional.ofNullable(
        (VersionedWrapper<ExecutionPayloadEnvelope>)
            metadata.get(SignType.EXECUTION_PAYLOAD_ENVELOPE.getName()));
  }

  @SuppressWarnings("unchecked")
  private Optional<VersionedWrapper<PayloadAttestationData>> getPayloadAttestationData() {
    return Optional.ofNullable(
        (VersionedWrapper<PayloadAttestationData>)
            metadata.get(SignType.PAYLOAD_ATTESTATION_MESSAGE.getName()));
  }

  @SuppressWarnings("unchecked")
  private Optional<VersionedWrapper<ProposerPreferences>> getProposerPreferences() {
    return Optional.ofNullable(
        (VersionedWrapper<ProposerPreferences>)
            metadata.get(SignType.PROPOSER_PREFERENCES.getName()));
  }

  @SuppressWarnings("unchecked")
  private Optional<VersionedWrapper<BuilderRequestAuth>> getBuilderRequestAuth() {
    return Optional.ofNullable(
        (VersionedWrapper<BuilderRequestAuth>)
            metadata.get(SignType.BUILDER_REQUEST_AUTH.getName()));
  }
}

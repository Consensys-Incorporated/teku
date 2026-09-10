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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema13;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public class BeaconBlockBodySchemaGloasImpl
    extends ContainerSchema13<
        BeaconBlockBodyGloasImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        SszList<SignedBlsToExecutionChange>,
        SignedExecutionPayloadBid,
        SszList<PayloadAttestation>,
        ExecutionRequests>
    implements BeaconBlockBodySchemaGloas<BeaconBlockBodyGloasImpl> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(13);

  protected BeaconBlockBodySchemaGloasImpl(
      final String containerName,
      final NamedSchema<SszSignature> randaoRevealSchema,
      final NamedSchema<Eth1Data> eth1DataSchema,
      final NamedSchema<SszBytes32> graffitiSchema,
      final NamedSchema<SszList<ProposerSlashing>> proposerSlashingsSchema,
      final NamedSchema<SszList<AttesterSlashing>> attesterSlashingsSchema,
      final NamedSchema<SszList<Attestation>> attestationsSchema,
      final NamedSchema<SszList<Deposit>> depositsSchema,
      final NamedSchema<SszList<SignedVoluntaryExit>> voluntaryExitsSchema,
      final NamedSchema<SyncAggregate> syncAggregateSchema,
      final NamedSchema<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final NamedSchema<SignedExecutionPayloadBid> signedExecutionPayloadBid,
      final NamedSchema<SszList<PayloadAttestation>> payloadAttestations,
      final NamedSchema<ExecutionRequests> executionRequests) {
    super(
        containerName,
        ACTIVE_FIELDS,
        randaoRevealSchema,
        eth1DataSchema,
        graffitiSchema,
        proposerSlashingsSchema,
        attesterSlashingsSchema,
        attestationsSchema,
        depositsSchema,
        voluntaryExitsSchema,
        syncAggregateSchema,
        blsToExecutionChange,
        signedExecutionPayloadBid,
        payloadAttestations,
        executionRequests);
  }

  public static BeaconBlockBodySchemaGloasImpl create(
      final SpecConfigGloas specConfig,
      final String containerName,
      final SchemaRegistry schemaRegistry) {
    return new BeaconBlockBodySchemaGloasImpl(
        containerName,
        namedSchema(BlockBodyFields.RANDAO_REVEAL, SszSignatureSchema.INSTANCE),
        namedSchema(BlockBodyFields.ETH1_DATA, Eth1Data.SSZ_SCHEMA),
        namedSchema(BlockBodyFields.GRAFFITI, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            BlockBodyFields.PROPOSER_SLASHINGS,
            SszProgressiveListSchema.create(ProposerSlashing.SSZ_SCHEMA)),
        namedSchema(
            BlockBodyFields.ATTESTER_SLASHINGS,
            SszProgressiveListSchema.create(
                schemaRegistry.get(SchemaTypes.ATTESTER_SLASHING_SCHEMA))),
        namedSchema(
            BlockBodyFields.ATTESTATIONS,
            SszProgressiveListSchema.create(schemaRegistry.get(ATTESTATION_SCHEMA))),
        namedSchema(BlockBodyFields.DEPOSITS, SszProgressiveListSchema.create(Deposit.SSZ_SCHEMA)),
        namedSchema(
            BlockBodyFields.VOLUNTARY_EXITS,
            SszProgressiveListSchema.create(SignedVoluntaryExit.SSZ_SCHEMA)),
        namedSchema(
            BlockBodyFields.SYNC_AGGREGATE,
            SyncAggregateSchema.create(specConfig.getSyncCommitteeSize())),
        namedSchema(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES,
            SszProgressiveListSchema.create(
                schemaRegistry.get(SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA))),
        namedSchema(
            BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID,
            schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA)),
        namedSchema(
            BlockBodyFields.PAYLOAD_ATTESTATIONS,
            SszProgressiveListSchema.create(schemaRegistry.get(PAYLOAD_ATTESTATION_SCHEMA))),
        namedSchema(
            BlockBodyFields.PARENT_EXECUTION_REQUESTS,
            SszSchema.as(ExecutionRequests.class, schemaRegistry.get(EXECUTION_REQUESTS_SCHEMA))));
  }

  @Override
  public SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder) {
    final BeaconBlockBodyBuilderGloas builder = new BeaconBlockBodyBuilderGloas(this);
    return bodyBuilder.apply(builder).thenApply(__ -> builder.build());
  }

  @Override
  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBodyGloasImpl(this);
  }

  /**
   * Genesis initialisation is not defined by the Gloas spec. This follows the convention shared by
   * the ethpandaops genesis generator, Lighthouse and Lodestar: the genesis block body is an empty
   * body whose (unsigned) execution payload bid is the state's {@code
   * latest_execution_payload_bid}.
   */
  @Override
  public BeaconBlockBody createGenesisBody(final BeaconState genesisState) {
    final ExecutionPayloadBid latestExecutionPayloadBid =
        BeaconStateGloas.required(genesisState).getLatestExecutionPayloadBid();
    final SignedExecutionPayloadBid signedExecutionPayloadBid =
        getSignedExecutionPayloadBidSchema()
            .create(latestExecutionPayloadBid, BLSSignature.empty());
    final BeaconBlockBody emptyBody = createEmpty();
    final List<SszData> fieldValues = new ArrayList<>(emptyBody.size());
    for (int i = 0; i < emptyBody.size(); i++) {
      fieldValues.add(emptyBody.get(i));
    }
    fieldValues.set(
        getFieldIndex(BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID), signedExecutionPayloadBid);
    return createFromFieldValues(fieldValues);
  }

  private SignedExecutionPayloadBidSchema getSignedExecutionPayloadBidSchema() {
    return (SignedExecutionPayloadBidSchema)
        getChildSchema(getFieldIndex(BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID));
  }

  @Override
  public BeaconBlockBodyGloasImpl createFromBackingNode(final TreeNode node) {
    return new BeaconBlockBodyGloasImpl(this, node);
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
    return (SszListSchema<ProposerSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.PROPOSER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
    return (SszListSchema<AttesterSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Attestation, ?> getAttestationsSchema() {
    return (SszListSchema<Attestation, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTATIONS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return (SszListSchema<Deposit, ?>) getChildSchema(getFieldIndex(BlockBodyFields.DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
    return (SszListSchema<SignedVoluntaryExit, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.VOLUNTARY_EXITS));
  }

  @Override
  public SyncAggregateSchema getSyncAggregateSchema() {
    return (SyncAggregateSchema) getChildSchema(getFieldIndex(BlockBodyFields.SYNC_AGGREGATE));
  }

  @Override
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    throw new UnsupportedOperationException("execution_payload field was removed in Gloas");
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedBlsToExecutionChange, ?> getBlsToExecutionChangesSchema() {
    return (SszListSchema<SignedBlsToExecutionChange, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.BLS_TO_EXECUTION_CHANGES));
  }

  @Override
  public SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema() {
    return getSignedExecutionPayloadBidSchema().getMessageSchema().getBlobKzgCommitmentsSchema();
  }

  @Override
  public long getBlobKzgCommitmentsGeneralizedIndex() {
    throw new UnsupportedOperationException("Not required in Gloas");
  }

  @Override
  public ExecutionRequestsSchema<?> getExecutionRequestsSchema() {
    throw new UnsupportedOperationException("execution_requests field was removed in Gloas");
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<PayloadAttestation, ?> getPayloadAttestationsSchema() {
    return (SszListSchema<PayloadAttestation, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.PAYLOAD_ATTESTATIONS));
  }

  @Override
  public ExecutionRequestsSchema<?> getParentExecutionRequestsSchema() {
    return (ExecutionRequestsSchema<?>)
        getChildSchema(getFieldIndex(BlockBodyFields.PARENT_EXECUTION_REQUESTS));
  }
}

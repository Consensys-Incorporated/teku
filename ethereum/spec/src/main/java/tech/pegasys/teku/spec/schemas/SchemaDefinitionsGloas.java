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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_DEPOSIT_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_EXIT_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENT_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWALS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWAL_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_DATA_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PROPOSER_PREFERENCES_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PTC_WINDOW_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_CONTENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_PROPOSER_PREFERENCES_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyBuilderGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BlindedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContentsSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequestSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.PayloadTimelinessCommitteeWindowSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsGloas extends SchemaDefinitionsFulu {

  private final BuilderDepositRequestSchema builderDepositRequestSchema;
  private final BuilderExitRequestSchema builderExitRequestSchema;
  private final BuilderPendingPaymentSchema builderPendingPaymentSchema;
  private final BuilderPendingWithdrawalSchema builderPendingWithdrawalSchema;
  private final PayloadAttestationDataSchema payloadAttestationDataSchema;
  private final PayloadAttestationSchema payloadAttestationSchema;
  private final PayloadAttestationMessageSchema payloadAttestationMessageSchema;
  private final IndexedPayloadAttestationSchema indexedPayloadAttestationSchema;
  private final ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> executionPayloadBidSchema;
  private final SignedExecutionPayloadBidSchema signedExecutionPayloadBidSchema;
  private final ProposerPreferencesSchema proposerPreferencesSchema;
  private final SignedProposerPreferencesSchema signedProposerPreferencesSchema;
  private final ExecutionPayloadEnvelopeSchema executionPayloadEnvelopeSchema;
  private final BlindedExecutionPayloadEnvelopeSchema blindedExecutionPayloadEnvelopeSchema;
  private final SignedExecutionPayloadEnvelopeSchema signedExecutionPayloadEnvelopeSchema;
  private final SignedExecutionPayloadEnvelopeContentsSchema
      signedExecutionPayloadEnvelopeContentsSchema;
  private final SignedBlindedExecutionPayloadEnvelopeSchema
      signedBlindedExecutionPayloadEnvelopeSchema;
  private final SszBitvectorSchema<?> executionPayloadAvailabilitySchema;
  private final SszVectorSchema<BuilderPendingPayment, ?> builderPendingPaymentsSchema;
  private final SszListSchema<BuilderPendingWithdrawal, ?> builderPendingWithdrawalsSchema;
  private final PayloadTimelinessCommitteeWindowSchema payloadTimelinessCommitteeWindowSchema;
  private final ExecutionPayloadEnvelopesByRootRequestMessageSchema
      executionPayloadEnvelopesByRootRequestMessageSchema;

  public SchemaDefinitionsGloas(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.builderDepositRequestSchema = schemaRegistry.get(BUILDER_DEPOSIT_REQUEST_SCHEMA);
    this.builderExitRequestSchema = schemaRegistry.get(BUILDER_EXIT_REQUEST_SCHEMA);
    this.builderPendingPaymentSchema = schemaRegistry.get(BUILDER_PENDING_PAYMENT_SCHEMA);
    this.builderPendingWithdrawalSchema = schemaRegistry.get(BUILDER_PENDING_WITHDRAWAL_SCHEMA);
    this.payloadAttestationDataSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_DATA_SCHEMA);
    this.payloadAttestationSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_SCHEMA);
    this.payloadAttestationMessageSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_MESSAGE_SCHEMA);
    this.indexedPayloadAttestationSchema = schemaRegistry.get(INDEXED_PAYLOAD_ATTESTATION_SCHEMA);
    this.executionPayloadBidSchema = schemaRegistry.get(EXECUTION_PAYLOAD_BID_SCHEMA);
    this.signedExecutionPayloadBidSchema = schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA);
    this.proposerPreferencesSchema = schemaRegistry.get(PROPOSER_PREFERENCES_SCHEMA);
    this.signedProposerPreferencesSchema = schemaRegistry.get(SIGNED_PROPOSER_PREFERENCES_SCHEMA);
    this.executionPayloadEnvelopeSchema = schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.blindedExecutionPayloadEnvelopeSchema =
        schemaRegistry.get(BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.signedExecutionPayloadEnvelopeSchema =
        schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.signedExecutionPayloadEnvelopeContentsSchema =
        schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_CONTENTS_SCHEMA);
    this.signedBlindedExecutionPayloadEnvelopeSchema =
        schemaRegistry.get(SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.executionPayloadAvailabilitySchema =
        schemaRegistry.get(EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA);
    this.builderPendingPaymentsSchema = schemaRegistry.get(BUILDER_PENDING_PAYMENTS_SCHEMA);
    this.builderPendingWithdrawalsSchema = schemaRegistry.get(BUILDER_PENDING_WITHDRAWALS_SCHEMA);
    this.payloadTimelinessCommitteeWindowSchema = schemaRegistry.get(PTC_WINDOW_SCHEMA);
    this.executionPayloadEnvelopesByRootRequestMessageSchema =
        schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
  }

  public static SchemaDefinitionsGloas required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsGloas,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsGloas.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsGloas) schemaDefinitions;
  }

  @Override
  protected BeaconBlockBodySchema<?> createBlindedBeaconBlockBodySchema(
      final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  protected BeaconBlockSchema createBlindedBeaconBlockSchema(final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(BEACON_BLOCK_SCHEMA);
  }

  @Override
  protected SignedBeaconBlockSchema createSignedBlindedBeaconBlockSchema(
      final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(SIGNED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderGloas(
        getBeaconBlockBodySchema().toVersionGloas().orElseThrow());
  }

  public BuilderDepositRequestSchema getBuilderDepositRequestSchema() {
    return builderDepositRequestSchema;
  }

  public BuilderExitRequestSchema getBuilderExitRequestSchema() {
    return builderExitRequestSchema;
  }

  public BuilderPendingPaymentSchema getBuilderPendingPaymentSchema() {
    return builderPendingPaymentSchema;
  }

  public BuilderPendingWithdrawalSchema getBuilderPendingWithdrawalSchema() {
    return builderPendingWithdrawalSchema;
  }

  public PayloadAttestationDataSchema getPayloadAttestationDataSchema() {
    return payloadAttestationDataSchema;
  }

  public PayloadAttestationSchema getPayloadAttestationSchema() {
    return payloadAttestationSchema;
  }

  public PayloadAttestationMessageSchema getPayloadAttestationMessageSchema() {
    return payloadAttestationMessageSchema;
  }

  public IndexedPayloadAttestationSchema getIndexedPayloadAttestationSchema() {
    return indexedPayloadAttestationSchema;
  }

  public ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> getExecutionPayloadBidSchema() {
    return executionPayloadBidSchema;
  }

  public SignedExecutionPayloadBidSchema getSignedExecutionPayloadBidSchema() {
    return signedExecutionPayloadBidSchema;
  }

  public ProposerPreferencesSchema getProposerPreferencesSchema() {
    return proposerPreferencesSchema;
  }

  public SignedProposerPreferencesSchema getSignedProposerPreferencesSchema() {
    return signedProposerPreferencesSchema;
  }

  public ExecutionPayloadEnvelopeSchema getExecutionPayloadEnvelopeSchema() {
    return executionPayloadEnvelopeSchema;
  }

  public BlindedExecutionPayloadEnvelopeSchema getBlindedExecutionPayloadEnvelopeSchema() {
    return blindedExecutionPayloadEnvelopeSchema;
  }

  public SignedExecutionPayloadEnvelopeSchema getSignedExecutionPayloadEnvelopeSchema() {
    return signedExecutionPayloadEnvelopeSchema;
  }

  public SignedExecutionPayloadEnvelopeContentsSchema
      getSignedExecutionPayloadEnvelopeContentsSchema() {
    return signedExecutionPayloadEnvelopeContentsSchema;
  }

  public SignedBlindedExecutionPayloadEnvelopeSchema
      getSignedBlindedExecutionPayloadEnvelopeSchema() {
    return signedBlindedExecutionPayloadEnvelopeSchema;
  }

  public SszBitvectorSchema<?> getExecutionPayloadAvailabilitySchema() {
    return executionPayloadAvailabilitySchema;
  }

  public SszVectorSchema<BuilderPendingPayment, ?> getBuilderPendingPaymentsSchema() {
    return builderPendingPaymentsSchema;
  }

  public SszListSchema<BuilderPendingWithdrawal, ?> getBuilderPendingWithdrawalsSchema() {
    return builderPendingWithdrawalsSchema;
  }

  public PayloadTimelinessCommitteeWindowSchema getPtcWindowSchema() {
    return payloadTimelinessCommitteeWindowSchema;
  }

  public ExecutionPayloadEnvelopesByRootRequestMessageSchema
      getExecutionPayloadEnvelopesByRootRequestMessageSchema() {
    return executionPayloadEnvelopesByRootRequestMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsGloas> toVersionGloas() {
    return Optional.of(this);
  }
}

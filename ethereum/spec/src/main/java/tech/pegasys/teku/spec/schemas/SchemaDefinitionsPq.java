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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;

import com.google.common.base.Suppliers;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.PqBlockSchemas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsSchemaEip8483;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsSchemaPq;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStateSchemaPq;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * Schemas for the PQ fork. The state, blocks, attestations, attester slashings, execution requests
 * and XMSS-signed bids, envelopes and proposer preferences are PQ's own. The aggregate, single
 * attestation and payload attestation message schemas, which do not share the Gloas types, stay
 * reachable only through {@link #getPqAttestationSchemas()} until PQ's networking and validator
 * client use them. Sync-committee and light-client schemas are inherited but unused: PQ has none.
 */
public class SchemaDefinitionsPq extends SchemaDefinitionsEip8483 {

  private final Supplier<BeaconStateSchemaPq> pqBeaconStateSchema;
  private final Supplier<ExecutionRequestsSchemaPq> pqExecutionRequestsSchema;
  private final Supplier<PqAttestationSchemas> pqAttestationSchemas;
  private final Supplier<PqBlockSchemas> pqBlockSchemas;
  private final Supplier<ExecutionRequestsSchemaEip8483> eip8483ExecutionRequestsSchema;

  public SchemaDefinitionsPq(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.pqBeaconStateSchema =
        Suppliers.memoize(
            () -> BeaconStateSchemaPq.required(schemaRegistry.get(BEACON_STATE_SCHEMA)));
    this.pqExecutionRequestsSchema =
        Suppliers.memoize(
            () ->
                ExecutionRequestsSchemaPq.required(schemaRegistry.get(EXECUTION_REQUESTS_SCHEMA)));
    this.pqAttestationSchemas = Suppliers.memoize(() -> new PqAttestationSchemas(schemaRegistry));
    this.pqBlockSchemas =
        Suppliers.memoize(
            () ->
                new PqBlockSchemas(
                    schemaRegistry, pqAttestationSchemas.get(), pqExecutionRequestsSchema.get()));
    this.eip8483ExecutionRequestsSchema =
        Suppliers.memoize(
            () ->
                new ExecutionRequestsSchemaEip8483(
                    schemaRegistry,
                    EXECUTION_REQUESTS_SCHEMA.getSchemaName(SpecMilestone.EIP8483)));
  }

  public static SchemaDefinitionsPq required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsPq,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsPq.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsPq) schemaDefinitions;
  }

  /**
   * The PQ attestation, indexed attestation, attester slashing, aggregate and payload attestation
   * message schemas. The attestation and attester slashing schemas are also the milestone's.
   */
  @NonSchema
  public PqAttestationSchemas getPqAttestationSchemas() {
    return pqAttestationSchemas.get();
  }

  /**
   * The PQ block body, block, signed block and XMSS-signed bid, envelope and proposer preferences
   * schemas, which are also the milestone's.
   */
  @NonSchema
  public PqBlockSchemas getPqBlockSchemas() {
    return pqBlockSchemas.get();
  }

  /** The PQ state schema, which is also the milestone's {@link #getBeaconStateSchema()}. */
  @NonSchema
  public BeaconStateSchemaPq getPqBeaconStateSchema() {
    return pqBeaconStateSchema.get();
  }

  /**
   * The PQ execution requests schema, also the milestone's {@link #getExecutionRequestsSchema()}.
   */
  @NonSchema
  public ExecutionRequestsSchemaPq getPqExecutionRequestsSchema() {
    return pqExecutionRequestsSchema.get();
  }

  /**
   * The EIP-8483 execution requests schema, which a parent payload from before the PQ fork
   * committed to.
   */
  @NonSchema
  public ExecutionRequestsSchemaEip8483 getEip8483ExecutionRequestsSchema() {
    return eip8483ExecutionRequestsSchema.get();
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return getPqBeaconStateSchema();
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return getPqBlockSchemas().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return getPqBlockSchemas().getBeaconBlockSchema();
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return getPqBlockSchemas().getSignedBeaconBlockSchema();
  }

  /** As in Gloas, there are no blinded blocks. */
  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return getBeaconBlockSchema();
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return getSignedBeaconBlockSchema();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlockContainerSchema() {
    return getBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema() {
    return getSignedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  /** Building PQ block bodies comes with PQ's block production. */
  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    throw new UnsupportedOperationException("PQ block production is not implemented yet");
  }

  @Override
  @SuppressWarnings("unchecked")
  public AttestationSchema<Attestation> getAttestationSchema() {
    return (AttestationSchema<Attestation>)
        (AttestationSchema<?>) getPqAttestationSchemas().getAttestationSchema();
  }

  @Override
  public AttesterSlashingSchema getAttesterSlashingSchema() {
    return getPqAttestationSchemas().getAttesterSlashingSchema();
  }

  @Override
  public ExecutionRequestsSchema<? extends ExecutionRequests> getExecutionRequestsSchema() {
    return getPqExecutionRequestsSchema();
  }

  @Override
  public SignedExecutionPayloadBidSchema getSignedExecutionPayloadBidSchema() {
    return getPqBlockSchemas().getSignedExecutionPayloadBidSchema();
  }

  @Override
  public SignedExecutionPayloadEnvelopeSchema getSignedExecutionPayloadEnvelopeSchema() {
    return getPqBlockSchemas().getSignedExecutionPayloadEnvelopeSchema();
  }

  @Override
  public SignedBlindedExecutionPayloadEnvelopeSchema
      getSignedBlindedExecutionPayloadEnvelopeSchema() {
    return getPqBlockSchemas().getSignedBlindedExecutionPayloadEnvelopeSchema();
  }

  @Override
  public SignedProposerPreferencesSchema getSignedProposerPreferencesSchema() {
    return getPqBlockSchemas().getSignedProposerPreferencesSchema();
  }

  @Override
  public Optional<SchemaDefinitionsPq> toVersionPq() {
    return Optional.of(this);
  }
}

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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.METADATA_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.STATUS_MESSAGE_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyBuilderPhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public class SchemaDefinitionsPhase0 extends AbstractSchemaDefinitions {
  private final IndexedAttestationSchema indexedAttestationSchema;
  private final AttesterSlashingSchema attesterSlashingSchema;
  private final AttestationSchema<Attestation> attestationSchema;
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private final AggregateAndProofSchema aggregateAndProofSchema;
  private final BeaconStateSchema<? extends BeaconState, ? extends MutableBeaconState>
      beaconStateSchema;
  private final BeaconBlockBodySchema<?> beaconBlockBodySchema;
  private final MetadataMessageSchema<?> metadataMessageSchema;
  private final StatusMessageSchema<?> statusMessageSchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;

  public SchemaDefinitionsPhase0(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.indexedAttestationSchema = schemaRegistry.get(SchemaTypes.INDEXED_ATTESTATION_SCHEMA);
    this.attesterSlashingSchema = schemaRegistry.get(SchemaTypes.ATTESTER_SLASHING_SCHEMA);

    this.attestationSchema = schemaRegistry.get(SchemaTypes.ATTESTATION_SCHEMA);
    this.aggregateAndProofSchema = schemaRegistry.get(AGGREGATE_AND_PROOF_SCHEMA);
    this.signedAggregateAndProofSchema = schemaRegistry.get(SIGNED_AGGREGATE_AND_PROOF_SCHEMA);
    this.beaconStateSchema = schemaRegistry.get(BEACON_STATE_SCHEMA);
    this.beaconBlockBodySchema = schemaRegistry.get(BEACON_BLOCK_BODY_SCHEMA);
    this.metadataMessageSchema = schemaRegistry.get(METADATA_MESSAGE_SCHEMA);
    this.statusMessageSchema = schemaRegistry.get(STATUS_MESSAGE_SCHEMA);
    this.beaconBlockSchema = schemaRegistry.get(BEACON_BLOCK_SCHEMA);
    this.signedBeaconBlockSchema = schemaRegistry.get(SIGNED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public SignedAggregateAndProofSchema getSignedAggregateAndProofSchema() {
    return signedAggregateAndProofSchema;
  }

  @Override
  public AggregateAndProofSchema getAggregateAndProofSchema() {
    return aggregateAndProofSchema;
  }

  @Override
  public AttestationSchema<Attestation> getAttestationSchema() {
    return attestationSchema;
  }

  @Override
  public IndexedAttestationSchema getIndexedAttestationSchema() {
    return indexedAttestationSchema;
  }

  @Override
  public AttesterSlashingSchema getAttesterSlashingSchema() {
    return attesterSlashingSchema;
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return signedBeaconBlockSchema;
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return beaconBlockSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  @Override
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return getBeaconBlockSchema();
  }

  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return getBeaconBlockBodySchema();
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
    return getSignedBlindedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderPhase0(beaconBlockBodySchema);
  }

  @Override
  public MetadataMessageSchema<?> getMetadataMessageSchema() {
    return metadataMessageSchema;
  }

  @Override
  public StatusMessageSchema<?> getStatusMessageSchema() {
    return statusMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }
}

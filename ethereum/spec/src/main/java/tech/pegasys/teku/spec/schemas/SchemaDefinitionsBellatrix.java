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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BUILDER_BID_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsBellatrix extends SchemaDefinitionsAltair {
  private final ExecutionPayloadSchema<?> executionPayloadSchema;
  private final BeaconBlockBodySchema<?> blindedBeaconBlockBodySchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;
  private final ExecutionPayloadHeaderSchema<?> executionPayloadHeaderSchema;
  private final BuilderBidSchema<?> builderBidSchema;
  private final SignedBuilderBidSchema signedBuilderBidSchema;

  public SchemaDefinitionsBellatrix(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.executionPayloadSchema = schemaRegistry.get(EXECUTION_PAYLOAD_SCHEMA);
    this.executionPayloadHeaderSchema = schemaRegistry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA);
    this.blindedBeaconBlockBodySchema = createBlindedBeaconBlockBodySchema(schemaRegistry);
    this.blindedBeaconBlockSchema = createBlindedBeaconBlockSchema(schemaRegistry);
    this.signedBlindedBeaconBlockSchema = createSignedBlindedBeaconBlockSchema(schemaRegistry);
    this.builderBidSchema = schemaRegistry.get(BUILDER_BID_SCHEMA);
    this.signedBuilderBidSchema = schemaRegistry.get(SIGNED_BUILDER_BID_SCHEMA);
  }

  public static SchemaDefinitionsBellatrix required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsBellatrix,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsBellatrix.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsBellatrix) schemaDefinitions;
  }

  protected BeaconBlockBodySchema<?> createBlindedBeaconBlockBodySchema(
      final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(BLINDED_BEACON_BLOCK_BODY_SCHEMA);
  }

  protected BeaconBlockSchema createBlindedBeaconBlockSchema(final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(BLINDED_BEACON_BLOCK_SCHEMA);
  }

  protected SignedBeaconBlockSchema createSignedBlindedBeaconBlockSchema(
      final SchemaRegistry schemaRegistry) {
    return schemaRegistry.get(SIGNED_BLINDED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return blindedBeaconBlockSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return blindedBeaconBlockBodySchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return signedBlindedBeaconBlockSchema;
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBlindedBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema() {
    return getSignedBlindedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderBellatrix(
        getBeaconBlockBodySchema().toVersionBellatrix().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionBellatrix().orElseThrow());
  }

  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return executionPayloadSchema;
  }

  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchema;
  }

  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchema;
  }

  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchema;
  }

  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadSchema();
  }

  @Override
  public Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}

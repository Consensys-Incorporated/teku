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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_IN_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECAR_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOCK_CONTENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLOCK_CONTENTS_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blobs.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsDeneb extends SchemaDefinitionsCapella {
  private final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema;

  private final BlobSchema blobSchema;
  private final SszListSchema<Blob, ? extends SszList<Blob>> blobsInBlockSchema;
  private final BlobSidecarSchema blobSidecarSchema;
  private final BlockContentsSchema<?> blockContentsSchema;
  private final SignedBlockContentsSchema<?> signedBlockContentsSchema;
  private final BlobsBundleSchema<?> blobsBundleSchema;
  private final ExecutionPayloadAndBlobsBundleSchema executionPayloadAndBlobsBundleSchema;
  private final BlobSidecarsByRootRequestMessageSchema blobSidecarsByRootRequestMessageSchema;

  public SchemaDefinitionsDeneb(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);

    this.blobKzgCommitmentsSchema = schemaRegistry.get(BLOB_KZG_COMMITMENTS_SCHEMA);
    this.blobSchema = schemaRegistry.get(BLOB_SCHEMA);
    this.blobsInBlockSchema = schemaRegistry.get(BLOBS_IN_BLOCK_SCHEMA);
    this.blobSidecarSchema = schemaRegistry.get(BLOB_SIDECAR_SCHEMA);
    this.blockContentsSchema = schemaRegistry.get(BLOCK_CONTENTS_SCHEMA);
    this.signedBlockContentsSchema = schemaRegistry.get(SIGNED_BLOCK_CONTENTS_SCHEMA);
    this.blobsBundleSchema = schemaRegistry.get(BLOBS_BUNDLE_SCHEMA);
    this.executionPayloadAndBlobsBundleSchema =
        schemaRegistry.get(EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA);
    this.blobSidecarsByRootRequestMessageSchema =
        schemaRegistry.get(BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
  }

  public static SchemaDefinitionsDeneb required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsDeneb,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsDeneb.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsDeneb) schemaDefinitions;
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlockContainerSchema() {
    return getBlockContentsSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBlockContentsSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadAndBlobsBundleSchema();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderDeneb(
        getBeaconBlockBodySchema().toVersionDeneb().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionDeneb().orElseThrow());
  }

  public BlobSchema getBlobSchema() {
    return blobSchema;
  }

  public BlobKzgCommitmentsSchema getBlobKzgCommitmentsSchema() {
    return blobKzgCommitmentsSchema;
  }

  public SszListSchema<Blob, ? extends SszList<Blob>> getBlobsInBlockSchema() {
    return blobsInBlockSchema;
  }

  public BlobSidecarSchema getBlobSidecarSchema() {
    return blobSidecarSchema;
  }

  public BlockContentsSchema<?> getBlockContentsSchema() {
    return blockContentsSchema;
  }

  public SignedBlockContentsSchema<?> getSignedBlockContentsSchema() {
    return signedBlockContentsSchema;
  }

  public BlobsBundleSchema<?> getBlobsBundleSchema() {
    return blobsBundleSchema;
  }

  public ExecutionPayloadAndBlobsBundleSchema getExecutionPayloadAndBlobsBundleSchema() {
    return executionPayloadAndBlobsBundleSchema;
  }

  public BlobSidecarsByRootRequestMessageSchema getBlobSidecarsByRootRequestMessageSchema() {
    return blobSidecarsByRootRequestMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}

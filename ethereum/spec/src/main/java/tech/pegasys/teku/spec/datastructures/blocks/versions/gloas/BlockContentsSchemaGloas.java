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

package tech.pegasys.teku.spec.datastructures.blocks.versions.gloas;

import static tech.pegasys.teku.kzg.KZG.FIELD_ELEMENTS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BlockContentsSchemaGloas
    extends ContainerSchema4<
        BlockContentsGloas,
        BeaconBlock,
        ExecutionPayloadEnvelope,
        SszList<SszKZGProof>,
        SszList<Blob>>
    implements BlockContentsSchema<BlockContentsGloas> {

  public BlockContentsSchemaGloas(
      final String containerName,
      final SpecConfigGloas specConfig,
      final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema(FIELD_BLOCK, schemaRegistry.get(BEACON_BLOCK_SCHEMA)),
        namedSchema(
            FIELD_EXECUTION_PAYLOAD_ENVELOPE,
            schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)),
        namedSchema(
            FIELD_KZG_PROOFS,
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE,
                (long) specConfig.getMaxBlobCommitmentsPerBlock() * FIELD_ELEMENTS_PER_EXT_BLOB)),
        namedSchema(
            FIELD_BLOBS,
            SszListSchema.create(
                schemaRegistry.get(BLOB_SCHEMA), specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  @Override
  public BlockContentsGloas create(
      final BeaconBlock beaconBlock,
      final List<KZGProof> kzgProofs,
      final List<Blob> blobs,
      final Optional<ExecutionPayloadEnvelope> executionPayloadEnvelope) {
    return new BlockContentsGloas(
        this,
        beaconBlock,
        executionPayloadEnvelope.orElseThrow(),
        getKzgProofsSchema().createFromElements(kzgProofs.stream().map(SszKZGProof::new).toList()),
        getBlobsSchema().createFromElements(blobs));
  }

  @Override
  public BlockContentsGloas createFromBackingNode(final TreeNode node) {
    return new BlockContentsGloas(this, node);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex(FIELD_BLOBS));
  }
}

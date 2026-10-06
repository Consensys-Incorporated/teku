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

package tech.pegasys.teku.spec.datastructures.blocks.versions.deneb;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedBlockContentsSchemaDeneb
    extends ContainerSchema3<
        SignedBlockContentsDeneb, SignedBeaconBlock, SszList<SszKZGProof>, SszList<Blob>>
    implements SignedBlockContentsSchema<SignedBlockContentsDeneb> {

  public SignedBlockContentsSchemaDeneb(
      final String containerName,
      final SpecConfigDeneb specConfig,
      final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema(FIELD_SIGNED_BLOCK, schemaRegistry.get(SIGNED_BEACON_BLOCK_SCHEMA)),
        namedSchema(
            FIELD_KZG_PROOFS,
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            FIELD_BLOBS,
            SszListSchema.create(
                schemaRegistry.get(BLOB_SCHEMA), specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  @Override
  public SignedBlockContentsDeneb create(
      final SignedBeaconBlock signedBeaconBlock,
      final SszList<SszKZGProof> kzgProofs,
      final SszList<Blob> blobs) {
    return new SignedBlockContentsDeneb(this, signedBeaconBlock, kzgProofs, blobs);
  }

  @Override
  public SignedBlockContentsDeneb createFromBackingNode(final TreeNode node) {
    return new SignedBlockContentsDeneb(this, node);
  }

  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return (SignedBeaconBlockSchema) getFieldSchema0();
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

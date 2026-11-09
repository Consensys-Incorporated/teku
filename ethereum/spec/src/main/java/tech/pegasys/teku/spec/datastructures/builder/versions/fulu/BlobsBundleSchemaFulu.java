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

package tech.pegasys.teku.spec.datastructures.builder.versions.fulu;

import static tech.pegasys.teku.kzg.KZG.FIELD_ELEMENTS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BlobsBundleSchemaFulu
    extends ContainerSchema3<
        BlobsBundleFulu, SszList<SszKZGCommitment>, SszList<SszKZGProof>, SszList<Blob>>
    implements BlobsBundleSchema<BlobsBundleFulu> {

  public BlobsBundleSchemaFulu(
      final SchemaRegistry schemaRegistry, final SpecConfigDeneb specConfig) {
    super(
        "BlobsBundleFulu",
        namedSchema("commitments", schemaRegistry.get(BLOB_KZG_COMMITMENTS_SCHEMA)),
        namedSchema(
            "proofs",
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE,
                (long) specConfig.getMaxBlobCommitmentsPerBlock() * FIELD_ELEMENTS_PER_EXT_BLOB)),
        namedSchema(
            "blobs",
            SszListSchema.create(
                schemaRegistry.get(BLOB_SCHEMA), specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  @Override
  public BlobsBundleFulu createFromBackingNode(final TreeNode node) {
    return new BlobsBundleFulu(this, node);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGCommitment, ?> getCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>) getChildSchema(getFieldIndex("commitments"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGProof, ?> getProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex("proofs"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex("blobs"));
  }

  @Override
  public BlobsBundle create(
      final List<KZGCommitment> commitments, final List<KZGProof> proofs, final List<Blob> blobs) {
    return create(
        getCommitmentsSchema()
            .createFromElements(commitments.stream().map(SszKZGCommitment::new).toList()),
        getProofsSchema().createFromElements(proofs.stream().map(SszKZGProof::new).toList()),
        getBlobsSchema().createFromElements(blobs));
  }

  @Override
  public BlobsBundle create(
      final SszList<SszKZGCommitment> commitments,
      final SszList<SszKZGProof> proofs,
      final SszList<Blob> blobs) {
    return new BlobsBundleFulu(this, commitments, proofs, blobs);
  }
}

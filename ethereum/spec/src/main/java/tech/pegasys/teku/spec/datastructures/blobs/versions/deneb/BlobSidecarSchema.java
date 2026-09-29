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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeaderSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class BlobSidecarSchema
    extends ContainerSchema6<
        BlobSidecar,
        SszUInt64,
        Blob,
        SszKZGCommitment,
        SszKZGProof,
        SignedBeaconBlockHeader,
        SszBytes32Vector> {

  static final SszFieldName FIELD_BLOB = () -> "blob";
  static final SszFieldName FIELD_SIGNED_BLOCK_HEADER = () -> "signed_block_header";
  static final SszFieldName FIELD_KZG_COMMITMENT_INCLUSION_PROOF =
      () -> "kzg_commitment_inclusion_proof";

  BlobSidecarSchema(
      final SignedBeaconBlockHeaderSchema signedBeaconBlockHeaderSchema,
      final BlobSchema blobSchema,
      final int kzgCommitmentInclusionProofDepth) {
    super(
        "BlobSidecar",
        namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BLOB, blobSchema),
        namedSchema("kzg_commitment", SszKZGCommitmentSchema.INSTANCE),
        namedSchema("kzg_proof", SszKZGProofSchema.INSTANCE),
        namedSchema(FIELD_SIGNED_BLOCK_HEADER, signedBeaconBlockHeaderSchema),
        namedSchema(
            FIELD_KZG_COMMITMENT_INCLUSION_PROOF,
            SszBytes32VectorSchema.create(kzgCommitmentInclusionProofDepth)));
  }

  public BlobSchema getBlobSchema() {
    return (BlobSchema) getChildSchema(getFieldIndex(FIELD_BLOB));
  }

  public SszBytes32VectorSchema<?> getKzgCommitmentInclusionProofSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENT_INCLUSION_PROOF));
  }

  public BlobSidecar create(
      final UInt64 index,
      final Blob blob,
      final SszKZGCommitment sszKzgCommitment,
      final SszKZGProof sszKzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    return new BlobSidecar(
        this,
        index,
        blob,
        sszKzgCommitment,
        sszKzgProof,
        signedBeaconBlockHeader,
        kzgCommitmentInclusionProof);
  }

  public BlobSidecar create(
      final UInt64 index,
      final Bytes blob,
      final Bytes48 kzgCommitment,
      final Bytes48 kzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    return create(
        index,
        new Blob(getBlobSchema(), blob),
        KZGCommitment.fromBytesCompressed(kzgCommitment),
        KZGProof.fromBytesCompressed(kzgProof),
        signedBeaconBlockHeader,
        kzgCommitmentInclusionProof);
  }

  public BlobSidecar create(
      final UInt64 index,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final KZGProof kzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    return new BlobSidecar(
        this,
        index,
        blob,
        kzgCommitment,
        kzgProof,
        signedBeaconBlockHeader,
        kzgCommitmentInclusionProof);
  }

  public static BlobSidecarSchema create(
      final SignedBeaconBlockHeaderSchema signedBeaconBlockHeaderSchema,
      final BlobSchema blobSchema,
      final int kzgCommitmentInclusionProofDepth) {
    return new BlobSidecarSchema(
        signedBeaconBlockHeaderSchema, blobSchema, kzgCommitmentInclusionProofDepth);
  }

  @Override
  public BlobSidecar createFromBackingNode(final TreeNode node) {
    return new BlobSidecar(this, node);
  }
}

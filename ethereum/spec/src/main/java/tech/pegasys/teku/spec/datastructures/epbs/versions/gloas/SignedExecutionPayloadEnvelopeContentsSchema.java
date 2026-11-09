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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.kzg.KZG.FIELD_ELEMENTS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedExecutionPayloadEnvelopeContentsSchema
    extends ContainerSchema3<
        SignedExecutionPayloadEnvelopeContents,
        SignedExecutionPayloadEnvelope,
        SszList<SszKZGProof>,
        SszList<Blob>> {

  private static final SszFieldName FIELD_KZG_PROOFS = () -> "kzg_proofs";
  private static final SszFieldName FIELD_BLOBS = () -> "blobs";

  public SignedExecutionPayloadEnvelopeContentsSchema(
      final SpecConfigFulu specConfig, final SchemaRegistry schemaRegistry) {
    super(
        "SignedExecutionPayloadEnvelopeContents",
        namedSchema(
            "signed_execution_payload_envelope",
            schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)),
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

  public SignedExecutionPayloadEnvelopeContents create(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final List<KZGProof> kzgProofs,
      final List<Blob> blobs) {
    return new SignedExecutionPayloadEnvelopeContents(
        this, signedExecutionPayloadEnvelope, kzgProofs, blobs);
  }

  @Override
  public SignedExecutionPayloadEnvelopeContents createFromBackingNode(final TreeNode node) {
    return new SignedExecutionPayloadEnvelopeContents(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex(FIELD_BLOBS));
  }
}

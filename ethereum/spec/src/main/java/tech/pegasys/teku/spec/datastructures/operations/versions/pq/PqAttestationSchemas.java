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

package tech.pegasys.teku.spec.datastructures.operations.versions.pq;

import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq.PayloadAttestationMessagePqSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq.AggregateAndProofPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq.SignedAggregateAndProofPqSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * The PQ attestation-family schemas, built from the PQ config: the attestation proof is limited to
 * {@code MAX_STARK_PROOF_SIZE} and committee bits to {@code MAX_COMMITTEES_PER_SLOT_PQ}.
 */
public class PqAttestationSchemas {

  private final SszByteListSchema<SszByteList> attestationProofSchema;
  private final AttestationPqSchema attestationSchema;
  private final IndexedAttestationPqSchema indexedAttestationSchema;
  private final AttesterSlashingSchema attesterSlashingSchema;
  private final AggregateAndProofPqSchema aggregateAndProofSchema;
  private final SignedAggregateAndProofPqSchema signedAggregateAndProofSchema;
  private final PayloadAttestationMessagePqSchema payloadAttestationMessageSchema;

  public PqAttestationSchemas(final SchemaRegistry schemaRegistry) {
    final SpecConfigPq specConfig = SpecConfigPq.required(schemaRegistry.getSpecConfig());
    this.attestationProofSchema = SszByteListSchema.create(specConfig.getMaxStarkProofSize());
    this.attestationSchema =
        new AttestationPqSchema(
            "AttestationPq", schemaRegistry, specConfig.getMaxCommitteesPerSlotPq());
    this.indexedAttestationSchema =
        new IndexedAttestationPqSchema(
            "IndexedAttestationPq", schemaRegistry, attestationProofSchema);
    this.attesterSlashingSchema =
        new AttesterSlashingSchema("AttesterSlashingPq", indexedAttestationSchema);
    this.aggregateAndProofSchema =
        new AggregateAndProofPqSchema(attestationSchema, attestationProofSchema);
    this.signedAggregateAndProofSchema =
        new SignedAggregateAndProofPqSchema(aggregateAndProofSchema);
    this.payloadAttestationMessageSchema = new PayloadAttestationMessagePqSchema(schemaRegistry);
  }

  /** {@code AttestationProof}: the STARK proof of attesters' XMSS signatures. */
  public SszByteListSchema<SszByteList> getAttestationProofSchema() {
    return attestationProofSchema;
  }

  public AttestationPqSchema getAttestationSchema() {
    return attestationSchema;
  }

  public IndexedAttestationPqSchema getIndexedAttestationSchema() {
    return indexedAttestationSchema;
  }

  public AttesterSlashingSchema getAttesterSlashingSchema() {
    return attesterSlashingSchema;
  }

  public AggregateAndProofPqSchema getAggregateAndProofSchema() {
    return aggregateAndProofSchema;
  }

  public SignedAggregateAndProofPqSchema getSignedAggregateAndProofSchema() {
    return signedAggregateAndProofSchema;
  }

  public PayloadAttestationMessagePqSchema getPayloadAttestationMessageSchema() {
    return payloadAttestationMessageSchema;
  }
}

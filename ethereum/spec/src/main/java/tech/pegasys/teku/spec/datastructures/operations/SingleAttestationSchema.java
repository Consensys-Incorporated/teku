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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SingleAttestationSchema
    extends ContainerSchema4<SingleAttestation, SszUInt64, SszUInt64, AttestationData, SszSignature>
    implements AttestationSchema<SingleAttestation> {
  public SingleAttestationSchema() {
    super(
        "SingleAttestation",
        namedSchema("committee_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("attester_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SingleAttestation createFromBackingNode(final TreeNode node) {
    return new SingleAttestation(this, node);
  }

  public SingleAttestation create(
      final UInt64 committeeIndex,
      final UInt64 attesterIndex,
      final AttestationData data,
      final BLSSignature signature) {
    return new SingleAttestation(this, committeeIndex, attesterIndex, data, signature);
  }

  @Override
  public Attestation create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final Supplier<SszBitvector> committeeBits) {
    throw new UnsupportedOperationException("Not supported in SingleAttestation");
  }

  @Override
  public SszBitlistSchema<?> getAggregationBitsSchema() {
    throw new UnsupportedOperationException("Not supported in SingleAttestation");
  }

  @Override
  public Optional<SszBitvectorSchema<?>> getCommitteeBitsSchema() {
    return Optional.empty();
  }

  @Override
  public SingleAttestationSchema toSingleAttestationSchemaRequired() {
    return this;
  }

  @Override
  public boolean requiresCommitteeBits() {
    return false;
  }
}

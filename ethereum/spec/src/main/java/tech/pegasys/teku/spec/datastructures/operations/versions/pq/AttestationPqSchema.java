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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.AGGREGATION_BITS_SCHEMA;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * {@code Attestation} in PQ: a progressive container 4 fields wide with a gap at 2, where the
 * signature was, and committee bits sized by {@code MAX_COMMITTEES_PER_SLOT_PQ}.
 */
public class AttestationPqSchema
    extends ContainerSchema3<AttestationPq, SszBitlist, AttestationData, SszBitvector>
    implements AttestationSchema<AttestationPq> {

  private static final boolean[] ACTIVE_FIELDS = {true, true, false, true};

  public AttestationPqSchema(
      final String containerName, final SchemaRegistry registry, final long maxCommitteesPerSlot) {
    super(
        containerName,
        ACTIVE_FIELDS,
        namedSchema("aggregation_bits", registry.get(AGGREGATION_BITS_SCHEMA)),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("committee_bits", SszBitvectorSchema.create(maxCommitteesPerSlot)));
  }

  public AttestationPq create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final SszBitvector committeeBits) {
    return new AttestationPq(this, aggregationBits, data, committeeBits);
  }

  /**
   * A PQ attestation has no signature: use {@link #create(SszBitlist, AttestationData,
   * SszBitvector)}.
   */
  @Override
  public Attestation create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final Supplier<SszBitvector> committeeBits) {
    throw new UnsupportedOperationException("A PQ attestation has no signature");
  }

  @Override
  public AttestationPq createFromBackingNode(final TreeNode node) {
    return new AttestationPq(this, node);
  }

  @Override
  public SszBitlist createEmptyAggregationBits() {
    return getAggregationBitsSchema().empty();
  }

  @Override
  public SszBitlistSchema<?> getAggregationBitsSchema() {
    return (SszBitlistSchema<?>) getFieldSchema0();
  }

  @Override
  public Optional<SszBitvectorSchema<?>> getCommitteeBitsSchema() {
    return Optional.of((SszBitvectorSchema<?>) getFieldSchema2());
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }
}

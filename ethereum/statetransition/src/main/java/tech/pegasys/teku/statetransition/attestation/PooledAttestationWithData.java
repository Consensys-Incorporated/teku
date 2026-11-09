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

package tech.pegasys.teku.statetransition.attestation;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

public record PooledAttestationWithData(AttestationData data, PooledAttestation pooledAttestation) {

  public Attestation toAttestation(final AttestationSchema<Attestation> attestationSchema) {
    return attestationSchema.create(
        adaptAggregationBits(pooledAttestation.bits().getAggregationSszBits(), attestationSchema),
        data,
        pooledAttestation.aggregatedSignature(),
        pooledAttestation.bits()::getCommitteeSszBits);
  }

  /**
   * Re-encodes the aggregation bits through the target attestation schema when it differs from the
   * schema the pooled bits were built with. This is required across a fork boundary: e.g. a Gloas
   * block's {@code attestations} use a {@code ProgressiveBitlist} for {@code aggregation_bits}, but
   * a previous-epoch attestation pooled before the fork carries a bounded {@code Bitlist}. The bit
   * values are identical; only the schema (and thus merkleization) differs. Steady-state callers
   * pass a matching schema, so this is a no-op fast path.
   */
  private static SszBitlist adaptAggregationBits(
      final SszBitlist aggregationBits, final AttestationSchema<Attestation> attestationSchema) {
    final SszBitlistSchema<?> targetSchema = attestationSchema.getAggregationBitsSchema();
    if (aggregationBits.getSchema().equals(targetSchema)) {
      return aggregationBits;
    }
    return targetSchema.wrapBitSet(aggregationBits.size(), aggregationBits.getAsBitSet());
  }
}

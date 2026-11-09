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

import java.util.List;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Lightweight alternative to the SSZ-backed {@link IndexedAttestation} for internal processing
 * paths that only read the three logical fields and never hash or serialize the container.
 *
 * <p>Attesting indices are stored as a pre-wrapped {@link List} of {@link UInt64}, matching what
 * most downstream consumers (fork choice, deferred votes, active-validator cache, attestation pool)
 * need without re-wrapping per iteration. Uniqueness is guaranteed for instances produced via
 * {@code AttestationUtil.getIndexedAttestation} (one entry per aggregation-bit position on a
 * committee) but the ordering is committee-position order, not spec-form sorted-by-validator-index.
 * The spec-form sorted+unique invariant is only enforced when an instance originates from an SSZ
 * {@link IndexedAttestation} embedded in an {@code AttesterSlashing}, via the dedicated overload of
 * {@code isValidIndexedAttestation}.
 *
 * <p>*NOTE*: attestingIndices are not sorted, they will be lazily sorted in {@link
 * #toSsz(IndexedAttestationSchema)}
 */
public record IndexedAttestationLight(
    List<UInt64> attestingIndices, AttestationData data, BLSSignature signature) {

  public static IndexedAttestationLight fromSsz(final IndexedAttestation sszAttestation) {
    return new IndexedAttestationLight(
        sszAttestation.getAttestingIndices().asListUnboxed(),
        sszAttestation.getData(),
        sszAttestation.getSignature());
  }

  public IndexedAttestation toSsz(final IndexedAttestationSchema schema) {
    // The SSZ form is used for AttesterSlashing, which is gossiped/stored and validated by
    // peers via is_valid_indexed_attestation; the spec requires indices sorted by validator
    // index. attestingIndices() is not guaranteed sorted internally, so sort here.
    final List<UInt64> sortedIndices = attestingIndices.stream().sorted().toList();
    return schema.create(schema.getAttestingIndicesSchema().of(sortedIndices), data, signature);
  }
}

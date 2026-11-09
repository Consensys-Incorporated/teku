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

package tech.pegasys.teku.statetransition.attestation.utils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;

public interface AttestationBits {
  static AttestationBits fromEmptyFromAttestationSchema(
      final AttestationSchema<?> attestationSchema, final Optional<Int2IntMap> committeesSize) {
    return attestationSchema
        .toVersionElectra()
        .map(
            schema ->
                AttestationBitsElectra.fromAttestationSchema(schema, committeesSize.orElseThrow()))
        .orElseGet(() -> AttestationBitsPhase0.fromAttestationSchema(attestationSchema));
  }

  static AttestationBits of(final ValidatableAttestation attestation) {
    return attestation
        .getAttestation()
        .getCommitteeBits()
        .map(
            committeeBits ->
                (AttestationBits)
                    new AttestationBitsElectra(
                        attestation.getAttestation().getAggregationBits(),
                        committeeBits,
                        attestation.getCommitteesSize().orElseThrow()))
        .orElseGet(
            () -> new AttestationBitsPhase0(attestation.getAttestation().getAggregationBits()));
  }

  static AttestationBits of(
      final Attestation attestation, final Optional<Int2IntMap> committeesSize) {
    return attestation
        .getCommitteeBits()
        .<AttestationBits>map(
            committeeBits ->
                new AttestationBitsElectra(
                    attestation.getAggregationBits(), committeeBits, committeesSize.orElseThrow()))
        .orElseGet(() -> new AttestationBitsPhase0(attestation.getAggregationBits()));
  }

  void or(AttestationBits other);

  boolean aggregateWith(PooledAttestation other);

  void or(Attestation other);

  boolean isSuperSetOf(Attestation other);

  boolean isSuperSetOf(PooledAttestation other);

  SszBitlist getAggregationSszBits();

  SszBitvector getCommitteeSszBits();

  Int2IntMap getCommitteesSize();

  boolean requiresCommitteeBits();

  int getBitCount();

  boolean isExclusivelyFromCommittee(int committeeIndex);

  boolean isFromCommittee(int committeeIndex);

  /**
   * This is supposed to be called only for single committee attestation bits committee attestation.
   */
  int getSingleCommitteeIndex();

  IntStream streamCommitteeIndices();

  /** Creates an independent copy of this instance */
  AttestationBits copy();
}

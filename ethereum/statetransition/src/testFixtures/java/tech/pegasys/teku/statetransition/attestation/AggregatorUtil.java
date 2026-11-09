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

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;

public class AggregatorUtil {
  public static Attestation aggregateAttestations(
      final Attestation firstAttestation, final Attestation... attestations) {
    return aggregateAttestations(Optional.empty(), firstAttestation, attestations);
  }

  public static Attestation aggregateAttestations(
      final Int2IntMap committeesSize,
      final Attestation firstAttestation,
      final Attestation... attestations) {
    return aggregateAttestations(Optional.of(committeesSize), firstAttestation, attestations);
  }

  public static Attestation aggregateAttestations(
      final Optional<Int2IntMap> committeesSize,
      final Attestation firstAttestation,
      final Attestation... attestations) {
    final AttestationBits aggregateBits = AttestationBits.of(firstAttestation, committeesSize);
    final List<BLSSignature> signatures = new ArrayList<>();
    signatures.add(firstAttestation.getAggregateSignature());

    for (Attestation attestation : attestations) {
      checkState(
          aggregateBits.aggregateWith(
              new PooledAttestation(
                  AttestationBits.of(attestation, committeesSize), Optional.empty(), null, false)),
          "attestations are not aggregatable");
      signatures.add(attestation.getAggregateSignature());
    }

    return firstAttestation
        .getSchema()
        .create(
            aggregateBits.getAggregationSszBits(),
            firstAttestation.getData(),
            BLS.aggregate(signatures),
            aggregateBits::getCommitteeSszBits);
  }
}

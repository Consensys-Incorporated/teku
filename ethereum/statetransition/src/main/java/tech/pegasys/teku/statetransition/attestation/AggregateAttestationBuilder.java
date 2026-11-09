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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;

/**
 * Builds an aggregate attestation, providing functions to test if an attestation can be added or is
 * made redundant by the current aggregate.
 */
class AggregateAttestationBuilder {
  private final List<PooledAttestation> includedAttestations = new ArrayList<>();
  private final boolean accumulateValidatorIndices;
  private List<UInt64> validatorIndices;
  private AttestationBits currentAggregateBits;

  /**
   * Creates a new AggregateAttestationBuilder.
   *
   * @param accumulateValidatorIndices is required to be True when producing aggregation for
   *     AggregatingAttestationPoolV2 which requires them to calculate rewards. When we deprecate
   *     AggregatingAttestationPoolV1 we will be able to remove it.
   */
  AggregateAttestationBuilder(final boolean accumulateValidatorIndices) {
    this.accumulateValidatorIndices = accumulateValidatorIndices;
  }

  public boolean aggregate(final PooledAttestation attestation) {
    if (currentAggregateBits == null) {
      includedAttestations.add(attestation);
      currentAggregateBits = attestation.bits().copy();
      if (accumulateValidatorIndices) {
        validatorIndices = new ArrayList<>(attestation.validatorIndices().orElseThrow());
      }
      return true;
    }
    if (currentAggregateBits.aggregateWith(attestation)) {
      includedAttestations.add(attestation);
      if (accumulateValidatorIndices) {
        // since we are aggregating only non-intersecting bits,
        // indices won't overlap too, so we can just add them
        validatorIndices.addAll(attestation.validatorIndices().orElseThrow());
      }
      return true;
    }
    return false;
  }

  public PooledAttestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    return new PooledAttestation(
        currentAggregateBits,
        Optional.ofNullable(validatorIndices),
        BLS.aggregate(
            includedAttestations.stream().map(PooledAttestation::aggregatedSignature).toList()),
        false);
  }

  public Collection<PooledAttestation> getIncludedAttestations() {
    return includedAttestations;
  }
}

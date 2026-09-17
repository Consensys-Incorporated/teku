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

package tech.pegasys.teku.statetransition.payloadattestation;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

/**
 * Provides a single {@link PayloadAttestation} aggregated from many instances of {@link
 * PayloadAttestationMessage} which all share the same {@link PayloadAttestationData}.
 */
class MatchingDataPayloadAttestationGroup {

  private final Map<Integer, PayloadAttestationMessage> payloadAttestationMessages =
      new ConcurrentHashMap<>();

  private final Spec spec;
  private final PayloadAttestationData data;

  MatchingDataPayloadAttestationGroup(final Spec spec, final PayloadAttestationData data) {
    this.spec = spec;
    this.data = data;
  }

  PayloadAttestationData getData() {
    return data;
  }

  int size() {
    return payloadAttestationMessages.size();
  }

  /**
   * Adds a payload attestation message to this group. This payload attestation message will
   * eventually be aggregated with others.
   */
  boolean add(final PayloadAttestationMessage payloadAttestationMessage) {
    final int validatorIndex = payloadAttestationMessage.getValidatorIndex().intValue();
    if (!payloadAttestationMessage.getData().equals(data)) {
      // ignore payload attestation messages with a different data
      return false;
    }
    return payloadAttestationMessages.putIfAbsent(validatorIndex, payloadAttestationMessage)
        == null;
  }

  PayloadAttestation createAggregatedPayloadAttestation(final IntList payloadTimelinessCommittee) {
    checkArgument(!payloadAttestationMessages.isEmpty(), "Nothing to aggregate");
    // relative positions of the validator indices with respect to the Payload Timeliness Committee
    final IntList setBitIndices = new IntArrayList();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int ptcPosition = 0; ptcPosition < payloadTimelinessCommittee.size(); ptcPosition++) {
      final int validatorIndex = payloadTimelinessCommittee.getInt(ptcPosition);
      // check if we have received a payload attestation message for the validator in the Payload
      // Timeliness Committee
      final PayloadAttestationMessage payloadAttestationMessage =
          payloadAttestationMessages.get(validatorIndex);
      if (payloadAttestationMessage != null) {
        setBitIndices.add(ptcPosition);
        signatures.add(payloadAttestationMessage.getSignature());
      }
    }
    final PayloadAttestationSchema payloadAttestationSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(data.getSlot()).getSchemaDefinitions())
            .getPayloadAttestationSchema();
    final SszBitvector aggregationBits =
        payloadAttestationSchema.getAggregationBitsSchema().ofBits(setBitIndices);
    return payloadAttestationSchema.create(aggregationBits, data, BLS.aggregate(signatures));
  }
}

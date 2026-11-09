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

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;

class PooledAttestationWithDataTest {

  // Source fork: bounded Bitlist aggregation_bits (Electra family).
  private final Spec electraSpec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(electraSpec);
  private final AttestationSchema<Attestation> electraAttestationSchema =
      electraSpec.getGenesisSchemaDefinitions().getAttestationSchema();

  // Target fork: progressive (EIP-7916) aggregation_bits.
  private final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
  private final AttestationSchema<Attestation> gloasAttestationSchema =
      gloasSpec.getGenesisSchemaDefinitions().getAttestationSchema();

  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();
  private final Int2IntMap committeeSizes = committeeSizes();

  @Test
  void toAttestation_reEncodesBoundedBitsThroughProgressiveTargetSchema() {
    // This reproduces the Fulu->Gloas boundary: a previous-epoch attestation pooled with the
    // bounded Bitlist schema is included in a Gloas block whose attestations use a ProgressiveList.
    final PooledAttestationWithData pooled = boundedPooledAttestation(List.of(0), 0, 2);

    final Attestation result = pooled.toAttestation(gloasAttestationSchema);

    assertThat(result.getSchema()).isEqualTo(gloasAttestationSchema);
    assertThat(result.getAggregationBits().getSchema())
        .isEqualTo(gloasAttestationSchema.getAggregationBitsSchema());
    assertThat(result.getAggregationBits().streamAllSetBits()).containsExactly(0, 2);
  }

  @Test
  void toAttestation_isNoOpWhenSchemaAlreadyMatchesTarget() {
    // Steady state: the pooled bits already use the bounded Electra schema and the block is
    // Electra.
    final PooledAttestationWithData pooled = boundedPooledAttestation(List.of(0), 1, 3);

    final SszBitlist sourceBits = pooled.pooledAttestation().bits().getAggregationSszBits();
    final Attestation result = pooled.toAttestation(electraAttestationSchema);

    // No re-encoding: the same bitlist instance flows through unchanged.
    assertThat(result.getAggregationBits()).isSameAs(sourceBits);
    assertThat(result.getAggregationBits().streamAllSetBits()).containsExactly(1, 3);
  }

  private PooledAttestationWithData boundedPooledAttestation(
      final List<Integer> committeeIndices, final int... setBits) {
    final int totalSize = committeeIndices.stream().mapToInt(committeeSizes::get).sum();
    final SszBitlist aggregationBits =
        electraAttestationSchema.getAggregationBitsSchema().ofBits(totalSize, setBits);
    final Supplier<SszBitvector> committeeBits =
        () ->
            electraAttestationSchema
                .getCommitteeBitsSchema()
                .orElseThrow()
                .ofBits(committeeIndices);
    final Attestation attestation =
        electraAttestationSchema.create(
            aggregationBits, attestationData, dataStructureUtil.randomSignature(), committeeBits);
    final AttestationBits bits = AttestationBits.of(attestation, Optional.of(committeeSizes));
    final PooledAttestation pooledAttestation =
        new PooledAttestation(bits, Optional.empty(), attestation.getAggregateSignature(), false);
    return new PooledAttestationWithData(attestationData, pooledAttestation);
  }

  private static Int2IntMap committeeSizes() {
    final Int2IntMap sizes = new Int2IntOpenHashMap();
    sizes.put(0, 4);
    sizes.put(1, 4);
    return sizes;
  }
}

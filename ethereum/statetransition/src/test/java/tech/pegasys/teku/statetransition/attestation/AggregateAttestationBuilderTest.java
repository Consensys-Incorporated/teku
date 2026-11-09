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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;

class AggregateAttestationBuilderTest {

  public static final int BITLIST_SIZE = 10;
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationSchema<?> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();

  private final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(false);

  @Test
  public void canAggregate_shouldBeTrueForFirstAttestation() {
    assertThat(builder.aggregate(createPooledAttestation(1, 2, 3, 4, 5, 6, 7, 8, 9))).isTrue();
  }

  @Test
  public void canAggregate_shouldBeTrueWhenValidatorsDoNotOverlap() {
    builder.aggregate(createPooledAttestation(1, 3, 5));
    assertThat(builder.aggregate(createPooledAttestation(0, 2, 4))).isTrue();
  }

  @Test
  public void canAggregate_shouldBeFalseWhenValidatorsDoOverlap() {
    builder.aggregate(createPooledAttestation(1, 3, 5));
    assertThat(builder.aggregate(createPooledAttestation(1, 2, 4))).isFalse();
  }

  @Test
  public void aggregate_shouldTrackIncludedAggregations() {
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);
    final PooledAttestation attestation3 = createPooledAttestation(3);
    builder.aggregate(attestation1);
    builder.aggregate(attestation2);
    builder.aggregate(attestation3);

    assertThat(builder.getIncludedAttestations())
        .containsExactlyInAnyOrder(attestation1, attestation2, attestation3);
  }

  @Test
  public void aggregate_shouldCombineBitsetsAndSignatures() {
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);
    final PooledAttestation attestation3 = createPooledAttestation(3);
    builder.aggregate(attestation1);
    builder.aggregate(attestation2);
    builder.aggregate(attestation3);

    final SszBitlist expectedAggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(BITLIST_SIZE, 1, 2, 3);

    final BLSSignature expectedSignature =
        BLS.aggregate(
            List.of(
                attestation1.aggregatedSignature(),
                attestation2.aggregatedSignature(),
                attestation3.aggregatedSignature()));

    assertThat(builder.buildAggregate())
        .isEqualTo(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    attestationSchema.create(
                        expectedAggregationBits, attestationData, expectedSignature))));
  }

  @Test
  public void aggregate_shouldCombineBitsetsAndSignaturesAndIndices() {
    final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(true);
    final PooledAttestation attestation1 = createPooledAttestation(true, 1);
    final PooledAttestation attestation2 = createPooledAttestation(true, 2, 3);
    final PooledAttestation attestation3 = createPooledAttestation(true, 4, 5, 6);
    builder.aggregate(attestation1);
    builder.aggregate(attestation2);
    builder.aggregate(attestation3);

    final SszBitlist expectedAggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(BITLIST_SIZE, 1, 2, 3, 4, 5, 6);

    final BLSSignature expectedSignature =
        BLS.aggregate(
            List.of(
                attestation1.aggregatedSignature(),
                attestation2.aggregatedSignature(),
                attestation3.aggregatedSignature()));

    final ValidatableAttestation expected =
        ValidatableAttestation.from(
            spec,
            attestationSchema.create(expectedAggregationBits, attestationData, expectedSignature));

    assertThat(builder.buildAggregate())
        .isEqualTo(
            new PooledAttestation(
                AttestationBits.of(expected),
                Optional.of(
                    List.of(
                        UInt64.valueOf(101),
                        UInt64.valueOf(102),
                        UInt64.valueOf(103),
                        UInt64.valueOf(104),
                        UInt64.valueOf(105),
                        UInt64.valueOf(106))),
                expectedSignature,
                false));
  }

  @Test
  public void aggregate_shouldThrowIfValidatorIndicesAreRequired() {
    final AggregateAttestationBuilder builder1 = new AggregateAttestationBuilder(true);
    final PooledAttestation attestationWithoutIndices = createPooledAttestation(false, 1);
    final PooledAttestation attestationWithIndices = createPooledAttestation(true, 2, 3);
    assertThatThrownBy(() -> builder1.aggregate(attestationWithoutIndices));

    final AggregateAttestationBuilder builder2 = new AggregateAttestationBuilder(true);
    builder2.aggregate(attestationWithIndices);
    assertThatThrownBy(() -> builder2.aggregate(attestationWithoutIndices));
  }

  @Test
  public void buildAggregate_shouldThrowExceptionIfNoAttestationsAggregated() {
    assertThatThrownBy(builder::buildAggregate).isInstanceOf(IllegalStateException.class);
  }

  private PooledAttestation createPooledAttestation(final int... validators) {
    return createPooledAttestation(false, validators);
  }

  private PooledAttestation createPooledAttestation(
      final boolean includeIndices, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(BITLIST_SIZE, validators);

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(
            spec,
            attestationSchema.create(
                aggregationBits, attestationData, dataStructureUtil.randomSignature()));

    if (includeIndices) {
      return PooledAttestation.fromValidatableAttestation(
          validatableAttestation,
          Arrays.stream(validators).mapToObj(this::validatorBitToValidatorIndex).toList());
    }
    return PooledAttestation.fromValidatableAttestation(validatableAttestation);
  }

  private UInt64 validatorBitToValidatorIndex(final int validatorBit) {
    return UInt64.valueOf(validatorBit + 100);
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PqAttestationContainersTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigPq specConfig =
      SpecConfigPq.required(spec.forMilestone(SpecMilestone.PQ).getConfig());
  private final PqAttestationSchemas schemas =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions())
          .getPqAttestationSchemas();

  @Test
  void attestationHasNoSignatureAndPqCommitteeBits() {
    final AttestationPqSchema schema = schemas.getAttestationSchema();
    assertThat(schema.getFieldNames())
        .containsExactly("aggregation_bits", "data", "committee_bits");
    assertThat(schema.getCommitteeBitsSchema().orElseThrow().getLength())
        .isEqualTo(specConfig.getMaxCommitteesPerSlotPq());

    final AttestationPq attestation = attestation();

    assertThat(schema.sszDeserialize(attestation.sszSerialize())).isEqualTo(attestation);
    assertThat(attestation.getCommitteeIndicesRequired())
        .containsExactly(UInt64.ONE, UInt64.valueOf(3));
    assertThat(attestation.getFirstCommitteeIndex()).isEqualTo(UInt64.ONE);
    assertThatThrownBy(attestation::getAggregateSignature)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                schema.create(
                    attestation.getAggregationBits(),
                    attestation.getData(),
                    BLSSignature.empty(),
                    attestation::getCommitteeBitsRequired))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void indexedAttestationCarriesAProofLimitedToTheStarkProofSize() {
    final IndexedAttestationPqSchema schema = schemas.getIndexedAttestationSchema();
    assertThat(schema.getFieldNames()).containsExactly("attesting_indices", "data", "proof");

    final IndexedAttestationPq indexedAttestation = indexedAttestation(1, 2, 3);

    assertThat(schema.sszDeserialize(indexedAttestation.sszSerialize()))
        .isEqualTo(indexedAttestation);
    assertThat(indexedAttestation.getProof()).isEqualTo(Bytes.of(1, 2, 3));
    assertThatThrownBy(indexedAttestation::getSignature)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                schema.create(
                    indexedAttestation.getAttestingIndices(),
                    indexedAttestation.getData(),
                    Bytes.wrap(new byte[specConfig.getMaxStarkProofSize() + 1])))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void attesterSlashingHoldsPqIndexedAttestations() {
    final AttesterSlashing slashing =
        schemas
            .getAttesterSlashingSchema()
            .create(indexedAttestation(1, 2, 3), indexedAttestation(2, 3, 4));

    final AttesterSlashing roundTripped =
        schemas.getAttesterSlashingSchema().sszDeserialize(slashing.sszSerialize());

    assertThat(roundTripped).isEqualTo(slashing);
    assertThat(roundTripped.getAttestation1()).isInstanceOf(IndexedAttestationPq.class);
    assertThat(roundTripped.getIntersectingValidatorIndices())
        .containsExactlyInAnyOrder(UInt64.valueOf(2), UInt64.valueOf(3));
  }

  @Test
  void aggregateReplacesTheSelectionProofWithAProof() {
    final AggregateAndProofPq.AggregateAndProofPqSchema schema =
        schemas.getAggregateAndProofSchema();
    assertThat(schema.getFieldNames()).containsExactly("aggregator_index", "aggregate", "proof");
    final AggregateAndProofPq aggregate =
        schema.create(UInt64.valueOf(7), attestation(), Bytes.of(9, 9));
    final SignedAggregateAndProofPq signed =
        schemas.getSignedAggregateAndProofSchema().create(aggregate, xmssSignature(5));

    final SignedAggregateAndProofPq roundTripped =
        schemas.getSignedAggregateAndProofSchema().sszDeserialize(signed.sszSerialize());

    assertThat(roundTripped).isEqualTo(signed);
    assertThat(roundTripped.getMessage().getAggregatorIndex()).isEqualTo(UInt64.valueOf(7));
    assertThat(roundTripped.getMessage().getProof()).isEqualTo(Bytes.of(9, 9));
    assertThat(roundTripped.getSignature()).isEqualTo(xmssSignature(5));
  }

  @Test
  void singleAttestationIsXmssSigned() {
    final AttestationData data = dataStructureUtil.randomAttestationData();
    final SingleAttestationPq single =
        new SingleAttestationPq(UInt64.ONE, UInt64.valueOf(2), data, xmssSignature(3));

    // committee_index, attester_index, data, signature
    assertThat(SingleAttestationPq.SSZ_SCHEMA.getSszFixedPartSize())
        .isEqualTo(8 + 8 + AttestationData.SSZ_SCHEMA.getSszFixedPartSize() + 2404);
    assertThat(SingleAttestationPq.SSZ_SCHEMA.sszDeserialize(single.sszSerialize()))
        .isEqualTo(single);
    assertThat(single.getAttesterIndex()).isEqualTo(UInt64.valueOf(2));
    assertThat(single.getSignature()).isEqualTo(xmssSignature(3));
  }

  @Test
  void payloadAttestationMessageIsXmssSigned() {
    final PayloadAttestationMessagePq message =
        schemas
            .getPayloadAttestationMessageSchema()
            .create(
                UInt64.valueOf(4),
                dataStructureUtil.randomPayloadAttestationData(),
                xmssSignature(6));

    assertThat(schemas.getPayloadAttestationMessageSchema().sszDeserialize(message.sszSerialize()))
        .isEqualTo(message);
    assertThat(message.getSignature()).isEqualTo(xmssSignature(6));
  }

  @Test
  void xmssSignaturesHaveTheirFixedSize() {
    assertThat(SszXmssSignatureSchema.INSTANCE.getLength()).isEqualTo(2404);
    assertThatThrownBy(
            () ->
                new SingleAttestationPq(
                    UInt64.ONE,
                    UInt64.ONE,
                    dataStructureUtil.randomAttestationData(),
                    Bytes.wrap(new byte[96])))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private AttestationPq attestation() {
    final AttestationPqSchema schema = schemas.getAttestationSchema();
    final SszBitlist aggregationBits = schema.getAggregationBitsSchema().ofBits(8, 0, 3, 5);
    final SszBitvector committeeBits = schema.getCommitteeBitsSchema().orElseThrow().ofBits(1, 3);
    return schema.create(aggregationBits, dataStructureUtil.randomAttestationData(), committeeBits);
  }

  private IndexedAttestationPq indexedAttestation(final long... indices) {
    final IndexedAttestationPqSchema schema = schemas.getIndexedAttestationSchema();
    return schema.create(
        schema
            .getAttestingIndicesSchema()
            .of(Arrays.stream(indices).mapToObj(UInt64::valueOf).toList()),
        dataStructureUtil.randomAttestationData(),
        Bytes.of(1, 2, 3));
  }

  private static Bytes xmssSignature(final int seed) {
    final byte[] signature = new byte[2404];
    signature[0] = (byte) seed;
    signature[2403] = (byte) seed;
    return Bytes.wrap(signature);
  }
}

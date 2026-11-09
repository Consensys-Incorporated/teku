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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;

public class AttestationBitsElectraTest {
  private final Spec spec = TestSpecFactory.createMainnetElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationSchema<Attestation> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final SingleAttestationSchema singleAttestationSchema =
      spec.getGenesisSchemaDefinitions()
          .toVersionElectra()
          .orElseThrow()
          .getSingleAttestationSchema();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();

  private Int2IntMap committeeSizes;

  @BeforeEach
  void setUp() {
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 2);
    committeeSizes.put(1, 3);
    committeeSizes.put(2, 4);
  }

  /*
  full committee bits structure
  01|234|5678
   */

  @Test
  void aggregateFromEmpty() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    final PooledAttestation attestation = createAttestation(List.of(1), 1, 2);

    final AttestationBits aggregator =
        AttestationBits.fromEmptyFromAttestationSchema(
            attestationSchema, Optional.of(committeeSizes));

    assertThat(aggregator.aggregateWith(attestation)).isTrue();

    assertThat(aggregator.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);

    assertThat(aggregator.getAggregationSszBits().streamAllSetBits()).containsExactly(1, 2);
  }

  @Test
  void cannotAggregateSameCommitteesWithOverlappingAggregates() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(1), 1, 2).bits();

    /*
     012 <- committee 1 indices
     110 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0, 1);

    assertThat(attestation.aggregateWith(otherAttestation)).isFalse();
  }

  @Test
  void aggregateOnSameCommittee() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(1), 1, 2).bits();

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     012 <- committee 1 indices
     111 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 1, 2);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBits() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     01|234 <- committee 0 and 1 indices
     01|010 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1), 1, 3);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|234 <- committee 0 and 1 indices
     11|110 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 1, 2, 3);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBitsVariation() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|011|0001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 3, 4, 8);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|111|0001 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactly(0, 1, 2, 3, 4, 8);
  }

  @Test
  void cannotAggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlapping() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    assertThat(attestation.aggregateWith(otherAttestation)).isFalse();

    // check remained untouched

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 2);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlappingWhenNoCheck() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    // cannot aggregate
    assertThat(attestation.aggregateWith(otherAttestation)).isFalse();

    // calculate the or
    attestation.or(otherAttestation.bits());

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|110|0001 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactly(0, 1, 2, 3, 8);
  }

  @Test
  void aggregateSingleAttestationFillUp_notAggregatable() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     123 <- committee 1 indices
     001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0);

    assertThat(attestation.aggregateWith(otherAttestation)).isFalse();

    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 2);
  }

  @Test
  void aggregateSingleAttestationFillUp() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     123 <- committee 1 indices
     001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 2);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 2, 4);
  }

  @Test
  void aggregateSingleAttestationFillUp_DisjointedCommitteeBitsVariation() {
    /*
     0123 <- committee 2 indices
     0001 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(2), 3).bits();

    /*
     01 <- committee 0 indices
     01 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0), 1);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|2345 <- committee 0 and 2 indices
     01|0001 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(1, 5);
  }

  @Test
  void orSingleAttestationFillUp() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     123 <- committee 1 indices
     001 <- bits
    */
    final AttestationBits otherAttestation = createAttestation(List.of(1), 2).bits();

    attestation.or(otherAttestation);

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 2, 4);
  }

  @Test
  void
      aggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlappingWhenNoCheck2() {
    /*
     0123 <- committee 2 indices
     0100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(2), 1).bits();

    /*
     0123 <- committee 2 indices
     1101 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(2), 0, 1, 3);

    // cannot aggregate
    assertThat(attestation.aggregateWith(otherAttestation)).isFalse();

    // calculate the or
    attestation.or(otherAttestation.bits());

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|110|0001 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 1, 3);
  }

  @Test
  void aggregateOnMultipleDisjointedCommitteeBits() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2).bits();

    /*
     0123 <- committee 1 indices
     1101 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(2), 0, 1, 3);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     10|100|1101 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactly(0, 2, 5, 6, 8);
  }

  @Test
  void aggregateOnMultipleDisjointedCommitteeBitsVariation() {
    /*
     0123 <- committee 2 indices
     0001 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(2), 3).bits();

    /*
     01 <- committee 0 indices
     11 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0), 0, 1);

    assertThat(attestation.aggregateWith(otherAttestation)).isTrue();

    /*
     01|2345 <- committee 0 and 2 indices
     11|0001 <- bits
    */

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(0, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits()).containsExactly(0, 1, 5);
  }

  @Test
  void bigAggregation() {
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 50);
    committeeSizes.put(1, 49);
    committeeSizes.put(2, 50);
    committeeSizes.put(3, 50);
    committeeSizes.put(4, 50);
    committeeSizes.put(5, 50);
    committeeSizes.put(6, 50);
    committeeSizes.put(7, 50);
    committeeSizes.put(8, 50);
    committeeSizes.put(9, 50);
    committeeSizes.put(10, 50);
    committeeSizes.put(11, 50);
    committeeSizes.put(12, 50);
    committeeSizes.put(13, 50);
    committeeSizes.put(14, 50);
    committeeSizes.put(15, 51);

    final AttestationBits attestation =
        createAttestation(
                "1111111111111111",
                """
    11111111111111111111111111111111111111111111111111\
    0000000000000000000000000000000000000000000000010\
    00000000000000000000100000000000000000000000000000\
    00000000000010000000000000000000000000000000000000\
    00000000000000000000000000000000000000000000000001\
    00000000000000000000000000000000000010000000000000\
    00000000000000000010000000000000000000000000000000\
    00000000000000000001000000000000000000000000000000\
    00000000000000010001000000000000000000000000000000\
    00000000000000000000000001000000000000000000000000\
    00000010000000000000000000000000000000000000000000\
    00010000000000000000000000000000000000000000000000\
    00000000000000100000000000000000000000000000000000\
    00000000000010000000000000000000000000000000000000\
    00100000000000000000000000000000000000000000000000\
    000000000000001000000000000000000000000000000000001\
    """)
            .bits();
    final PooledAttestation att =
        createAttestation("0001000000000000", "00000000000000000000000100000000000000000000000000");

    assertThat(attestation.aggregateWith(att)).isTrue();

    final AttestationBits result =
        createAttestation(
                "1111111111111111",
                """
        11111111111111111111111111111111111111111111111111\
        0000000000000000000000000000000000000000000000010\
        00000000000000000000100000000000000000000000000000\
        00000000000010000000000100000000000000000000000000\
        00000000000000000000000000000000000000000000000001\
        00000000000000000000000000000000000010000000000000\
        00000000000000000010000000000000000000000000000000\
        00000000000000000001000000000000000000000000000000\
        00000000000000010001000000000000000000000000000000\
        00000000000000000000000001000000000000000000000000\
        00000010000000000000000000000000000000000000000000\
        00010000000000000000000000000000000000000000000000\
        00000000000000100000000000000000000000000000000000\
        00000000000010000000000000000000000000000000000000\
        00100000000000000000000000000000000000000000000000\
        000000000000001000000000000000000000000000000000001\
        """)
            .bits();

    assertThat(attestation.getCommitteeSszBits()).isEqualTo(result.getCommitteeSszBits());
    assertThat(attestation.getAggregationSszBits()).isEqualTo(result.getAggregationSszBits());
  }

  @Test
  void orIntoEmptyAggregator() {
    final AttestationBits bits =
        AttestationBits.fromEmptyFromAttestationSchema(
            attestationSchema, Optional.of(committeeSizes));

    final AttestationBits otherAttestation = createAttestation(List.of(1), 2).bits();

    bits.or(otherAttestation);

    assertThat(bits.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);
    assertThat(bits.getAggregationSszBits().streamAllSetBits()).containsExactly(2);
  }

  @Test
  void orWithNewCommittee() {
    final AttestationBits attestation = createAttestation(List.of(0), 1).bits();

    final AttestationBits otherAttestation = createAttestation(List.of(1), 2).bits();

    attestation.or(otherAttestation);

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(1, 4);
  }

  @Test
  void orWithExistingCommitteeAddNewBits() {

    final AttestationBits attestation = createAttestation(List.of(1), 0).bits();

    final AttestationBits otherAttestation = createAttestation(List.of(1), 2).bits();

    attestation.or(otherAttestation);

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 2);
  }

  @Test
  void orWithExistingCommitteeOverlapAndNewBits() {
    final AttestationBits attestation = createAttestation(List.of(1), 0, 1).bits();

    final AttestationBits otherAttestation = createAttestation(List.of(1), 1, 2).bits();

    attestation.or(otherAttestation);

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1, 2);
  }

  @Test
  void orWithStrictSubsetAttestation() {
    final AttestationBits attestation = createAttestation(List.of(1), 0, 2).bits();

    final AttestationBits otherAttestation = createAttestation(List.of(1), 0).bits();

    attestation.or(otherAttestation);

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits()).containsExactly(1);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 2);
  }

  @Test
  void orWithMultipleCommitteesMixedNewAndExisting() {
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 3).bits();

    final AttestationBits otherAttestation = createAttestation(List.of(1, 2), 2, 5).bits();

    attestation.or(otherAttestation);

    assertThat(attestation.getCommitteeSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1, 2);
    assertThat(attestation.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 3, 4, 7);
  }

  @Test
  void orAggregatorWithAggregator() {
    final AttestationBits att1Data = createAttestation(List.of(0), 0).bits();

    final AttestationBits att2Data = createAttestation(List.of(0, 1), 1, 2).bits();

    att1Data.or(att2Data);

    assertThat(att1Data.getCommitteeSszBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1);
    assertThat(att1Data.getAggregationSszBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1, 2);

    // aggregator2 should remain unchanged
    assertThat(att2Data.getCommitteeSszBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1);
    assertThat(att2Data.getAggregationSszBits().streamAllSetBits()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void isSuperSetOf_nonPooledAttestation() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final AttestationBits otherAttestation = createAttestation(List.of(0, 1), 0, 2).bits();

    final Attestation other =
        attestationSchema.create(
            otherAttestation.getAggregationSszBits(),
            attestationData,
            dataStructureUtil.randomSignature(),
            otherAttestation::getCommitteeSszBits);

    assertThat(attestation.isSuperSetOf(other)).isTrue();
  }

  @Test
  void isSuperSetOfSingleAttestation1() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01 <- committee 0 indices
     10 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0), 0);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isTrue();
  }

  @Test
  void isSuperSetOfSingleAttestation2() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01 <- committee 0 indices
     01 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0), 1);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isFalse();
  }

  @Test
  void isSuperSetOfSingleAttestation3() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     1234 <- committee 1 indices
     0001 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(2), 0);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isFalse();
  }

  @Test
  void isSuperSetOf1() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isTrue();
  }

  @Test
  void isSuperSetOf2() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isTrue();
  }

  @Test
  void isSuperSetOf3() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     01|234 <- committee 0 and 1 indices
     10|111 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2, 3, 4);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isFalse();
  }

  @Test
  void isSuperSetOf4() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 2, 4).bits();

    /*
     012 <- committee 1 indices
     111 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0, 1, 2);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isFalse();
  }

  @Test
  void isSuperSetOf5() {

    /*
     01 <- committee 0
     10 <- bits
    */
    final AttestationBits attestation = createAttestation(List.of(0), 0).bits();

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isFalse();
  }

  @Test
  void isSuperSetOf6() {

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|111|1111 <- bits
    */
    final AttestationBits attestation =
        createAttestation(List.of(0, 1, 2), 0, 1, 2, 3, 4, 5, 6, 7, 8).bits();

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final PooledAttestation otherAttestation = createAttestation(List.of(1), 0);

    assertThat(attestation.isSuperSetOf(otherAttestation)).isTrue();
  }

  @Test
  void getAggregationSszBits_shouldBeConsistent_singleCommittee() {
    final AttestationBits attestation = createAttestation(List.of(0), 0).bits();

    assertThat(attestation.getAggregationSszBits().size()).isEqualTo(committeeSizes.get(0));

    assertThat(attestation.getAggregationSszBits()).isEqualTo(attestation.getAggregationSszBits());
  }

  @Test
  void getAggregationSszBits_shouldBeConsistent_multiCommittee() {
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 3).bits();

    assertThat(attestation.getAggregationSszBits().size())
        .isEqualTo(committeeSizes.get(0) + committeeSizes.get(1));

    assertThat(attestation.getAggregationSszBits()).isEqualTo(attestation.getAggregationSszBits());
  }

  @Test
  void copy_shouldNotModifyOriginal() {
    final AttestationBits attestation = createAttestation(List.of(0), 0).bits();
    final AttestationBits sameAttestation = createAttestation(List.of(0), 0).bits();

    final AttestationBits copy = attestation.copy();

    assertThat(copy.getCommitteeSszBits()).isEqualTo(attestation.getCommitteeSszBits());
    assertThat(copy.getAggregationSszBits()).isEqualTo(attestation.getAggregationSszBits());
    assertThat(copy).isNotSameAs(attestation);

    assertThat(copy.aggregateWith(createAttestation(List.of(1), 1))).isTrue();

    // the original should not be modified
    assertThat(attestation.getCommitteeSszBits()).isEqualTo(sameAttestation.getCommitteeSszBits());
    assertThat(attestation.getAggregationSszBits())
        .isEqualTo(sameAttestation.getAggregationSszBits());
  }

  @Test
  void equals_shouldBeConsistent() {
    final AttestationBits attestation = createAttestation(List.of(0, 1), 0, 3).bits();
    final AttestationBits bits = createAttestation(List.of(0, 1), 0, 3).bits();

    assertThat(bits).isEqualTo(attestation);
    assertThat(bits.aggregateWith(createAttestation(List.of(0), 1))).isTrue();

    assertThat(bits).isNotEqualTo(attestation);
    assertThat(bits).isNotEqualTo(null);
    assertThat(bits).isNotEqualTo(new Object());
  }

  @Test
  void isExclusivelyFromCommittee_shouldReturnConsistentResult() {
    final AttestationBits fromMultipleCommittees = createAttestation(List.of(0, 1), 0, 3).bits();
    final AttestationBits fromSingleCommittee = createAttestation(List.of(0), 0, 1).bits();
    final AttestationBits singleAttestationFromSingleCommittee =
        createAttestation(List.of(1), 0).bits();

    assertThat(fromMultipleCommittees.isExclusivelyFromCommittee(0)).isFalse();
    assertThat(fromMultipleCommittees.isExclusivelyFromCommittee(1)).isFalse();
    assertThat(fromMultipleCommittees.isExclusivelyFromCommittee(2)).isFalse();

    assertThat(fromSingleCommittee.isExclusivelyFromCommittee(0)).isTrue();
    assertThat(fromSingleCommittee.isExclusivelyFromCommittee(1)).isFalse();
    assertThat(fromSingleCommittee.isExclusivelyFromCommittee(2)).isFalse();

    assertThat(singleAttestationFromSingleCommittee.isExclusivelyFromCommittee(0)).isFalse();
    assertThat(singleAttestationFromSingleCommittee.isExclusivelyFromCommittee(1)).isTrue();
    assertThat(singleAttestationFromSingleCommittee.isExclusivelyFromCommittee(2)).isFalse();
  }

  @Test
  void isFromCommittee_shouldReturnConsistentResult() {
    final AttestationBits fromMultipleCommittees = createAttestation(List.of(0, 1), 0, 3).bits();
    final AttestationBits fromSingleCommittee = createAttestation(List.of(0), 0, 1).bits();
    final AttestationBits singleAttestationFromSingleCommittee =
        createAttestation(List.of(1), 0).bits();

    assertThat(fromMultipleCommittees.isFromCommittee(0)).isTrue();
    assertThat(fromMultipleCommittees.isFromCommittee(1)).isTrue();
    assertThat(fromMultipleCommittees.isFromCommittee(2)).isFalse();

    assertThat(fromSingleCommittee.isFromCommittee(0)).isTrue();
    assertThat(fromSingleCommittee.isFromCommittee(1)).isFalse();
    assertThat(fromSingleCommittee.isFromCommittee(2)).isFalse();

    assertThat(singleAttestationFromSingleCommittee.isFromCommittee(0)).isFalse();
    assertThat(singleAttestationFromSingleCommittee.isFromCommittee(1)).isTrue();
    assertThat(singleAttestationFromSingleCommittee.isFromCommittee(2)).isFalse();
  }

  @Test
  void streamCommitteeIndices__shouldReturnConsistentResult() {
    final AttestationBits fromMultipleCommittees = createAttestation(List.of(0, 1), 0, 3).bits();
    final AttestationBits fromSingleCommittee = createAttestation(List.of(0), 0, 1).bits();
    final AttestationBits singleAttestationFromSingleCommittee =
        createAttestation(List.of(1), 0).bits();

    assertThat(fromMultipleCommittees.streamCommitteeIndices()).containsExactly(0, 1);
    assertThat(fromSingleCommittee.streamCommitteeIndices()).containsExactly(0);
    assertThat(singleAttestationFromSingleCommittee.streamCommitteeIndices()).containsExactly(1);
  }

  @Test
  void getBitCount_shouldReturnConsistentResult() {
    final AttestationBits fromMultipleCommittees = createAttestation(List.of(0, 1), 0, 3).bits();
    final AttestationBits fromSingleCommittee = createAttestation(List.of(0), 0, 1).bits();
    final AttestationBits singleAttestationFromSingleCommittee =
        createAttestation(List.of(1), 0).bits();

    assertThat(fromMultipleCommittees.getBitCount()).isEqualTo(2);
    assertThat(fromSingleCommittee.getBitCount()).isEqualTo(2);
    assertThat(singleAttestationFromSingleCommittee.getBitCount()).isEqualTo(1);
  }

  private PooledAttestation createAttestation(final String commBits, final String aggBits) {
    assertThat(commBits).matches(Pattern.compile("^[0-1]+$"));
    assertThat(aggBits).matches(Pattern.compile("^[0-1]+$"));
    final List<Integer> commBitList =
        IntStream.range(0, commBits.length())
            .mapToObj(index -> commBits.charAt(index) == '1' ? index : -1)
            .filter(index -> index >= 0)
            .toList();
    final int[] aggBitList =
        IntStream.range(0, aggBits.length())
            .map(index -> aggBits.charAt(index) == '1' ? index : -1)
            .filter(index -> index >= 0)
            .toArray();
    return createAttestation(commBitList, aggBitList);
  }

  private PooledAttestation createAttestation(
      final List<Integer> committeeIndices, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(
                committeeSizes.int2IntEntrySet().stream()
                    .filter(entry -> committeeIndices.contains(entry.getIntKey()))
                    .mapToInt(Entry::getIntValue)
                    .sum(),
                validators);
    final Supplier<SszBitvector> committeeBits =
        () -> attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndices);

    final ValidatableAttestation attestation = mock(ValidatableAttestation.class);

    var realAttestation =
        attestationSchema.create(
            aggregationBits, attestationData, dataStructureUtil.randomSignature(), committeeBits);
    when(attestation.getAttestation()).thenReturn(realAttestation);

    if (validators.length > 1) {
      when(attestation.getUnconvertedAttestation()).thenReturn(realAttestation);
    } else {
      when(attestation.getUnconvertedAttestation())
          .thenReturn(
              singleAttestationSchema.create(
                  UInt64.valueOf(committeeIndices.getFirst()),
                  UInt64.valueOf(validators[0]),
                  attestationData,
                  dataStructureUtil.randomSignature()));
    }

    when(attestation.getCommitteesSize()).thenReturn(Optional.of(committeeSizes));

    return PooledAttestation.fromValidatableAttestation(attestation);
  }
}

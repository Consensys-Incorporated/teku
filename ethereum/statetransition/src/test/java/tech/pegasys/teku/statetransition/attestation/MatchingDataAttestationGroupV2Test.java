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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class MatchingDataAttestationGroupV2Test {
  private static final UInt64 SLOT = UInt64.valueOf(1234);

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);

  private final LongSupplier nanoSupplier = mock(LongSupplier.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AttestationSchema<Attestation> attestationSchema;

  private AttestationData attestationData;

  private MatchingDataAttestationGroupV2 group;
  private Int2IntMap committeeSizes;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    attestationSchema = spec.getGenesisSchemaDefinitions().getAttestationSchema();
    dataStructureUtil = specContext.getDataStructureUtil();
    attestationData = dataStructureUtil.randomAttestationData(SLOT);
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 10);
    committeeSizes.put(1, 10);
    when(nanoSupplier.getAsLong()).thenReturn(0L);
    group =
        new MatchingDataAttestationGroupV2(
            spec, nanoSupplier, attestationData, Optional.of(committeeSizes));
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyInitially() {
    assertThat(group.isEmpty()).isTrue();
  }

  @TestTemplate
  public void isEmpty_shouldNotBeEmptyWhenAnAttestationIsAdded() {
    addPooledAttestation(1);
    assertThat(group.isEmpty()).isFalse();
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyAfterAttestationRemoved() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    final Attestation copy = attestationSchema.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    verifyGroupContainsExactly(); // empty
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_multipleCallsShouldAggregate() {

    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Add some attestations
    final PooledAttestation attestation3 = addPooledAttestation(3);
    addPooledAttestation(1, 2); // This will be an aggregate, not single

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    assertThat(numRemoved).isEqualTo(0); // Attestation (1) is covered by (1,2) which is still there
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
  }

  @TestTemplate
  public void
      onAttestationIncludedInBlock_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);
    final PooledAttestation attestation3 = addPooledAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                committeeSizes, toAttestation(attestation1), toAttestation(attestation2)));

    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
    assertThat(numRemoved).isEqualTo(2);
  }

  @TestTemplate
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Create attestation to be added / ignored
    final PooledAttestation attestationToIgnore = createPooledAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore, Optional.empty())).isFalse();
    verifyGroupContainsExactly(); // empty
  }

  @TestTemplate
  public void add_shouldIgnoreDuplicateAttestations() {
    final PooledAttestation attestation = addPooledAttestation(1, 2);
    final PooledAttestation copy =
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(
                spec,
                attestationSchema.sszDeserialize(toAttestation(attestation).sszSerialize()),
                committeeSizes),
            validatorBitToValidatorIndex(1, 2));

    assertThat(group.add(copy, Optional.empty())).isFalse();

    verifyGroupContainsExactly(toPooledAttestationWithData(attestation));
  }

  // --- Tests for streamForApiRequest ---
  @TestTemplate
  public void streamForApiRequest_shouldAggregateDisjointAttestations(
      final SpecContext specContext) {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);

    final Attestation expectedAggregate =
        aggregateAttestations(
            committeeSizes, toAttestation(attestation1), toAttestation(attestation2));

    verifyStreamForApiRequest(
        Optional.empty(),
        isElectra(specContext),
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expectedAggregate, committeeSizes))));
  }

  @TestTemplate
  public void streamForApiRequest_shouldPrioritizeLargerAndAggregateNonOverlapping(
      final SpecContext specContext) {
    final PooledAttestation bigAttestation =
        addPooledAttestation(1, 3, 5, 7); // Aggregate (4 validators)
    final PooledAttestation mediumAttestation =
        addPooledAttestation(3, 5, 9); // Aggregate (3 validators)
    final PooledAttestation littleAttestation =
        addPooledAttestation(2, 4); // Aggregate (2 validators)

    final Attestation combinedBigLittle =
        aggregateAttestations(
            committeeSizes, toAttestation(bigAttestation), toAttestation(littleAttestation));

    verifyStreamForApiRequest(
        Optional.empty(),
        isElectra(specContext),
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec, combinedBigLittle, committeeSizes))), // Aggregate of (1,2,3,4,5,7)
        toPooledAttestationWithData(
            mediumAttestation) // Separate (3,5,9) because 9 is new, but 3,5 overlap
        );
  }

  @TestTemplate
  public void streamForApiRequest_shouldReturnOverlappingAttestationsSeparately(
      final SpecContext specContext) {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addPooledAttestation(1, 2, 3);

    // These overlap but neither is a superset of the other. They should be returned separately.
    verifyStreamForApiRequest(
        Optional.empty(),
        isElectra(specContext),
        toPooledAttestationWithData(attestation1),
        toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  void streamForApiRequest_shouldAggregateLeavingNoRedundantParts(final SpecContext specContext) {
    final PooledAttestation useful1 = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2, 4); // This is (2,4). Partially overlaps with (1,2,3) and (4)
    final PooledAttestation useful2 = addPooledAttestation(4);

    // Expect (1,2,3) and (4) to be aggregated into (1,2,3,4).
    // The attestation (2,4) becomes redundant.
    final PooledAttestationWithData expected =
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        committeeSizes, toAttestation(useful1), toAttestation(useful2)),
                    committeeSizes)));
    verifyStreamForApiRequest(Optional.empty(), isElectra(specContext), expected);
  }

  @TestTemplate
  void streamForApiRequest_electra_withCommitteeIndex_returnsMatchingAggregated(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    // C0 attestations
    final PooledAttestation singleC0V1 = addPooledAttestation(Optional.of(0), 1); // Single
    final PooledAttestation aggC0V23 = addPooledAttestation(Optional.of(0), 2, 3); // Aggregate
    // C1 attestations
    final PooledAttestation singleC1V4 = addPooledAttestation(Optional.of(1), 4); // Single

    // Request for committee 0
    // Expected: aggregate of singleC0V1 and aggC0V23
    final Attestation expectedForC0 =
        aggregateAttestations(committeeSizes, toAttestation(singleC0V1), toAttestation(aggC0V23));

    verifyStreamForApiRequest(
        Optional.of(UInt64.ZERO),
        true,
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expectedForC0, committeeSizes))));

    // Request for committee 1
    // Expected: single from singleC1V4
    verifyStreamForApiRequest(
        Optional.of(UInt64.ONE), true, toPooledAttestationWithData(singleC1V4));
  }

  // --- Tests for streamForAggregationProduction ---

  @TestTemplate
  void streamForAggregationProduction_phase0_noCommitteeIndex_returnsAggregatedFromAggregates(
      final SpecContext specContext) {
    specContext.assumeIsNotOneOf(ELECTRA);

    final PooledAttestation att1 =
        addPooledAttestation(1, 2); // Goes to attestationsByValidatorCount
    final PooledAttestation att2 = addPooledAttestation(3); // Goes to attestationsByValidatorCount
    final Attestation expected = aggregateAttestations(toAttestation(att1), toAttestation(att2));

    verifyStreamForAggregationProductionPhase0ContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  void streamForAggregationProduction_phase0_committeeIndexShouldBeIgnoredAndReturnAggregates(
      final SpecContext specContext) {
    specContext.assumeIsNotOneOf(ELECTRA);

    final PooledAttestation att1 =
        addPooledAttestation(1, 2); // Goes to attestationsByValidatorCount
    final PooledAttestation att2 = addPooledAttestation(3); // Goes to attestationsByValidatorCount
    final Attestation expected = aggregateAttestations(toAttestation(att1), toAttestation(att2));

    verifyStreamForAggregationProductionContainsExactly(
        UInt64.ONE,
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  void streamForAggregationProduction_electra_noCommitteeIndex_throwsException(
      final SpecContext specContext) {
    specContext.assumeElectraActive();

    assertThatThrownBy(this::verifyStreamForAggregationProductionPhase0ContainsExactly)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Committee index must be present if committee bits are required");
  }

  @TestTemplate
  void streamForAggregationProduction_electra_withCommitteeIndex_aggregatesMatchingSingles(
      final SpecContext specContext) {
    specContext.assumeElectraActive();

    final PooledAttestation singleC0V1 = addPooledAttestation(Optional.of(0), 1);
    final PooledAttestation singleC0V2 = addPooledAttestation(Optional.of(0), 2);

    // this will be ignored since it is not a single attestation
    addPooledAttestation(Optional.of(0), 5, 6);

    // single att on committee 1
    addPooledAttestation(Optional.of(1), 3);

    final Attestation expectedForC0 =
        aggregateAttestations(committeeSizes, toAttestation(singleC0V1), toAttestation(singleC0V2));
    verifyStreamForAggregationProductionContainsExactly(
        UInt64.ZERO,
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expectedForC0, committeeSizes))));
  }

  @TestTemplate
  void streamForAggregationProduction_electra_withCommitteeIndex_noMatchingSingles_returnsEmpty(
      final SpecContext specContext) {
    specContext.assumeElectraActive();

    addPooledAttestation(Optional.of(0), 1, 2);
    addPooledAttestation(Optional.of(1), 3);

    // attestation for C0 is an aggregate, not a single.
    verifyStreamForAggregationProductionContainsExactly(UInt64.ZERO); // Expect empty
  }

  // --- Tests for streamAggregatesForBlockProduction ---
  @TestTemplate
  public void
      streamAggregatesForBlockProduction_electra_shouldSkipSinglesAndReturnAggregatesWhenThereAreAggregates(
          final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9);
    addPooledAttestation(Optional.of(0), 2);

    // we don't expect the single attestation to be returned
    // in the block production flow they are used during fillUp phase only

    verifyStreamAggregatesForBlockProductionContainsExactly(
        toPooledAttestationWithData(bigAttestation),
        toPooledAttestationWithData(mediumAttestation));
  }

  @TestTemplate
  public void streamAggregatesForBlockProduction_shouldOmitRedundantSmallerAttestations() {
    final PooledAttestation aggregate = addPooledAttestation(1, 2, 3, 4);
    addPooledAttestation(2, 3);

    verifyStreamAggregatesForBlockProductionContainsExactly(toPooledAttestationWithData(aggregate));
  }

  @TestTemplate
  void streamAggregatesForBlockProduction_aggregatesNonOverlapping() {
    final PooledAttestation att1 = addPooledAttestation(1, 2);
    final PooledAttestation att2 = addPooledAttestation(3, 4);
    final Attestation expected =
        aggregateAttestations(committeeSizes, toAttestation(att1), toAttestation(att2));
    verifyStreamAggregatesForBlockProductionContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  // --- Tests for streamStreamSingleAttestationsForBlockProduction ---

  @TestTemplate
  public void
      streamStreamSingleAttestationsForBlockProduction_electra_shouldConsiderSinglesAndReturnAggregatesWhenThereAreNoAggregates(
          final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation single0 = addPooledAttestation(Optional.of(0), 2);
    final PooledAttestation single1 = addPooledAttestation(Optional.of(1), 3);

    final Attestation expectedSAAggregate =
        aggregateAttestations(committeeSizes, toAttestation(single0), toAttestation(single1));

    verifyStreamSingleAttestationsForBlockProductionContainsExactly(
        toPooledAttestationWithData(expectedSAAggregate));
  }

  @TestTemplate
  public void
      streamStreamSingleAttestationsForBlockProduction_electra_shouldNotConsiderSinglesAndReturnAggregatesWhenThereAreAggregates(
          final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(0), 3, 4);

    addPooledAttestation(Optional.of(0), 2);
    addPooledAttestation(Optional.of(1), 3);

    verifyStreamSingleAttestationsForBlockProductionContainsExactly();
  }

  // --- Tests for fillUpAggregation ---
  @TestTemplate
  void fillUp_noSingleAttestations_returnsOriginal(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation initialAgg = createPooledAttestation(Optional.of(0), 1, 2);
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);
    assertThat(result).isEqualTo(toPooledAttestationWithData(initialAgg));
  }

  @TestTemplate
  void fillUp_withNonOverlappingSingleAttestations_aggregatesThem(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation singleC0V3 = addPooledAttestation(Optional.of(0), 3);
    final PooledAttestation singleC1V4 =
        addPooledAttestation(Optional.of(1), 4); // This single has committee_bits=1

    final PooledAttestation initialAgg =
        addPooledAttestation(Optional.of(0), 1, 2); // Initial agg is for C0
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);

    final Attestation expectedFullAggregate =
        aggregateAttestations(
            committeeSizes,
            toAttestation(initialAgg),
            toAttestation(singleC0V3),
            toAttestation(singleC1V4));

    assertThat(result).isEqualTo(toPooledAttestationWithData(expectedFullAggregate));
  }

  @TestTemplate
  void fillUp_withSingleAttestationsPartiallyOverlappingInput_aggregatesNewBits(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(0), 2); // Single on C0 for V2 (overlaps with initial)
    final PooledAttestation singleC0V3 =
        addPooledAttestation(Optional.of(0), 3); // Single on C0 for V3 (new)
    final PooledAttestation singleC1V3 =
        addPooledAttestation(Optional.of(1), 3); // Single on C1 for V3 (new)
    addPooledAttestation(Optional.of(1), 4, 5); // aggregated (to be ignored)

    final PooledAttestation initialAgg =
        createPooledAttestation(Optional.of(0), 1, 2); // C0, agg {1,2}
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);

    final Attestation expectedAggregate =
        aggregateAttestations(
            committeeSizes,
            toAttestation(initialAgg),
            toAttestation(singleC0V3),
            toAttestation(singleC1V3));

    assertThat(result).isEqualTo(toPooledAttestationWithData(expectedAggregate));
  }

  @TestTemplate
  void fillUp_withTimeLimit_stopsAggregating(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final long timeLimitNanos = 1_000_000_000L;
    final PooledAttestation single1 = addPooledAttestation(Optional.of(0), 3);
    addPooledAttestation(Optional.of(1), 4);

    final PooledAttestation initialAgg = addPooledAttestation(Optional.of(0), 1, 2);
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    // the first fillup is immediately done, only the second is subject to time limit,
    // so setup the time to be in the future from the beginning
    when(nanoSupplier.getAsLong()).thenReturn(timeLimitNanos + 10);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, timeLimitNanos);

    final Attestation expectedWithSingle1 =
        aggregateAttestations(committeeSizes, toAttestation(initialAgg), toAttestation(single1));

    assertThat(result).isEqualTo(toPooledAttestationWithData(expectedWithSingle1));
  }

  @TestTemplate
  void fillUp_shouldNotFillupIfNoAggregatesArePresent(final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(0), 3);
    addPooledAttestation(Optional.of(1), 4);

    final PooledAttestation initialAgg = createPooledAttestation(Optional.of(0), 1, 2);
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);

    final Attestation expectedInitial = toAttestation(initialAgg);

    assertThat(result).isEqualTo(toPooledAttestationWithData(expectedInitial));
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamAggregatesForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6, 7)));

    assertThat(group.size()).isZero();
    verifyGroupContainsExactly();
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamAggregatesForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Validator 7 is still relevant (from attestation2)
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(createPooledAttestation(1), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(1, 2, 3, 4, 5, 6), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(2, 3), Optional.empty())).isFalse();
  }

  @TestTemplate
  void onReorg_shouldAllowReadingAttestationsThatAreNoLongerRedundant() {
    final PooledAttestation attestation = createPooledAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(attestation, Optional.empty())).isFalse();

    group.onReorg(UInt64.ZERO);

    assertThat(group.add(attestation, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation));
  }

  @TestTemplate
  void onReorg_shouldNotAllowReadingAttestationsThatAreStillRedundant() {
    final PooledAttestation attestation1 = createPooledAttestation(3, 4);
    final PooledAttestation attestation2 = createPooledAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(2, 3, 4)));
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), toAttestation(createPooledAttestation(1, 2, 3, 4)));

    assertThat(group.add(attestation1, Optional.empty())).isFalse();
    assertThat(group.add(attestation2, Optional.empty())).isFalse();

    group.onReorg(UInt64.valueOf(2)); // Block at slot 3 removed, block at slot 1 remains.

    // Attestation from slot 1 (2,3,4) still makes (3,4) redundant.
    assertThat(group.add(attestation1, Optional.empty())).isFalse();

    // Attestation (1,2,3,4) has validator 1, which is not in (2,3,4). So it can be added.
    assertThat(group.add(attestation2, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  public void size() {
    assertThat(group.size()).isEqualTo(0);
    final PooledAttestationWithData attestation1Data =
        toPooledAttestationWithData(addPooledAttestation(1));
    assertThat(group.size()).isEqualTo(1);
    final PooledAttestationWithData attestation2Data =
        toPooledAttestationWithData(addPooledAttestation(2));
    assertThat(group.size()).isEqualTo(2);
    addPooledAttestation(3, 4);
    assertThat(group.size()).isEqualTo(3);
    addPooledAttestation(1, 2);
    assertThat(group.size()).isEqualTo(4);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                committeeSizes,
                attestation1Data.toAttestation(attestationSchema),
                attestation2Data.toAttestation(attestationSchema)));

    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
  }

  // --- Tests for aggregates whose signature is the point at infinity ---

  @TestTemplate
  void streamAggregatesForBlockProduction_shouldDropAggregateWithInfinitySignature() {
    addPooledAttestation(Optional.empty(), sign(KEYS.p()), 1, 2);
    addPooledAttestation(Optional.empty(), sign(KEYS.minusP()), 3, 4);

    verifyStreamAggregatesForBlockProductionContainsExactly(); // empty
  }

  @TestTemplate
  void streamAggregatesForBlockProduction_shouldKeepAggregateWhenSignatureIsNotInfinity() {
    final PooledAttestation att1 = addPooledAttestation(Optional.empty(), sign(KEYS.p()), 1, 2);
    final PooledAttestation att2 =
        addPooledAttestation(Optional.empty(), sign(KEYS.minusP()), 3, 4);
    final PooledAttestation att3 = addPooledAttestation(Optional.empty(), sign(KEYS.q()), 5, 6);

    final Attestation expected =
        aggregateAttestations(
            committeeSizes, toAttestation(att1), toAttestation(att2), toAttestation(att3));
    // P + (-P) + Q == Q
    assertThat(expected.getAggregateSignature()).isEqualTo(sign(KEYS.q()));
    verifyStreamAggregatesForBlockProductionContainsExactly(toPooledAttestationWithData(expected));
  }

  @TestTemplate
  void streamSingleAttestationsForBlockProduction_shouldDropAggregateWithInfinitySignature(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1);
    addPooledAttestation(Optional.of(1), sign(KEYS.minusP()), 2);

    verifyStreamSingleAttestationsForBlockProductionContainsExactly(); // empty
  }

  @TestTemplate
  void streamSingleAttestationsForBlockProduction_shouldKeepAggregateWhenSignatureIsNotInfinity(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation single1 = addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1);
    final PooledAttestation single2 = addPooledAttestation(Optional.of(0), sign(KEYS.minusP()), 2);
    final PooledAttestation single3 = addPooledAttestation(Optional.of(1), sign(KEYS.q()), 3);

    final Attestation expected =
        aggregateAttestations(
            committeeSizes, toAttestation(single1), toAttestation(single2), toAttestation(single3));
    verifyStreamSingleAttestationsForBlockProductionContainsExactly(
        toPooledAttestationWithData(expected));
  }

  @TestTemplate
  void streamForAggregationProduction_electra_shouldNotProduceAggregateWithInfinitySignature(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1);
    addPooledAttestation(Optional.of(0), sign(KEYS.minusP()), 2);

    verifyStreamForAggregationProductionContainsExactly(UInt64.ZERO); // empty
  }

  @TestTemplate
  void streamForAggregationProduction_electra_shouldProduceAggregateWhenSignatureIsNotInfinity(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation single1 = addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1);
    final PooledAttestation single2 = addPooledAttestation(Optional.of(0), sign(KEYS.minusP()), 2);
    final PooledAttestation single3 = addPooledAttestation(Optional.of(0), sign(KEYS.q()), 3);

    final Attestation expected =
        aggregateAttestations(
            committeeSizes, toAttestation(single1), toAttestation(single2), toAttestation(single3));
    verifyStreamForAggregationProductionContainsExactly(
        UInt64.ZERO, toPooledAttestationWithData(expected));
  }

  @TestTemplate
  void streamForAggregationProduction_phase0_shouldSkipAggregateWithInfinitySignature(
      final SpecContext specContext) {
    specContext.assumeIsNotOneOf(ELECTRA);
    // the largest candidates are aggregated first and give the infinity signature
    addPooledAttestation(Optional.empty(), sign(KEYS.p()), 1, 2, 3);
    addPooledAttestation(Optional.empty(), sign(KEYS.minusP()), 4, 5, 6);
    // overlaps the first candidate so ends up in the next aggregate
    final PooledAttestation nextCandidate =
        addPooledAttestation(Optional.empty(), sign(KEYS.q()), 1, 7);

    verifyStreamForAggregationProductionPhase0ContainsExactly(
        toPooledAttestationWithData(nextCandidate));
  }

  @TestTemplate
  void streamForApiRequest_shouldDropAggregateWithInfinitySignature(final SpecContext specContext) {
    addPooledAttestation(Optional.empty(), sign(KEYS.p()), 1, 2);
    addPooledAttestation(Optional.empty(), sign(KEYS.minusP()), 3, 4);

    verifyStreamForApiRequest(Optional.empty(), isElectra(specContext)); // empty
  }

  @TestTemplate
  void fillUp_shouldReturnOriginalWhenFillUpWouldGiveInfinitySignature(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    addPooledAttestation(Optional.of(1), sign(KEYS.minusP()), 3);

    final PooledAttestation initialAgg = addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1, 2);
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);

    assertThat(result).isEqualTo(initial);
  }

  @TestTemplate
  void fillUp_shouldFillUpWhenSignatureIsNotInfinity(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation single1 = addPooledAttestation(Optional.of(1), sign(KEYS.minusP()), 3);
    final PooledAttestation single2 = addPooledAttestation(Optional.of(1), sign(KEYS.q()), 4);

    final PooledAttestation initialAgg = addPooledAttestation(Optional.of(0), sign(KEYS.p()), 1, 2);
    final PooledAttestationWithData initial = toPooledAttestationWithData(initialAgg);

    final PooledAttestationWithData result = group.fillUpAggregation(initial, Long.MAX_VALUE);

    final Attestation expected =
        aggregateAttestations(
            committeeSizes,
            toAttestation(initialAgg),
            toAttestation(single1),
            toAttestation(single2));
    assertThat(toPooledAttestationWithDataWithSortedValidatorIndices(result))
        .isEqualTo(toPooledAttestationWithData(expected));
  }

  private BLSSignature sign(final BLSKeyPair keyPair) {
    // the group never verifies signatures; any message shared by all participants will do
    return BLS.sign(keyPair.getSecretKey(), attestationData.hashTreeRoot());
  }

  void verifyGroupContainsExactly(final PooledAttestationWithData... expectedAttestations) {
    // streamForApiRequest with no committee index is the only stream that gives us all attestations
    verifyStreamForApiRequest(
        Optional.empty(),
        spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(ELECTRA),
        expectedAttestations);
  }

  void verifyStreamForAggregationProductionContainsExactly(
      final UInt64 committeeIndex, final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.of(committeeIndex), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForAggregationProductionPhase0ContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.empty(), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamAggregatesForBlockProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamAggregatesForBlockProduction(Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamSingleAttestationsForBlockProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamSingleAttestationsForBlockProduction(Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForApiRequest(
      final Optional<UInt64> committeeIndex,
      final boolean requiresCommitteeBits,
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForApiRequest(committeeIndex, requiresCommitteeBits)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  // This is needed because our underlining data structure stores the validator indices in a list
  // for efficiency,
  // but it is logically a set, so to be able to make equality checks, we need to sort the indices
  private PooledAttestationWithData toPooledAttestationWithDataWithSortedValidatorIndices(
      final PooledAttestationWithData attestation) {
    return new PooledAttestationWithData(
        attestation.data(),
        new PooledAttestation(
            attestation.pooledAttestation().bits(),
            attestation.pooledAttestation().validatorIndices().map(TreeSet::new).map(List::copyOf),
            attestation.pooledAttestation().aggregatedSignature(),
            attestation.pooledAttestation().isSingleAttestation()));
  }

  private PooledAttestation addPooledAttestation(final int... validators) {
    return addPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation addPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    return addPooledAttestation(committeeIndex, dataStructureUtil.randomSignature(), validators);
  }

  private PooledAttestation addPooledAttestation(
      final Optional<Integer> committeeIndex,
      final BLSSignature signature,
      final int... validators) {
    final PooledAttestation attestation =
        createPooledAttestation(committeeIndex, signature, validators);
    final boolean added = group.add(attestation, Optional.empty());
    assertThat(added).isTrue();
    return attestation;
  }

  private PooledAttestation createPooledAttestation(final int... validators) {
    return createPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation createPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    return createPooledAttestation(committeeIndex, dataStructureUtil.randomSignature(), validators);
  }

  private PooledAttestation createPooledAttestation(
      final Optional<Integer> committeeIndex,
      final BLSSignature signature,
      final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(10, validators);
    final boolean isElectra = spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA);
    final Supplier<SszBitvector> committeeBits;
    final Optional<Attestation> singleAttestation;
    final int resolvedCommitteeIndex = committeeIndex.orElse(0);

    if (validators.length == 1 && isElectra) {
      singleAttestation =
          Optional.of(
              spec.getGenesisSchemaDefinitions()
                  .toVersionElectra()
                  .orElseThrow()
                  .getSingleAttestationSchema()
                  .create(
                      UInt64.valueOf(resolvedCommitteeIndex),
                      UInt64.valueOf(validators[0]),
                      attestationData,
                      signature));
    } else {
      singleAttestation = Optional.empty();
    }

    if (spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      committeeBits =
          () ->
              attestationSchema
                  .getCommitteeBitsSchema()
                  .orElseThrow()
                  .ofBits(resolvedCommitteeIndex);
    } else {
      committeeBits = () -> null;
    }

    final Attestation attestation =
        attestationSchema.create(aggregationBits, attestationData, signature, committeeBits);

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, singleAttestation.orElse(attestation), committeeSizes);

    singleAttestation.ifPresent(
        __ -> validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation));

    return PooledAttestation.fromValidatableAttestation(
        validatableAttestation, validatorBitToValidatorIndex(committeeIndex, validators));
  }

  private List<UInt64> validatorBitToValidatorIndex(
      final Optional<Integer> committeeIndex, final int... validatorBits) {
    final int committeeOffset =
        committeeIndex
            .map(
                index ->
                    IntStream.range(0, index)
                        .map(i -> committeeSizes.get(i))
                        .reduce(0, Integer::sum))
            .orElse(0);

    return Arrays.stream(validatorBits)
        .sorted() // make sure we sort validators so that we can compare the list without converting
        // it to a set
        .mapToObj(bit -> UInt64.valueOf(bit + 100 + committeeOffset))
        .toList();
  }

  private List<UInt64> validatorBitToValidatorIndex(final int... validatorBits) {
    return validatorBitToValidatorIndex(Optional.empty(), validatorBits);
  }

  private List<UInt64> validatorBitToValidatorIndex(final AttestationBits bits) {
    if (!bits.requiresCommitteeBits()) {
      return validatorBitToValidatorIndex(
          bits.getAggregationSszBits().getAllSetBits().toIntArray());
    }

    // only 2 committees are supported
    assertThat(committeeSizes.keySet()).isEqualTo(Set.of(0, 1));
    final IntList committeeBits = bits.getCommitteeSszBits().getAllSetBits();
    assertThat(committeeBits.size()).isLessThanOrEqualTo(2);

    final List<UInt64> result = new ArrayList<>();
    int offset = 0;
    if (committeeBits.contains(0)) {
      result.addAll(
          validatorBitToValidatorIndex(
              Optional.of(0),
              bits.getAggregationSszBits().getAsBitSet(0, committeeSizes.get(0)).stream()
                  .toArray()));
      offset = committeeSizes.get(0);
    }
    if (committeeBits.contains(1)) {
      result.addAll(
          validatorBitToValidatorIndex(
              Optional.of(1),
              bits
                  .getAggregationSszBits()
                  .getAsBitSet(offset, committeeSizes.get(1) + offset)
                  .stream()
                  .toArray()));
    }

    return result;
  }

  private Attestation toAttestation(final PooledAttestation pooledAttestation) {
    return attestationSchema.create(
        pooledAttestation.bits().getAggregationSszBits(),
        attestationData,
        pooledAttestation.aggregatedSignature(),
        pooledAttestation.bits()::getCommitteeSszBits); // Supplier for committee bits
  }

  private PooledAttestationWithData toPooledAttestationWithData(
      final PooledAttestation pooledAttestation) {
    // Use the attestation created by toAttestation to ensure consistency if PooledAttestation
    // doesn't directly hold a full Attestation object in the exact schema form.
    return toPooledAttestationWithData(toAttestation(pooledAttestation));
  }

  private PooledAttestationWithData toPooledAttestationWithData(final Attestation attestation) {
    return new PooledAttestationWithData(
        attestationData,
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(spec, attestation, committeeSizes),
            validatorBitToValidatorIndex(
                AttestationBits.of(attestation, Optional.of(committeeSizes)))));
  }

  private boolean isElectra(final SpecContext specContext) {
    return specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA);
  }
}

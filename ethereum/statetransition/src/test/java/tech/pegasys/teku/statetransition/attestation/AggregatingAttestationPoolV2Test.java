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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.RewardBasedAttestationSorterFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class AggregatingAttestationPoolV2Test extends AggregatingAttestationPoolTest {

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);

  @Override
  AggregatingAttestationPool instantiatePool(
      final Spec spec, final RecentChainData recentChainData, final int maxAttestations) {
    return instantiatePool(
        spec,
        recentChainData,
        maxAttestations,
        () -> 0L,
        RewardBasedAttestationSorterFactory.NOOP,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Optional.of(ZERO));
  }

  AggregatingAttestationPool instantiatePool(
      final LongSupplier nanosSupplier,
      final int maxBlockAggregationTimeMillis,
      final int maxTotalBlockAggregationTimeMillis) {
    return instantiatePool(
        mockSpec,
        mockRecentChainData,
        10,
        nanosSupplier,
        RewardBasedAttestationSorterFactory.NOOP,
        maxBlockAggregationTimeMillis,
        maxTotalBlockAggregationTimeMillis,
        Optional.of(ZERO));
  }

  AggregatingAttestationPool instantiatePool(
      final Spec spec,
      final RecentChainData recentChainData,
      final int maxAttestations,
      final LongSupplier nanosSupplier,
      final RewardBasedAttestationSorterFactory sorterFactory,
      final int maxBlockAggregationTimeMillis,
      final int maxTotalBlockAggregationTimeMillis,
      final Optional<UInt64> onAttestationIncludedInBlockSlot) {
    var pool =
        new AggregatingAttestationPoolV2(
            spec,
            recentChainData,
            new NoOpMetricsSystem(),
            maxAttestations,
            nanosSupplier,
            sorterFactory,
            maxBlockAggregationTimeMillis,
            maxTotalBlockAggregationTimeMillis);

    onAttestationIncludedInBlockSlot.ifPresent(
        slot -> pool.onAttestationsIncludedInBlock(slot, List.of()));

    return pool;
  }

  @TestTemplate
  @Override
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = createAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2);

    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result).contains(aggregateAttestations(committeeSizes, attestation1, attestation2));
  }

  @TestTemplate
  @Override
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {
    // this does not apply since we only deal with single attestation, which cannot partially
    // overlap
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldFillupOnlyFirstAggregateFromSameMatchingData() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);

    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestationBestAggregate =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestationAggregate = addAttestationFromValidators(attestationData, 1, 2, 5);

    final Attestation singleAttestation = addAttestationFromValidators(attestationData, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(
            aggregateAttestations(committeeSizes, attestationBestAggregate, singleAttestation),
            attestationAggregate);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotTryToFillupIfTimeLimitIsExceeded() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);

    // by passing 0 as maxTotalBlockAggregationTimeMillis we give no time to fillup
    aggregatingPool = instantiatePool(System::nanoTime, Integer.MAX_VALUE, 0);

    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestationBestAggregate =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    addAttestationFromValidators(attestationData, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(attestationBestAggregate);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotContinueAggregatingIfTimeLimitIsExceeded() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);

    final LongSupplier nanosSupplier = mock(LongSupplier.class);
    when(nanosSupplier.getAsLong())
        .thenReturn(
            0L, // first call to get now
            1_000_000L, // 1 ms, first aggregation time check
            3_000_000L // 3 ms, second aggregation time - this is the limit
            );

    final int maxBlockAggregationTimeMillis = 2; // less than 3 ms

    // by passing 0 as maxBlockAggregationTimeMillis we limit the time to aggregate
    aggregatingPool =
        instantiatePool(nanosSupplier, maxBlockAggregationTimeMillis, Integer.MAX_VALUE);

    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestationBestAggregate =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4);

    // let's add another attestation which overlaps, so that it should be added to another aggregate
    // in the result, but we will not have time to get it
    addAttestationFromValidators(attestationData, 1, 2, 5);

    // the fillup will anyway happen since the total time limit won't be reached
    final Attestation singleAttestation = addAttestationFromValidators(attestationData, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(
            aggregateAttestations(committeeSizes, attestationBestAggregate, singleAttestation));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldRespectSorter() {
    var attestations =
        List.of(
            createAttestation(createAttestationData(), 1, 2, 3, 4),
            createAttestation(createAttestationData(), 4, 5, 6));

    var sorterResult =
        attestations.stream().map(this::convertToPooledAttestationWithRewardInfo).toList();

    var localSorter =
        new RewardBasedAttestationSorter(null, null, null, null, true) {
          @Override
          public List<PooledAttestationWithRewardInfo> sort(
              final List<PooledAttestationWithData> attestations, final int maxAttestations) {
            return sorterResult;
          }
        };

    var sorterFactory = mock(RewardBasedAttestationSorterFactory.class);
    when(sorterFactory.create(any(), any())).thenReturn(localSorter);

    aggregatingPool =
        instantiatePool(
            mockSpec,
            mockRecentChainData,
            10,
            () -> 0L,
            sorterFactory,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Optional.of(ZERO));

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyElementsOf(attestations);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotConsiderAttestationsPriorToInclusionTracking() {
    aggregatingPool =
        instantiatePool(
            mockSpec,
            mockRecentChainData,
            10,
            () -> 0L,
            RewardBasedAttestationSorterFactory.NOOP,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Optional.of(ONE));

    // won't be considered since they are for slot 0, and we only track attestations from slot 1
    final AttestationData attestationData0 = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData0, 1, 2);
    addAttestationFromValidators(attestationData0, 3);

    final AttestationData attestationData1 = createAttestationData(ONE);
    final Attestation attestationBestAggregate1 =
        addAttestationFromValidators(attestationData1, 4, 5);
    final Attestation singleAttestation1 = addAttestationFromValidators(attestationData1, 6);

    // the earliest slot we track is supposed to remain 1, so attestationData1 will be included
    aggregatingPool.onAttestationsIncludedInBlock(UInt64.valueOf(2), List.of());

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(
            aggregateAttestations(committeeSizes, attestationBestAggregate1, singleAttestation1));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotConsiderAnyAttestationsBeforeTracking() {
    aggregatingPool =
        instantiatePool(
            mockSpec,
            mockRecentChainData,
            10,
            () -> 0L,
            RewardBasedAttestationSorterFactory.NOOP,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Optional.empty());

    // no attestation will be considered since we haven't started tracking yet

    final AttestationData attestationData0 = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData0, 1, 2);
    addAttestationFromValidators(attestationData0, 3);

    final AttestationData attestationData1 = createAttestationData(ONE);
    addAttestationFromValidators(attestationData1, 4, 5);
    addAttestationFromValidators(attestationData1, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotIncludeAggregateWithInfinitySignature() {
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, sign(KEYS.p(), attestationData), 1, 2);
    addAttestationFromValidators(attestationData, sign(KEYS.minusP(), attestationData), 3, 4);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotFillUpWithSingleAttestationsWithInfinitySignature() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    final AttestationData attestationData = createAttestationData(ZERO);
    final Attestation attestationP =
        addAttestationFromValidators(attestationData, sign(KEYS.p(), attestationData), 1, 2);
    addAttestationFromValidators(attestationData, sign(KEYS.minusP(), attestationData), 3);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(attestationP);
  }

  @TestTemplate
  public void createAggregateFor_shouldNotCreateAggregateWithInfinitySignature() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    final AttestationData attestationData = createAttestationData();
    addAttestationFromValidators(attestationData, sign(KEYS.p(), attestationData), 1);
    addAttestationFromValidators(attestationData, sign(KEYS.minusP(), attestationData), 2);

    assertThat(aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex))
        .isEmpty();
  }

  private BLSSignature sign(final BLSKeyPair keyPair, final AttestationData data) {
    // the pool never verifies signatures; any message shared by all participants will do
    return BLS.sign(keyPair.getSecretKey(), data.hashTreeRoot());
  }

  private PooledAttestationWithRewardInfo convertToPooledAttestationWithRewardInfo(
      final Attestation attestation) {
    final ValidatableAttestation validatableAttestation =
        createValidatableAttestationFromAttestation(attestation, true, true);
    return PooledAttestationWithRewardInfo.empty(
        new PooledAttestationWithData(
            validatableAttestation.getData(),
            PooledAttestation.fromValidatableAttestation(validatableAttestation)));
  }
}

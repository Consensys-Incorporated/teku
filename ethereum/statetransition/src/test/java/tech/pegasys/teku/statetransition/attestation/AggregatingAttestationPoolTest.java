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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.ATTESTATION_RETENTION_SLOTS;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractIntegerAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator.AttestationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

abstract class AggregatingAttestationPoolTest {

  public static final UInt64 SLOT = UInt64.valueOf(1234);
  private static final int COMMITTEE_SIZE = 130;

  protected Spec spec;
  protected SpecMilestone specMilestone;
  protected DataStructureUtil dataStructureUtil;
  protected Optional<UInt64> committeeIndex;
  protected final Spec mockSpec = mock(Spec.class);
  protected final RecentChainData mockRecentChainData = mock(RecentChainData.class);

  protected AggregatingAttestationPool aggregatingPool;

  protected final AttestationForkChecker forkChecker = mock(AttestationForkChecker.class);

  protected BeaconState state;
  protected Int2IntMap committeeSizes;

  abstract AggregatingAttestationPool instantiatePool(
      final Spec spec, final RecentChainData recentChainData, final int maxAttestations);

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    aggregatingPool = instantiatePool(mockSpec, mockRecentChainData, 100);
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();

    committeeSizes = new Int2IntOpenHashMap();
    IntStream.range(0, spec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot())
        .forEach(index -> committeeSizes.put(index, COMMITTEE_SIZE));

    final UpdatableStore mockStore = mock(UpdatableStore.class);
    state = dataStructureUtil.randomBeaconState();
    when(mockRecentChainData.getCurrentEpoch()).thenReturn(Optional.of(ZERO));
    when(mockRecentChainData.getStore()).thenReturn(mockStore);
    when(mockRecentChainData.getBestState())
        .thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(mockRecentChainData.retrieveStateInEffectAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(mockSpec.getBeaconCommitteesSize(any(), any())).thenReturn(committeeSizes);

    if (specMilestone.equals(PHASE0)) {
      committeeIndex = Optional.empty();
    } else {
      committeeIndex =
          Optional.of(
              dataStructureUtil.randomUInt64(
                  spec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot()));
    }

    when(forkChecker.areAttestationsFromCorrectForkV2(any())).thenReturn(true);

    when(mockSpec.getPreviousEpochAttestationCapacity(any())).thenReturn(Integer.MAX_VALUE);
    // Fwd some calls to the real spec
    when(mockSpec.computeEpochAtSlot(any()))
        .thenAnswer(i -> spec.computeEpochAtSlot(i.getArgument(0)));
    when(mockSpec.getSlotsPerEpoch(any())).thenAnswer(i -> spec.getSlotsPerEpoch(i.getArgument(0)));
    when(mockSpec.getCurrentEpoch(any(BeaconState.class)))
        .thenAnswer(i -> spec.getCurrentEpoch(i.<BeaconState>getArgument(0)));
    when(mockSpec.atSlot(any())).thenAnswer(invocation -> spec.atSlot(invocation.getArgument(0)));
    when(mockSpec.getGenesisSchemaDefinitions()).thenReturn(spec.getGenesisSchemaDefinitions());
    when(mockSpec.getSeed(any(), any(), any()))
        .thenAnswer(
            invocation ->
                spec.getSeed(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
  }

  @TestTemplate
  public void add_shouldRetrieveCommitteeSizesFromStateWhenMissing() {
    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestation = createAttestation(attestationData, spec, 1);

    final ValidatableAttestation validatableAttestation =
        createValidatableAttestationFromAttestation(attestation, false, true);

    assertThat(validatableAttestation.getCommitteesSize()).isEmpty();

    aggregatingPool.add(validatableAttestation);

    final int expectedCalls = specMilestone.isGreaterThanOrEqualTo(ELECTRA) ? 1 : 0;

    verify(mockSpec, times(expectedCalls))
        .getBeaconCommitteesSize(eq(state), eq(attestationData.getSlot()));

    assertSize().isEqualTo(1);
  }

  @TestTemplate
  public void add_shouldNotRetrieveCommitteeSizesWhenNotNeeded() {
    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestation = createAttestation(attestationData, spec, 1);

    final ValidatableAttestation validatableAttestation =
        createValidatableAttestationFromAttestation(attestation, true, true);

    if (specMilestone.isLessThan(ELECTRA)) {
      assertThat(validatableAttestation.getCommitteesSize()).isEmpty();
    } else {
      assertThat(validatableAttestation.getCommitteesSize()).isNotEmpty();
    }

    aggregatingPool.add(validatableAttestation);

    verify(mockSpec, never()).getBeaconCommitteesSize(eq(state), eq(attestationData.getSlot()));

    assertSize().isEqualTo(1);
  }

  @TestTemplate
  public void add_shouldNotAddIfFailsRetrievingCommitteesSize() {
    when(mockRecentChainData.getBestState()).thenReturn(Optional.empty());
    when(mockRecentChainData.retrieveStateInEffectAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestation = createAttestation(attestationData, spec, 1);

    final ValidatableAttestation validatableAttestation =
        createValidatableAttestationFromAttestation(attestation, false, true);

    assertThat(validatableAttestation.getCommitteesSize()).isEmpty();

    aggregatingPool.add(validatableAttestation);

    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA)) {
      assertSize().isZero();
    } else {
      assertSize().isEqualTo(1);
    }
  }

  @TestTemplate
  public void createAggregateFor_shouldReturnEmptyWhenNoAttestationsMatchGivenData() {
    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(createAttestationData().hashTreeRoot(), committeeIndex);
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = createAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6);

    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result).contains(aggregateAttestations(committeeSizes, attestation1, attestation2));
  }

  @TestTemplate
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {
    final AttestationData attestationData = createAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5, 7);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6, 8);
    addAttestationFromValidators(attestationData, 2, 3, 9);

    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result).contains(aggregateAttestations(committeeSizes, attestation1, attestation2));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldReturnEmptyListWhenNoAttestationsAvailable() {
    when(mockSpec.validateAttestation(any(), any())).thenReturn(Optional.empty());

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotIncludeAttestationsWhereDataDoesNotValidate() {
    addAttestationFromValidators(createAttestationData(), 1);
    addAttestationFromValidators(createAttestationData(), 2);
    addAttestationFromValidators(createAttestationData(), 3);

    when(mockSpec.validateAttestation(any(), any()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  void getAttestationsForBlock_shouldNotThrowExceptionWhenShufflingSeedIsUnknown() {
    final Attestation attestation = createAttestation(createAttestationData(ONE), 1, 2, 3, 4);
    // Receive the attestation from a block, prior to receiving it via gossip
    aggregatingPool.onAttestationsIncludedInBlock(ONE, List.of(attestation));
    // Attestation isn't added because it's already redundant
    aggregatingPool.add(createValidatableAttestationFromAttestation(attestation, true, true));
    assertSize().isZero();

    // But we now have a MatchingDataAttestationGroup with unknown shuffling seed present
    // It was previously assumed that wasn't possible so it threw an IllegalStateException
    // Now it should just exclude the group from consideration
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));
    final SszList<Attestation> result =
        aggregatingPool.getAttestationsForBlock(state, new AttestationForkChecker(spec, state));
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldIncludeAttestationsThatPassValidation() {
    final Attestation attestation1 =
        addAttestationFromValidators(createAttestationData(ZERO), 1, 2);
    final Attestation attestation2 =
        addAttestationFromValidators(createAttestationData(ZERO), 2, 3);
    final Attestation attestation3 =
        addAttestationFromValidators(createAttestationData(ZERO), 3, 4);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);
    when(mockSpec.validateAttestation(state, attestation1.getData()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));
    when(mockSpec.validateAttestation(state, attestation2.getData())).thenReturn(Optional.empty());
    when(mockSpec.validateAttestation(state, attestation3.getData())).thenReturn(Optional.empty());

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactlyInAnyOrder(attestation2, attestation3);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = createAttestationData(SLOT);
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(SLOT.increment());

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(aggregateAttestations(committeeSizes, attestation1, attestation2));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = createAttestationData(ZERO);
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(createAttestationData(ZERO), 3, 4);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(ONE);

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(
            aggregateAttestations(committeeSizes, attestation1, attestation2), attestation3);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldIncludeMoreRecentAttestationsFirst() {
    final AttestationData attestationData1 = createAttestationData(UInt64.valueOf(5));
    final AttestationData attestationData2 = createAttestationData(UInt64.valueOf(6));
    final AttestationData attestationData3 = createAttestationData(UInt64.valueOf(7));
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData2, 3, 4);
    final Attestation attestation3 = addAttestationFromValidators(attestationData3, 5, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(attestation3, attestation2, attestation1);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotAddMoreAttestationsThanAllowedInBlock() {
    final int allowed =
        Math.toIntExact(
            spec.atSlot(ONE)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getAttestationsSchema()
                .getMaxLength());

    final int validatorCount = allowed + 1;
    final BeaconState state = dataStructureUtil.randomBeaconState(validatorCount, 100, ONE);
    final AttestationData attestationData = createAttestationData(ZERO);

    final int lastValidatorIndex = validatorCount - 1;

    // add non aggregatable attestations, more than allowed in block
    for (int i = 0; i < validatorCount; i++) {
      addAttestationFromValidators(attestationData, i, lastValidatorIndex);
    }

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker)).hasSize(allowed);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldGivePriorityToBestAggregationForEachSlot() {
    // let's test this on electra only, which has only 8 attestations for block
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    assertThat(
            spec.atSlot(ONE)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getAttestationsSchema()
                .getMaxLength())
        .isEqualTo(8);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);

    // let's prepare 2 different attestationData for the same slot
    final AttestationData attestationData0 = createAttestationData(ZERO);
    final AttestationData attestationData1 = createAttestationData(ZERO);

    // let's fill up the pool with non-aggregatable attestationsData0
    addAttestationFromValidators(attestationData0, 1, 2);
    addAttestationFromValidators(attestationData0, 1, 3);
    addAttestationFromValidators(attestationData0, 1, 4);
    addAttestationFromValidators(attestationData0, 1, 5);
    addAttestationFromValidators(attestationData0, 1, 6);
    addAttestationFromValidators(attestationData0, 1, 7);
    addAttestationFromValidators(attestationData0, 1, 8);
    addAttestationFromValidators(attestationData0, 1, 9);

    // let's add a better aggregation for attestationData1
    final Attestation bestAttestation = addAttestationFromValidators(attestationData1, 11, 14, 15);

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker).get(0))
        .isEqualTo(bestAttestation);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf2() {
    testPrevEpochLimits(2);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf1() {
    testPrevEpochLimits(1);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf0() {
    testPrevEpochLimits(0);
  }

  void testPrevEpochLimits(final int prevEpochCapacity) {
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 startSlotAtCurrentEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    final BeaconState stateAtBlockSlot =
        specMilestone.isGreaterThanOrEqualTo(ELECTRA)
            ? dataStructureUtil
                .stateBuilderElectra(10, 20)
                .slot(startSlotAtCurrentEpoch.plus(5))
                .build()
            : dataStructureUtil
                .stateBuilderPhase0(10, 20)
                .slot(startSlotAtCurrentEpoch.plus(5))
                .build();
    when(mockSpec.getPreviousEpochAttestationCapacity(stateAtBlockSlot))
        .thenReturn(prevEpochCapacity);

    final List<Attestation> expectedAttestations = new ArrayList<>();
    // Current epoch Attestations
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch.plus(2), 1, 2));
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch.plus(1), 2, 3));
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch, 3, 4));

    // Prev epoch attestations within capacity limit
    for (int i = 0; i < prevEpochCapacity; i++) {
      expectedAttestations.add(
          addAttestationFromValidators(startSlotAtCurrentEpoch.minus(i + 1), 3 + i, 4 + i));
    }
    // Add a few extras
    addAttestationFromValidators(startSlotAtCurrentEpoch.minus(3), 1, 2);
    addAttestationFromValidators(startSlotAtCurrentEpoch.minus(4), 2, 3);

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyElementsOf(expectedAttestations);
  }

  @TestTemplate
  public void onSlot_shouldPruneAttestationsMoreThanTwoEpochsBehindCurrentSlot() {
    final AttestationData pruneAttestationData = createAttestationData(SLOT);
    final AttestationData preserveAttestationData = createAttestationData(SLOT.plus(ONE));
    addAttestationFromValidators(pruneAttestationData, 1, 2);
    final Attestation preserveAttestation =
        addAttestationFromValidators(preserveAttestationData, 2, 3);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertSize().isEqualTo(2);
    aggregatingPool.onSlot(
        pruneAttestationData.getSlot().plus(ATTESTATION_RETENTION_SLOTS).plus(ONE));

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsOnly(preserveAttestation);
    assertSize().isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldIncludeAttestationsAdded() {
    final AttestationData attestationData = createAttestationData();

    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    addAttestationFromValidators(attestationData, 2, 5);
    assertSize().isEqualTo(2);
  }

  @TestTemplate
  public void getSize_shouldDecreaseWhenAttestationsRemoved() {
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestationToRemove = addAttestationFromValidators(attestationData, 2, 5);

    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertSize().isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldNotIncrementWhenAttestationAlreadyExists() {
    final AttestationData attestationData = createAttestationData();

    final Attestation attestation = addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    aggregatingPool.add(createValidatableAttestationFromAttestation(attestation, true, true));
    assertSize().isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldDecrementForAllRemovedAttestations() {
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    assertSize().isEqualTo(2);
    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);

    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertSize().isEqualTo(0);
  }

  @TestTemplate
  public void getSize_shouldAddTheRightData() {
    final AttestationData attestationData = createAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);
    assertSize().isEqualTo(5);
  }

  @TestTemplate
  public void getSize_shouldDecrementForAllRemovedAttestationsWhileKeepingOthers() {
    final AttestationData attestationData = createAttestationData(ZERO);

    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);

    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    assertSize().isEqualTo(5);

    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertSize().isEqualTo(2);
  }

  @TestTemplate
  void shouldRemoveOldSlotsWhenMaximumNumberOfAttestationsReached() {
    aggregatingPool = instantiatePool(mockSpec, mockRecentChainData, 5);
    final AttestationData attestationData0 = createAttestationData(ZERO);
    final AttestationData attestationData1 = createAttestationData(ONE);
    final AttestationData attestationData2 = createAttestationData(UInt64.valueOf(2));
    addAttestationFromValidators(attestationData0, 1, 2);
    addAttestationFromValidators(attestationData0, 2, 3);
    addAttestationFromValidators(attestationData1, 3, 4);
    addAttestationFromValidators(attestationData1, 4, 5);
    addAttestationFromValidators(attestationData2, 5, 6);

    assertSize().isEqualTo(5);

    final BeaconState slot1State = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isNotEmpty();

    addAttestationFromValidators(attestationData2, 6, 7);
    // Should drop the slot 0 attestations
    if (aggregatingPool instanceof AggregatingAttestationPoolV2) {
      // v2 don't immediately drop attestations on add
      aggregatingPool.onSlot(UInt64.valueOf(3));
    }
    assertSize().isEqualTo(4);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isEmpty();
  }

  @TestTemplate
  void shouldNotRemoveLastSlotEvenWhenMaximumNumberOfAttestationsReached() {
    aggregatingPool = instantiatePool(mockSpec, mockRecentChainData, 5);
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 1, 2);
    addAttestationFromValidators(attestationData, 2, 3);
    addAttestationFromValidators(attestationData, 3, 4);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 5, 6);

    assertSize().isEqualTo(5);

    final BeaconState slot1State = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isNotEmpty();

    addAttestationFromValidators(attestationData, 6, 7);
    // Can't drop anything as we only have one slot.
    assertSize().isEqualTo(6);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotAddAttestationsFromWrongFork() {
    final AttestationData attestationData1 = createAttestationData(ZERO);
    final AttestationData attestationData2 = createAttestationData(ZERO);

    addAttestationFromValidators(attestationData1, 1, 2, 3);
    Attestation attestation2 = addAttestationFromValidators(attestationData2, 4, 5);

    when(forkChecker.areAttestationsFromCorrectForkV2(any())).thenReturn(false);
    when(forkChecker.areAttestationsFromCorrectForkV2(
            ArgumentMatchers.argThat(arg -> arg.getAttestationData().equals(attestationData2))))
        .thenReturn(true);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactly(attestation2);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAllAttestations() {
    final AttestationData firstAttestationData = createAttestationData();
    final Attestation firstAttestation =
        addAttestationFromValidators(firstAttestationData, 1, 2, 3);
    final AttestationData secondAttestationData = createAttestationData();
    final Attestation secondAttestation =
        addAttestationFromValidators(secondAttestationData, 3, 4, 5);
    assertThat(aggregatingPool.getAttestations(Optional.empty(), Optional.empty()))
        .containsExactlyInAnyOrder(firstAttestation, secondAttestation);
  }

  @TestTemplate
  public void
      getAttestations_shouldReturnElectraAttestationsOnly_whenElectraActivatesAndNoSlotProvided() {
    // Genesis spec must be before Electra in order to be able to add phase0 attestations
    assumeThat(specMilestone).isLessThan(ELECTRA);
    final Spec mockedSpec = mock(Spec.class);
    final AggregatingAttestationPool aggregatingPool =
        instantiatePool(mockedSpec, mockRecentChainData, DEFAULT_MAXIMUM_ATTESTATION_COUNT);
    // Adding a phase0 attestation to the aggregation pool
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    when(mockedSpec.atSlot(any())).thenReturn(phase0Spec.getGenesisSpec());
    final DataStructureUtil phase0DataStructureUtil = new DataStructureUtil(phase0Spec);
    final AttestationData phase0AttestationData =
        phase0DataStructureUtil.randomAttestationData(SLOT.minus(4));
    addAttestationFromValidators(aggregatingPool, phase0AttestationData, phase0Spec, 4, 5);

    // Adding an Electra attestation to the aggregation pool
    final Spec electraSpec = TestSpecFactory.createMinimalElectra();
    // Electra activates from SLOT
    when(mockedSpec.atSlot(argThat(slot -> slot.isGreaterThanOrEqualTo(SLOT))))
        .thenReturn(electraSpec.getGenesisSpec());
    final AttestationData electraAttestationData = createAttestationData(SLOT);
    committeeIndex =
        Optional.of(
            dataStructureUtil.randomUInt64(
                electraSpec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot()));
    final Attestation electraAttestation =
        addAttestationFromValidators(aggregatingPool, electraAttestationData, electraSpec, 1, 2, 3);

    when(mockRecentChainData.getCurrentSlot()).thenReturn(Optional.of(SLOT));
    // Should get the Electra attestation only
    assertThat(aggregatingPool.getAttestations(Optional.empty(), Optional.empty()))
        .containsExactly(electraAttestation);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenCommitteeIndexOnly_PreElectra() {
    assumeThat(specMilestone).isLessThan(ELECTRA);
    // Pre Electra the committee index filter is applied to the index set at the attestation data
    // level
    final AttestationData attestationData1 = createAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot(),
            attestationData1.getIndex().plus(1),
            attestationData1.getBeaconBlockRoot(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.empty(), Optional.of(attestationData1.getIndex())))
        .containsExactly(attestation1);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenCommitteeIndexOnly_PostElectra() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    // Post Electra the committee index filter is applied to the committee bits
    final AttestationData attestationData1 = createAttestationData();
    final AttestationData attestationData2 = createAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    final Optional<UInt64> committeeIndexFilter = committeeIndex;

    // set a different committee index
    if (committeeIndex.get().isZero()) {
      committeeIndex = Optional.of(committeeIndex.get().plus(1));
    } else {
      committeeIndex = Optional.of(committeeIndex.get().minus(1));
    }

    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(aggregatingPool.getAttestations(Optional.empty(), committeeIndexFilter))
        .containsExactly(attestation1);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenSlotOnly() {
    final AttestationData attestationData1 = createAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot().plus(1),
            attestationData1.getIndex(),
            attestationData1.getBeaconBlockRoot(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.of(attestationData1.getSlot()), Optional.empty()))
        .containsExactly(attestation1);
  }

  @TestTemplate
  void onAttestationsIncludedInBlock_shouldNotAddAttestationsAlreadySeenInABlock() {
    final AttestationData attestationData = createAttestationData(ZERO);
    // Included in block before we see any attestations with this data
    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    // But still shouldn't be able to add a redundant attestation later
    addAttestationFromValidators(attestationData, 2, 3);
    assertSize().isZero();
  }

  @TestTemplate
  void onAttestationsIncludedInBlock_shouldRemoveAttestationsWhenSeenInABlock() {
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 2, 3);

    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    assertSize().isZero();
  }

  @TestTemplate
  public void onAttestationsIncludedInBlock_shouldRetrieveCommitteeSizesFromStateWhenMissing() {
    final AttestationData attestationData = createAttestationData(ZERO);

    final Attestation attestation = createAttestation(attestationData, spec, 1);

    aggregatingPool.onAttestationsIncludedInBlock(ONE, List.of(attestation));

    final int expectedCalls = specMilestone.isGreaterThanOrEqualTo(ELECTRA) ? 1 : 0;

    verify(mockSpec, times(expectedCalls))
        .getBeaconCommitteesSize(eq(state), eq(attestationData.getSlot()));
  }

  @TestTemplate
  public void onAttestationsIncludedInBlock_shouldNotAddIfFailsRetrievingCommitteesSize() {
    final AttestationData attestationData = createAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 2, 3);

    when(mockRecentChainData.getBestState()).thenReturn(Optional.empty());
    when(mockRecentChainData.retrieveStateInEffectAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA)) {
      // we can't process onAttestationsIncludedInBlock wihthout committees size
      assertSize().isEqualTo(1);
    } else {
      assertSize().isZero();
    }
  }

  @TestTemplate
  void onReorg_shouldBeAbleToReadAttestations() {
    final AttestationData attestationData = createAttestationData(ZERO);
    // Included in block before we see any attestations with this data
    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    aggregatingPool.onReorg(ZERO);

    // Should now be able to add attestations that were redundant
    addAttestationFromValidators(attestationData, 2, 3);
    assertSize().isEqualTo(1);
  }

  protected Attestation addAttestationFromValidators(final UInt64 slot, final int... validators) {
    return addAttestationFromValidators(createAttestationData(slot), validators);
  }

  protected Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    return addAttestationFromValidators(data, spec, validators);
  }

  protected Attestation addAttestationFromValidators(
      final AttestationData data, final Spec spec, final int... validators) {
    return addAttestationFromValidators(aggregatingPool, data, spec, validators);
  }

  protected Attestation addAttestationFromValidators(
      final AttestationData data, final BLSSignature signature, final int... validators) {
    return addAttestationFromValidators(aggregatingPool, data, spec, signature, validators);
  }

  protected Attestation addAttestationFromValidators(
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationData data,
      final Spec spec,
      final int... validators) {
    return addAttestationFromValidators(
        aggregatingAttestationPool, data, spec, dataStructureUtil.randomSignature(), validators);
  }

  protected Attestation addAttestationFromValidators(
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationData data,
      final Spec spec,
      final BLSSignature signature,
      final int... validators) {
    final Attestation attestationFromValidators;
    final Attestation attestation;
    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA) && validators.length == 1) {
      attestationFromValidators = createSingleAttestation(data, validators[0], signature);
    } else {
      attestationFromValidators = createAttestation(data, spec, signature, validators);
    }

    final ValidatableAttestation validatableAttestation =
        createValidatableAttestationFromAttestation(attestationFromValidators, true, true);

    if (attestationFromValidators.isSingleAttestation()) {
      attestation = createAttestation(data, spec, signature, validators);
      validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation);
    } else {
      attestation = attestationFromValidators;
    }

    aggregatingAttestationPool.add(validatableAttestation);
    return attestation;
  }

  protected ValidatableAttestation createValidatableAttestationFromAttestation(
      final Attestation attestation,
      final boolean addShufflingAndCommitteeSizes,
      final boolean addIndexedAttestation) {
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(mockSpec, attestation);

    final Attestation finalAttestation;

    if (attestation.isSingleAttestation()) {
      finalAttestation =
          createAttestation(
              attestation.getData(), spec, attestation.getValidatorIndexRequired().intValue());
      validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation);
    } else {
      finalAttestation = attestation;
    }

    if (addShufflingAndCommitteeSizes) {
      validatableAttestation.saveCommitteeShufflingSeedAndCommitteesSize(
          dataStructureUtil.randomBeaconState(100, 15, finalAttestation.getData().getSlot()));
    }
    if (addIndexedAttestation) {
      validatableAttestation.setIndexedAttestation(
          IndexedAttestationLight.fromSsz(
              dataStructureUtil.randomIndexedAttestation(
                  finalAttestation.getData(),
                  finalAttestation
                      .getAggregationBits()
                      .streamAllSetBits()
                      .mapToObj(this::validatorBitToValidatorIndex)
                      .toArray(UInt64[]::new))));
    }

    return validatableAttestation;
  }

  protected AttestationData createAttestationData() {
    return createAttestationData(dataStructureUtil.randomUInt64());
  }

  protected AttestationData createAttestationData(final UInt64 slot) {
    if (specMilestone.isLessThan(ELECTRA)) {
      return dataStructureUtil.randomAttestationData(
          slot, committeeIndex.orElse(UInt64.valueOf(dataStructureUtil.randomPositiveInt())));
    }
    return dataStructureUtil.randomAttestationData(slot, ZERO);
  }

  private UInt64 validatorBitToValidatorIndex(final int validatorBit) {
    return UInt64.valueOf(validatorBit + 100);
  }

  protected Attestation createAttestation(final AttestationData data, final int... validators) {
    return createAttestation(data, spec, validators);
  }

  protected SingleAttestation createSingleAttestation(
      final AttestationData data, final int validatorIndex) {
    return createSingleAttestation(data, validatorIndex, dataStructureUtil.randomSignature());
  }

  protected SingleAttestation createSingleAttestation(
      final AttestationData data, final int validatorIndex, final BLSSignature signature) {
    final SingleAttestationSchema attestationSchema =
        spec.getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getSingleAttestationSchema();

    return attestationSchema.create(
        committeeIndex.orElseThrow(), UInt64.valueOf(validatorIndex), data, signature);
  }

  protected Attestation createAttestation(
      final AttestationData data, final Spec spec, final int... validators) {
    return createAttestation(data, spec, dataStructureUtil.randomSignature(), validators);
  }

  protected Attestation createAttestation(
      final AttestationData data,
      final Spec spec,
      final BLSSignature signature,
      final int... validators) {
    final AttestationSchema<?> attestationSchema =
        spec.getGenesisSchemaDefinitions().getAttestationSchema();
    final SszBitlist bitlist =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .getAggregationBitsSchema()
            .ofBits(COMMITTEE_SIZE, validators);

    final Supplier<SszBitvector> committeeBits;

    if (spec.atSlot(data.getSlot()).getMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      committeeBits =
          () ->
              attestationSchema
                  .getCommitteeBitsSchema()
                  .orElseThrow()
                  .ofBits(committeeIndex.orElseThrow().intValue());
    } else {
      committeeBits = () -> null;
    }
    return attestationSchema.create(bitlist, data, signature, committeeBits);
  }

  protected AbstractIntegerAssert<?> assertSize() {
    if (aggregatingPool instanceof AggregatingAttestationPoolV2) {
      // V2 prunes at onSlot, so we have to call it before checking the size
      aggregatingPool.onSlot(ONE);
    }
    return assertThat(aggregatingPool.getSize());
  }
}

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

package tech.pegasys.teku.validator.client;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationDutyFactory;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutySchedulerTest extends AbstractDutySchedulerTest {
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
      mock(BeaconCommitteeSubscriptions.class);
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);

  private final AttestationDutyFactory attestationDutyFactory = mock(AttestationDutyFactory.class);

  @SuppressWarnings("unchecked")
  private final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>
      scheduledDuties = mock(SlotBasedScheduledDuties.class);

  private final AttestationDutySchedulingStrategySelector
      attestationDutySchedulingStrategySelector =
          mock(AttestationDutySchedulingStrategySelector.class);

  private final StubMetricsSystem metricsSystem2 = new StubMetricsSystem();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  private AttestationDutyScheduler dutyScheduler;

  @BeforeEach
  public void init() {
    when(validatorApiChannel.getAttestationDuties(any(), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList()))));
    when(scheduledDuties.performProductionDuty(any()))
        .thenReturn(SafeFuture.completedFuture(DutyResult.NOOP));
    when(scheduledDuties.performAggregationDuty(any()))
        .thenReturn(SafeFuture.completedFuture(DutyResult.NOOP));
  }

  @Test
  public void shouldFetchDutiesForCurrentAndNextEpoch() {
    createDutySchedulerWithRealDuties();
    when(validatorApiChannel.getAttestationDuties(any(), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList()))));

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));

    verify(validatorApiChannel).getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(UInt64.valueOf(2), VALIDATOR_INDICES);
  }

  @Test
  public void shouldFetchDutiesForSecondEpochWhenFirstEpochReached() {
    createDutySchedulerWithRealDuties();
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getAttestationDuties(ZERO, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES);

    // Process each slot up to the start of epoch 1
    final UInt64 epoch1Start = spec.computeStartSlotAtEpoch(UInt64.ONE);
    for (int slot = 0; slot <= epoch1Start.intValue(); slot++) {
      dutyScheduler.onSlot(UInt64.valueOf(slot));
    }
    verify(validatorApiChannel).getAttestationDuties(UInt64.valueOf(2), VALIDATOR_INDICES);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrieved() {
    createDutySchedulerWithRealDuties();
    when(validatorApiChannel.getAttestationDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));

    verify(validatorApiChannel).getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(UInt64.valueOf(2), VALIDATOR_INDICES);

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));

    // Requests the next epoch, but not the current one because we already have that
    verify(validatorApiChannel).getAttestationDuties(UInt64.valueOf(3), VALIDATOR_INDICES);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrievedDuringFirstEpoch() {
    createDutySchedulerWithRealDuties();
    when(validatorApiChannel.getAttestationDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getAttestationDuties(ZERO, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(ONE, VALIDATOR_INDICES);

    // Second slot in epoch 0
    dutyScheduler.onSlot(ONE);

    // Shouldn't request any more duties
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRetryWhenRequestingDutiesFails() {
    createDutySchedulerWithRealDuties();
    final SafeFuture<Optional<AttesterDuties>> request1 = new SafeFuture<>();
    final SafeFuture<Optional<AttesterDuties>> request2 = new SafeFuture<>();
    when(validatorApiChannel.getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES))
        .thenReturn(request1)
        .thenReturn(request2);

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    verify(validatorApiChannel, times(1)).getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES);

    request1.completeExceptionally(new RuntimeException("Nope"));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    // Should retry request
    verify(validatorApiChannel, times(2)).getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES);

    // And not have any more retries scheduled
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldRefetchDutiesForMultipleEpochsWhenCurrentAndPreviousDependentRootChanges() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 nextEpoch = currentEpoch.plus(1);
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(currentEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, previousDutyDependentRoot, emptyList()))));
    when(validatorApiChannel.getAttestationDuties(eq(nextEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, currentDutyDependentRoot, emptyList()))));
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);

    dutyScheduler.onHeadUpdate(
        currentSlot,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());

    verify(validatorApiChannel, times(2)).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel, times(2)).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRefetchDutiesForNextEpochWhenHeadUpdateHasNewCurrentDependentRoot() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 nextEpoch = currentEpoch.plus(1);
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(currentEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, previousDutyDependentRoot, emptyList()))));
    when(validatorApiChannel.getAttestationDuties(eq(nextEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, currentDutyDependentRoot, emptyList()))));
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);

    dutyScheduler.onHeadUpdate(
        currentSlot,
        previousDutyDependentRoot,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());

    verify(validatorApiChannel, times(2)).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhenHeadUpdateReceivedWithNoChangeInDependentRoots() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 nextEpoch = currentEpoch.plus(1);
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(currentEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, previousDutyDependentRoot, emptyList()))));
    when(validatorApiChannel.getAttestationDuties(eq(nextEpoch), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, currentDutyDependentRoot, emptyList()))));
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);

    dutyScheduler.onHeadUpdate(
        currentSlot,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        dataStructureUtil.randomBytes32());

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEvents() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 nextEpoch = currentEpoch.plus(1);
    when(validatorApiChannel.getAttestationDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);

    dutyScheduler.onPossibleMissedEvents();

    // Remembers the previous latest epoch and uses it to recalculate duties
    verify(validatorApiChannel, times(2)).getAttestationDuties(currentEpoch, VALIDATOR_INDICES);
    verify(validatorApiChannel, times(2)).getAttestationDuties(nextEpoch, VALIDATOR_INDICES);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEventsWithNoPreviousEpoch() {
    createDutySchedulerWithRealDuties();
    dutyScheduler.onPossibleMissedEvents();

    // Latest epoch is unknown so can't recalculate duties
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotProcessAggregationIfEpochIsUnknown() {
    createDutySchedulerWithMockDuties();
    dutyScheduler.onAttestationAggregationDue(ONE);
    verify(scheduledDuties, never()).performProductionDuty(ZERO);
  }

  @Test
  public void shouldNotProcessAttestationIfEpochIsUnknown() {
    createDutySchedulerWithMockDuties();
    dutyScheduler.onAttestationCreationDue(ONE);
    verify(scheduledDuties, never()).performProductionDuty(ZERO);
  }

  @Test
  public void shouldNotProcessAggregationIfCurrentEpochIsTooFarBeforeSlotEpoch() {
    createDutySchedulerWithMockDuties();
    final int lookAheadEpoch = dutyScheduler.getLookAheadEpochs(UInt64.valueOf(2));
    // first slot of epoch 2
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpoch + 1));
    dutyScheduler.onSlot(ONE); // epoch 0
    dutyScheduler.onAttestationAggregationDue(slot);
    verify(scheduledDuties, never()).performAggregationDuty(slot);
  }

  @Test
  public void shouldNotProcessAttestationIfCurrentEpochIsTooFarBeforeSlotEpoch() {
    createDutySchedulerWithMockDuties();
    final int lookAheadEpoch = dutyScheduler.getLookAheadEpochs(UInt64.valueOf(2));
    // first slot of epoch 2
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpoch + 1));
    dutyScheduler.onSlot(ONE); // epoch 0
    dutyScheduler.onAttestationCreationDue(slot);
    verify(scheduledDuties, never()).performProductionDuty(slot);
  }

  @Test
  public void shouldProcessAggregationIfCurrentEpochIsAtBoundaryOfLookaheadEpoch() {
    createDutySchedulerWithMockDuties();
    final int lookAheadEpoch = dutyScheduler.getLookAheadEpochs(UInt64.valueOf(1));
    // last slot of epoch 1
    final UInt64 slot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpoch + 1)).decrement();
    dutyScheduler.onSlot(ONE); // epoch 0
    dutyScheduler.onAttestationAggregationDue(slot);
    verify(scheduledDuties).performAggregationDuty(slot);
  }

  @Test
  public void shouldProcessAttestationIfCurrentEpochIsAtBoundaryOfLookaheadEpoch() {
    createDutySchedulerWithMockDuties();
    final int lookAheadEpoch = dutyScheduler.getLookAheadEpochs(UInt64.valueOf(1));
    // last slot of epoch 1
    final UInt64 slot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpoch + 1)).decrement();
    dutyScheduler.onSlot(ONE); // epoch 0
    dutyScheduler.onAttestationCreationDue(slot);
    verify(scheduledDuties).performProductionDuty(slot);
  }

  @Test
  public void shouldDelayExecutingDutiesUntilSchedulingIsComplete() {
    createDutySchedulerWithMockDuties();
    final SafeFuture<Optional<AttesterDuties>> epoch0Duties = new SafeFuture<>();

    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any())).thenReturn(epoch0Duties);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList()))));
    dutyScheduler.onSlot(ZERO);

    dutyScheduler.onBlockProductionDue(ZERO);
    dutyScheduler.onAttestationCreationDue(ZERO);
    dutyScheduler.onAttestationAggregationDue(ZERO);
    // Duties haven't been loaded yet.
    verify(scheduledDuties, never()).performProductionDuty(ZERO);
    verify(scheduledDuties, never()).performAggregationDuty(ZERO);

    epoch0Duties.complete(
        Optional.of(new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList())));
    verify(scheduledDuties).performProductionDuty(ZERO);
    verify(scheduledDuties).performAggregationDuty(ZERO);
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    createDutySchedulerWithRealDuties();
    final UInt64 attestationProductionSlot = UInt64.valueOf(5);
    final int committeesAtSlot = 15;
    final AttesterDuty validator1Duties =
        new AttesterDuty(VALIDATOR1_KEY, 5, 10, 3, committeesAtSlot, 6, attestationProductionSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(validator1Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDutyFactory.createProductionDuty(attestationProductionSlot, validator1))
        .thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));
    verify(attestationDuty).addValidator(validator1, 3, 6, 5, 10);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationProductionSlot);

    // Somehow we triggered the same slot again.
    dutyScheduler.onAttestationCreationDue(attestationProductionSlot);

    // But shouldn't produce another block and get ourselves slashed.
    verify(attestationDuty, times(1)).performDuty();
  }

  @Test
  public void shouldScheduleAttestationDuties() {
    createDutySchedulerWithRealDuties();
    final UInt64 attestationSlot = UInt64.valueOf(5);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator1CommitteeSize = 10;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final int validator2CommitteeSize = 11;
    final int committeesAtSlot = 15;
    final AttesterDuty validator1Duties =
        new AttesterDuty(
            VALIDATOR1_KEY,
            validator1Index,
            validator1CommitteeSize,
            validator1Committee,
            committeesAtSlot,
            validator1CommitteePosition,
            attestationSlot);
    final AttesterDuty validator2Duties =
        new AttesterDuty(
            VALIDATOR2_KEY,
            validator2Index,
            validator2CommitteeSize,
            validator2Committee,
            committeesAtSlot,
            validator2CommitteePosition,
            attestationSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false,
                        dataStructureUtil.randomBytes32(),
                        List.of(validator1Duties, validator2Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDutyFactory.createProductionDuty(attestationSlot, validator1))
        .thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1,
            validator1Committee,
            validator1CommitteePosition,
            validator1Index,
            validator1CommitteeSize);
    verify(attestationDuty)
        .addValidator(
            validator2,
            validator2Committee,
            validator2CommitteePosition,
            validator2Index,
            validator2CommitteeSize);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verify(attestationDuty).performDuty();
  }

  @Test
  public void shouldScheduleAttestationDutiesWhenBlockIsImported() {
    createDutySchedulerWithRealDuties();
    final UInt64 attestationSlot = UInt64.valueOf(5);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator1CommitteeSize = 10;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final int validator2CommitteeSize = 11;
    final int committeesAtSlot = 15;
    final AttesterDuty validator1Duties =
        new AttesterDuty(
            VALIDATOR1_KEY,
            validator1Index,
            validator1CommitteeSize,
            validator1Committee,
            committeesAtSlot,
            validator1CommitteePosition,
            attestationSlot);
    final AttesterDuty validator2Duties =
        new AttesterDuty(
            VALIDATOR2_KEY,
            validator2Index,
            validator2CommitteeSize,
            validator2Committee,
            committeesAtSlot,
            validator2CommitteePosition,
            attestationSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false,
                        dataStructureUtil.randomBytes32(),
                        List.of(validator1Duties, validator2Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDutyFactory.createProductionDuty(attestationSlot, validator1))
        .thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1,
            validator1Committee,
            validator1CommitteePosition,
            validator1Index,
            validator1CommitteeSize);
    verify(attestationDuty)
        .addValidator(
            validator2,
            validator2Committee,
            validator2CommitteePosition,
            validator2Index,
            validator2CommitteeSize);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verify(attestationDuty).performDuty();
  }

  @Test
  public void shouldNotScheduleAttestationDutiesTwice() {
    createDutySchedulerWithRealDuties();
    final UInt64 attestationSlot = UInt64.valueOf(5);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator1CommitteeSize = 10;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final int validator2CommitteeSize = 11;
    final int committeesAtSlot = 15;
    final AttesterDuty validator1Duties =
        new AttesterDuty(
            VALIDATOR1_KEY,
            validator1Index,
            validator1CommitteeSize,
            validator1Committee,
            committeesAtSlot,
            validator1CommitteePosition,
            attestationSlot);
    final AttesterDuty validator2Duties =
        new AttesterDuty(
            VALIDATOR2_KEY,
            validator2Index,
            validator2CommitteeSize,
            validator2Committee,
            committeesAtSlot,
            validator2CommitteePosition,
            attestationSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false,
                        dataStructureUtil.randomBytes32(),
                        List.of(validator1Duties, validator2Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDutyFactory.createProductionDuty(attestationSlot, validator1))
        .thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1,
            validator1Committee,
            validator1CommitteePosition,
            validator1Index,
            validator1CommitteeSize);
    verify(attestationDuty)
        .addValidator(
            validator2,
            validator2Committee,
            validator2CommitteePosition,
            validator2Index,
            validator2CommitteeSize);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationSlot);

    // Run again for same slot
    dutyScheduler.onAttestationCreationDue(attestationSlot);

    // Verify we did not run it twice
    verify(attestationDuty, times(1)).performDuty();
  }

  @Test
  public void shouldScheduleAggregationDuties() {
    createDutySchedulerWithRealDuties();
    final UInt64 attestationSlot = UInt64.valueOf(13);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator1CommitteeSize = 1; // Guaranteed to be an aggregator
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final int validator2CommitteeSize = 1000000000; // Won't be an aggregator
    final int committeesAtSlot = 15;

    final AttesterDuty validator1Duties =
        new AttesterDuty(
            VALIDATOR1_KEY,
            validator1Index,
            validator1CommitteeSize,
            validator1Committee,
            committeesAtSlot,
            validator1CommitteePosition,
            attestationSlot);
    final AttesterDuty validator2Duties =
        new AttesterDuty(
            VALIDATOR2_KEY,
            validator2Index,
            validator2CommitteeSize,
            validator2Committee,
            committeesAtSlot,
            validator2CommitteePosition,
            attestationSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false,
                        dataStructureUtil.randomBytes32(),
                        List.of(validator1Duties, validator2Duties)))));

    final BLSSignature validator1Signature = dataStructureUtil.randomSignature();
    when(validator1.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(validator1Signature));
    when(validator2.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture = new SafeFuture<>();
    final AggregationDuty aggregationDuty = mock(AggregationDuty.class);
    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDutyFactory.createProductionDuty(attestationSlot, validator1))
        .thenReturn(attestationDuty);
    when(attestationDutyFactory.createAggregationDuty(attestationSlot, validator1))
        .thenReturn(aggregationDuty);
    when(aggregationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDuty.addValidator(
            validator1,
            validator1Committee,
            validator1CommitteePosition,
            validator1Index,
            validator1CommitteeSize))
        .thenReturn(unsignedAttestationFuture);

    // Load duties
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(ONE);
    dutyScheduler.onSlot(epochStartSlot);

    // Only validator1 should have had an aggregation duty created for it
    verify(attestationDutyFactory).createAggregationDuty(attestationSlot, validator1);
    // And should have added validator1 to each duty
    verify(aggregationDuty)
        .addValidator(
            validator1,
            validator1Index,
            validator1Signature,
            validator1Committee,
            unsignedAttestationFuture);
    verifyNoMoreInteractions(aggregationDuty);

    // Perform the duties
    dutyScheduler.onAttestationAggregationDue(attestationSlot);
    verify(aggregationDuty).performDuty();
  }

  @Test
  void shouldUsePreviousDependentRootWhenDutyFromCurrentEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 previousDutyDependentRoot = Bytes32.fromHexString("0x1111");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            Bytes32.fromHexString("0x3333"),
            previousDutyDependentRoot,
            Bytes32.fromHexString("0x2222"),
            ONE,
            ONE);

    assertThat(result).isEqualTo(previousDutyDependentRoot);
  }

  @Test
  void shouldUseCurrentDependentRootWhenDutyIsFromNextEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 currentDutyDependentRoot = Bytes32.fromHexString("0x2222");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            Bytes32.fromHexString("0x3333"),
            Bytes32.fromHexString("0x1111"),
            currentDutyDependentRoot,
            ZERO,
            ONE);

    assertThat(result).isEqualTo(currentDutyDependentRoot);
  }

  @Test
  void shouldUseHeadRootWhenDutyIsFromBeyondNextEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x3333");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            headBlockRoot,
            Bytes32.fromHexString("0x1111"),
            Bytes32.fromHexString("0x2222"),
            ZERO,
            UInt64.valueOf(2));

    assertThat(result).isEqualTo(headBlockRoot);
  }

  @Test
  public void getAttestationNextSlotScheduledWithSingleScheduledAttestation() {
    createDutySchedulerWithRealDuties();

    final UInt64 attestationProductionSlot = UInt64.valueOf(5);
    final AttesterDuty validator1Duties =
        createAttestationDutyForEpoch(VALIDATOR1_KEY, 5, attestationProductionSlot);
    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(validator1Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.getType()).thenReturn(DutyType.ATTESTATION_PRODUCTION);

    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDutyFactory.createProductionDuty(attestationProductionSlot, validator1))
        .thenReturn(attestationDuty);

    // Before we have any schedule duty we don't have a scheduled slot for attestation production
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();

    // Load attestation production duty
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Check that we are returning the expected slot for the attestation duty
    assertThat(dutyScheduler.getNextAttestationSlotScheduled())
        .hasValue(attestationProductionSlot.intValue());

    // Execute attestation production
    dutyScheduler.onAttestationCreationDue(attestationProductionSlot);

    // After executing the scheduled duty, we are back to no scheduled slot for attestation
    // production
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();
  }

  @Test
  public void getNextAttestationSlotScheduledWithManyAttestationsScheduledOnSameEpoch() {
    createDutySchedulerWithRealDuties();

    final UInt64 validator1AttestationProductionSlot = UInt64.valueOf(5);
    final AttesterDuty validator1Duties =
        createAttestationDutyForEpoch(VALIDATOR1_KEY, 5, validator1AttestationProductionSlot);

    final UInt64 validator2AttestationProductionSlot = UInt64.valueOf(7);
    final AttesterDuty validator2Duties =
        createAttestationDutyForEpoch(VALIDATOR2_KEY, 6, validator2AttestationProductionSlot);

    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false,
                        dataStructureUtil.randomBytes32(),
                        List.of(validator1Duties, validator2Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.getType()).thenReturn(DutyType.ATTESTATION_PRODUCTION);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());

    when(attestationDutyFactory.createProductionDuty(
            validator1AttestationProductionSlot, validator1))
        .thenReturn(attestationDuty);
    when(attestationDutyFactory.createProductionDuty(
            validator2AttestationProductionSlot, validator2))
        .thenReturn(attestationDuty);

    // Before we have any schedule duty we don't have a scheduled slot for attestation production
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();

    // Load attestation production duty
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Check that we are returning the expected slot for the attestation duty
    assertThat(dutyScheduler.getNextAttestationSlotScheduled())
        .hasValue(validator1AttestationProductionSlot.intValue());

    // Execute first attestation production
    dutyScheduler.onAttestationCreationDue(validator1AttestationProductionSlot);

    // After executing the first scheduled duty, the next duty slot is our new next slot
    assertThat(dutyScheduler.getNextAttestationSlotScheduled())
        .hasValue(validator2AttestationProductionSlot.intValue());

    // Execute second attestation production
    dutyScheduler.onAttestationCreationDue(validator2AttestationProductionSlot);

    // After both attestation creation duties have been created, we don't have a next scheduled slot
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();
  }

  @Test
  public void getNextAttestationSlotScheduledWithManyAttestationsScheduledOnDifferentEpochs() {
    createDutySchedulerWithRealDuties();

    // slot 5 is in epoch 0
    final UInt64 validator1AttestationProductionSlot = UInt64.valueOf(5);
    final AttesterDuty validator1Duties =
        createAttestationDutyForEpoch(VALIDATOR1_KEY, 5, validator1AttestationProductionSlot);

    // using minimal spec, slot 12 is on epoch 1
    final UInt64 validator2AttestationProductionSlot = UInt64.valueOf(12);
    final AttesterDuty validator2Duties =
        createAttestationDutyForEpoch(VALIDATOR2_KEY, 6, validator2AttestationProductionSlot);

    when(validatorApiChannel.getAttestationDuties(eq(ZERO), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(validator1Duties)))));
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(validator2Duties)))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.getType()).thenReturn(DutyType.ATTESTATION_PRODUCTION);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());

    when(attestationDutyFactory.createProductionDuty(
            validator1AttestationProductionSlot, validator1))
        .thenReturn(attestationDuty);
    when(attestationDutyFactory.createProductionDuty(
            validator2AttestationProductionSlot, validator2))
        .thenReturn(attestationDuty);

    // Before we have any schedule duty we don't have a scheduled slot for attestation production
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();

    // Load attestation production duty
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Check that we are returning the expected slot for the attestation duty
    assertThat(dutyScheduler.getNextAttestationSlotScheduled())
        .hasValue(validator1AttestationProductionSlot.intValue());

    // Execute first attestation production
    dutyScheduler.onAttestationCreationDue(validator1AttestationProductionSlot);

    // After executing the first scheduled duty, the next duty slot is our new next slot
    assertThat(dutyScheduler.getNextAttestationSlotScheduled())
        .hasValue(validator2AttestationProductionSlot.intValue());

    // Execute second attestation production
    dutyScheduler.onAttestationCreationDue(validator2AttestationProductionSlot);

    // After both attestation creation duties have been created, we don't have a next scheduled slot
    assertThat(dutyScheduler.getNextAttestationSlotScheduled()).isEmpty();
  }

  private AttesterDuty createAttestationDutyForEpoch(
      final BLSPublicKey validatorKey, final int validatorIndex, final UInt64 epoch) {
    return new AttesterDuty(validatorKey, validatorIndex, 10, 3, 15, 6, epoch);
  }

  @Test
  public void shouldOnlyUpdateNextAttestationSlotFromCurrentScheduledSlotOnwards() {
    createDutySchedulerWithRealDuties();
    AttestationDutyScheduler spyDutyScheduler = spy(dutyScheduler);
    when(spyDutyScheduler.getNextAttestationSlotScheduled()).thenReturn(Optional.of(2));

    // currentSlot = nextAttestationSlot; recalculate nextAttestationSlot
    spyDutyScheduler.onSlot(ZERO);
    verify(spyDutyScheduler, times(1)).getNextAttestationSlotScheduled();

    // currentSlot < nextAttestationSlot; DO NOT recalculate nextAttestationSlot
    spyDutyScheduler.onSlot(UInt64.valueOf(1));
    verify(spyDutyScheduler, times(1)).getNextAttestationSlotScheduled();

    // currentSlot = nextAttestationSlot; recalculate nextAttestationSlot
    spyDutyScheduler.onSlot(UInt64.valueOf(2));
    verify(spyDutyScheduler, times(2)).getNextAttestationSlotScheduled();

    // currentSlot > nextAttestationSlot; recalculate nextAttestationSlot
    spyDutyScheduler.onSlot(UInt64.valueOf(3));
    verify(spyDutyScheduler, times(3)).getNextAttestationSlotScheduled();
  }

  private void createDutySchedulerWithRealDuties() {
    final OwnedValidators validators =
        new OwnedValidators(Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2));
    final AttestationDutyDefaultSchedulingStrategy dutySchedulingStrategy =
        new AttestationDutyDefaultSchedulingStrategy(
            spec,
            forkProvider,
            dependentRoot ->
                new SlotBasedScheduledDuties<>(
                    attestationDutyFactory, dependentRoot, Duty::performDuty),
            validators,
            beaconCommitteeSubscriptions,
            validatorApiChannel,
            false);
    when(attestationDutySchedulingStrategySelector.selectStrategy(anyInt()))
        .thenReturn(dutySchedulingStrategy);
    dutyScheduler =
        new AttestationDutyScheduler(
            metricsSystem,
            new RetryingDutyLoader<>(
                asyncRunner,
                timeProvider,
                new AttestationDutyLoader(
                    validators,
                    validatorIndexProvider,
                    validatorApiChannel,
                    attestationDutySchedulingStrategySelector)),
            spec);
  }

  private void createDutySchedulerWithMockDuties() {
    final OwnedValidators validators =
        new OwnedValidators(Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2));
    final AttestationDutyDefaultSchedulingStrategy dutySchedulingStrategy =
        new AttestationDutyDefaultSchedulingStrategy(
            spec,
            forkProvider,
            dependentRoot -> scheduledDuties,
            validators,
            beaconCommitteeSubscriptions,
            validatorApiChannel,
            false);
    when(attestationDutySchedulingStrategySelector.selectStrategy(anyInt()))
        .thenReturn(dutySchedulingStrategy);
    dutyScheduler =
        new AttestationDutyScheduler(
            metricsSystem2,
            new RetryingDutyLoader<>(
                asyncRunner,
                timeProvider,
                new AttestationDutyLoader(
                    validators,
                    validatorIndexProvider,
                    validatorApiChannel,
                    attestationDutySchedulingStrategySelector)),
            spec);
  }
}

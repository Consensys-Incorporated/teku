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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class AttestationDutyDefaultSchedulingStrategyTest {

  private static final IntList VALIDATOR_INDICES = IntList.of(1, 2, 3, 4, 5, 6, 7, 8);

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
      mock(BeaconCommitteeSubscriptions.class);

  @SuppressWarnings("unchecked")
  private final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>
      scheduledDuties = mock(SlotBasedScheduledDuties.class);

  private final BLSPublicKey validatorKey = dataStructureUtil.randomPublicKey();
  private final Signer signer = mock(Signer.class);
  private final Validator validator =
      new Validator(validatorKey, signer, new FileBackedGraffitiProvider());
  private final Map<BLSPublicKey, Validator> validators = Map.of(validatorKey, validator);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

  private final AttestationDutyDefaultSchedulingStrategy dutySchedulingStrategy =
      new AttestationDutyDefaultSchedulingStrategy(
          spec,
          forkProvider,
          dependentRoot -> scheduledDuties,
          new OwnedValidators(validators),
          beaconCommitteeSubscriptions,
          validatorApiChannel,
          false);

  @BeforeEach
  void setUp() {
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.completedFuture(forkInfo));
  }

  @Test
  void shouldSubscribeToSubnetWhenValidatorIsAggregator() {
    final UInt64 slot = UInt64.ONE;
    final int validatorIndex = VALIDATOR_INDICES.getInt(0);
    final int committeeLength = 1;
    final int committeeIndex = 3;
    final int committeesAtSlot = 4;
    final AttesterDuty duty =
        new AttesterDuty(
            validatorKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            0,
            slot);
    final AttesterDuties duties =
        new AttesterDuties(false, dataStructureUtil.randomBytes32(), List.of(duty));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(slot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<SlotBasedScheduledDuties<?, ?>> result =
        dutySchedulingStrategy.scheduleAllDuties(UInt64.ONE, duties);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions)
        .subscribeToBeaconCommittee(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, UInt64.valueOf(committeesAtSlot), slot, true));
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldSubscribeToSubnetWhenValidatorIsNotAggregator() {
    final UInt64 slot = UInt64.ONE;
    final int validatorIndex = VALIDATOR_INDICES.getInt(0);
    final int committeeLength = 10000000;
    final int committeeIndex = 3;
    final int committeesAtSlot = 4;
    final AttesterDuty duty =
        new AttesterDuty(
            validatorKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            0,
            slot);
    final AttesterDuties duties =
        new AttesterDuties(false, dataStructureUtil.randomBytes32(), List.of(duty));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(slot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<SlotBasedScheduledDuties<?, ?>> result =
        dutySchedulingStrategy.scheduleAllDuties(UInt64.ONE, duties);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions)
        .subscribeToBeaconCommittee(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, UInt64.valueOf(committeesAtSlot), slot, false));
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldSendSubscriptionRequestsWhenAllDutiesAreScheduled() {
    final AttesterDuties duties =
        new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList());
    final SafeFuture<SlotBasedScheduledDuties<?, ?>> result =
        dutySchedulingStrategy.scheduleAllDuties(UInt64.ONE, duties);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtSubmissionDeferredUntilEpochStartForLookaheadEpoch() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final UInt64 lookaheadEpoch = UInt64.ONE;
    final UInt64 firstSlotOfLookaheadEpoch = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    final AttesterDuties duties =
        new AttesterDuties(
            false,
            dataStructureUtil.randomBytes32(),
            List.of(createAggregatorDuty(firstSlotOfLookaheadEpoch)));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(firstSlotOfLookaheadEpoch, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    dvtStrategy.scheduleAllDuties(lookaheadEpoch, duties);

    verifyNoInteractions(validatorApiChannel);

    dvtStrategy.onSlot(firstSlotOfLookaheadEpoch);

    verify(validatorApiChannel).getBeaconCommitteeSelectionProof(any());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtLookaheadDutiesAreAvailableBeforeDvtSubmissionCompletes() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final UInt64 lookaheadEpoch = UInt64.ONE;
    final UInt64 firstSlotOfLookaheadEpoch = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    final AttesterDuties duties =
        new AttesterDuties(
            false,
            dataStructureUtil.randomBytes32(),
            List.of(createAggregatorDuty(firstSlotOfLookaheadEpoch)));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(firstSlotOfLookaheadEpoch, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(new SafeFuture<>());

    final SafeFuture<SlotBasedScheduledDuties<?, ?>> result =
        dvtStrategy.scheduleAllDuties(lookaheadEpoch, duties);

    assertThat(result).isCompleted();
    assertThat(result.getImmediately()).isSameAs(scheduledDuties);
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtSubmissionForCurrentEpochActivatesImmediately() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final UInt64 currentEpoch = UInt64.ONE;
    final UInt64 firstSlotOfCurrentEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    final AttesterDuties duties =
        new AttesterDuties(
            false,
            dataStructureUtil.randomBytes32(),
            List.of(createAggregatorDuty(firstSlotOfCurrentEpoch)));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(firstSlotOfCurrentEpoch, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    dvtStrategy.onSlot(firstSlotOfCurrentEpoch);
    dvtStrategy.scheduleAllDuties(currentEpoch, duties);

    verify(validatorApiChannel).getBeaconCommitteeSelectionProof(any());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtSubmissionActivatedOnLaterSlotWhenEpochBoundaryMissed() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final UInt64 lookaheadEpoch = UInt64.ONE;
    final UInt64 firstSlotOfLookaheadEpoch = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    final UInt64 secondSlotOfLookaheadEpoch = firstSlotOfLookaheadEpoch.plus(1);
    final AttesterDuties duties =
        new AttesterDuties(
            false,
            dataStructureUtil.randomBytes32(),
            List.of(createAggregatorDuty(firstSlotOfLookaheadEpoch)));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(firstSlotOfLookaheadEpoch, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    dvtStrategy.scheduleAllDuties(lookaheadEpoch, duties);

    verifyNoInteractions(validatorApiChannel);

    dvtStrategy.onSlot(secondSlotOfLookaheadEpoch);

    verify(validatorApiChannel).getBeaconCommitteeSelectionProof(any());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtRescheduledDutiesCancelPreviousPendingInstance() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final UInt64 lookaheadEpoch = UInt64.ONE;
    final UInt64 firstSlotOfLookaheadEpoch = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    final AttesterDuties duties =
        new AttesterDuties(
            false,
            dataStructureUtil.randomBytes32(),
            List.of(createAggregatorDuty(firstSlotOfLookaheadEpoch)));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(firstSlotOfLookaheadEpoch, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    dvtStrategy.scheduleAllDuties(lookaheadEpoch, duties);
    dvtStrategy.scheduleAllDuties(lookaheadEpoch, duties);
    dvtStrategy.onSlot(firstSlotOfLookaheadEpoch);

    verify(validatorApiChannel, times(1)).getBeaconCommitteeSelectionProof(any());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void dvtNotSubmittedWhenDutiesListIsEmpty() {
    final AttestationDutyDefaultSchedulingStrategy dvtStrategy = createDvtStrategy();
    final AttesterDuties emptyDuties =
        new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList());

    dvtStrategy.scheduleAllDuties(UInt64.ONE, emptyDuties);
    dvtStrategy.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));

    verifyNoInteractions(validatorApiChannel);
  }

  private AttestationDutyDefaultSchedulingStrategy createDvtStrategy() {
    return new AttestationDutyDefaultSchedulingStrategy(
        spec,
        forkProvider,
        dependentRoot -> scheduledDuties,
        new OwnedValidators(validators),
        beaconCommitteeSubscriptions,
        validatorApiChannel,
        true);
  }

  private AttesterDuty createAggregatorDuty(final UInt64 slot) {
    return new AttesterDuty(validatorKey, VALIDATOR_INDICES.getInt(0), 1, 3, 4, 0, slot);
  }
}

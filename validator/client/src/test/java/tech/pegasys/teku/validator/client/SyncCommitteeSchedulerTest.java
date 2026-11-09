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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.SyncCommitteeScheduler.EarlySubscribeRandomSource;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;

class SyncCommitteeSchedulerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final int epochsPerSyncCommitteePeriod =
      SpecConfigAltair.required(spec.getGenesisSpecConfig()).getEpochsPerSyncCommitteePeriod();
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);

  private final Map<UInt64, SafeFuture<Optional<SyncCommitteeScheduledDuties>>>
      requestedDutiesByEpoch = new HashMap<>();

  @SuppressWarnings("unchecked")
  private final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader = mock(DutyLoader.class);

  private final SyncCommitteeScheduledDuties duties = createScheduledDuties();
  private final EarlySubscribeRandomSource earlySubscribeRandomSource =
      mock(EarlySubscribeRandomSource.class);

  private final SyncCommitteeScheduler scheduler =
      new SyncCommitteeScheduler(
          new StubMetricsSystem(), spec, dutyLoader, earlySubscribeRandomSource);

  @BeforeEach
  void setUp() {
    when(dutyLoader.loadDutiesForEpoch(any()))
        .thenAnswer(
            invocation -> {
              final UInt64 epoch = invocation.getArgument(0);
              return requestedDutiesByEpoch.computeIfAbsent(epoch, __ -> new SafeFuture<>());
            });
  }

  @Test
  void shouldCalculateCurrentPeriodDutiesOnFirstSlot() {
    scheduler.onSlot(UInt64.ONE);

    // Should request the last epoch in the period as it's most likely to be non-finalized
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));
  }

  @Test
  void shouldNotReloadDutiesAtNextEpochWhenInSameSyncCommitteePeriod() {
    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));

    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    verifyNoMoreInteractions(dutyLoader);
  }

  @Test
  void shouldPerformProductionForEachSlotWhenAttestationCreationDue() {
    scheduler.onSlot(UInt64.ONE);

    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    UInt64.range(UInt64.ONE, UInt64.valueOf(10))
        .forEach(
            slot -> {
              scheduler.onSyncCommitteeCreationDue(slot);
              verify(duties).performProductionDuty(slot);
            });
  }

  @Test
  void shouldNotPerformProductionMultipleTimesForSameSlot() {
    scheduler.onSlot(UInt64.ONE);

    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    scheduler.onSyncCommitteeCreationDue(UInt64.ONE);
    scheduler.onSyncCommitteeCreationDue(UInt64.ONE);

    verify(duties, times(1)).performProductionDuty(UInt64.ONE);
  }

  @Test
  void shouldNotPerformProductionForEarlierSlot() {
    scheduler.onSlot(UInt64.ONE);

    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    scheduler.onSyncCommitteeCreationDue(UInt64.valueOf(2));
    scheduler.onSyncCommitteeCreationDue(UInt64.ONE);

    verify(duties).performProductionDuty(UInt64.valueOf(2));
    verify(duties, never()).performProductionDuty(UInt64.ONE);
  }

  @Test
  void shouldPerformAggregationForEachSlotWhenContributionDue() {
    scheduler.onSlot(UInt64.ONE);

    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    UInt64.range(UInt64.ONE, UInt64.valueOf(10))
        .forEach(
            slot -> {
              scheduler.onContributionCreationDue(slot);
              verify(duties).performAggregationDuty(slot);
            });
  }

  @Test
  void shouldCalculateNextSyncPeriodDutiesRandomNumberOfEpochsPriorToStart() {
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT)).thenReturn(4);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(4);

    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader, never()).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(1));
    scheduler.onSlot(spec.computeStartSlotAtEpoch(subscribeEpoch));

    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(1));
  }

  @Test
  void shouldTriggerResubscribeRequestForCurrentSyncCommitteeEveryEpoch() {
    // Initial scheduler tick, will create sync periods
    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ZERO));
    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    // Still within first epoch, should not trigger
    scheduler.onSlot(UInt64.ONE);
    verify(duties, never()).subscribeToSubnets();

    // Now on new epoch, should trigger current sync committee subscribe request
    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    verify(duties, times(1)).subscribeToSubnets();
  }

  @Test
  void shouldTriggerResubscribeRequestForNextSyncCommitteeEveryEpochAfterSubscribeEpoch() {
    final int nextSyncCommitteeSubscribeEpoch = 4;
    // Set next sync committee subscribe epoch
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT))
        .thenReturn(nextSyncCommitteeSubscribeEpoch);

    // Initial scheduler tick, will create sync periods and calculate duties for current sync
    // committee
    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ZERO));
    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    // On subscribe epoch, will calculate duties for the next sync committee
    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(nextSyncCommitteeSubscribeEpoch)));
    getRequestedDutiesForSyncCommitteePeriod(1).complete(Optional.of(duties));
    // Verify subscribe request for current sync committee
    verify(duties, times(1)).subscribeToSubnets();

    // Now on the next epoch, should trigger both subscribe request (current and next sync
    // committee)
    scheduler.onSlot(
        spec.computeStartSlotAtEpoch(UInt64.valueOf(nextSyncCommitteeSubscribeEpoch + 1)));

    // Verify subscribe request for current sync committee and next sync committee (2 reqs + 1
    // previous req)
    verify(duties, times(3)).subscribeToSubnets();
  }

  @Test
  void shouldNotSelectNewRandomNumberEachSlot() {
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT)).thenReturn(4);

    scheduler.onSlot(UInt64.ONE);
    verify(earlySubscribeRandomSource).randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT);

    // Already picked a random epoch to subscribe, so don't pick again
    scheduler.onSlot(UInt64.valueOf(2));
    verifyNoMoreInteractions(earlySubscribeRandomSource);
  }

  @Test
  void shouldNotRecalculateDutiesEverySlot() {
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT)).thenReturn(4);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(4);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);

    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));

    scheduler.onSlot(subscribeSlot);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(1));
    verifyNoMoreInteractions(dutyLoader);

    // Already calculated all the duties we need so don't calculate them again
    scheduler.onSlot(subscribeSlot.plus(1));
    scheduler.onSlot(spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch));
    verifyNoMoreInteractions(dutyLoader);
  }

  @Test
  void shouldSwitchToNextCommitteePeriodWhenLastSlotOfSyncCommitteePeriodReached() {
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT)).thenReturn(5);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(5);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);
    final UInt64 nextSyncCommitteePeriodStartSlot =
        spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch);
    final SyncCommitteeScheduledDuties nextDuties = createScheduledDuties();

    scheduler.onSlot(UInt64.valueOf(5));
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));
    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));

    scheduler.onSlot(subscribeSlot);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(1));
    getRequestedDutiesForSyncCommitteePeriod(1).complete(Optional.of(nextDuties));

    // Subscribed, but still performing duties for first sync committee period
    scheduler.onSyncCommitteeCreationDue(subscribeSlot);
    verify(duties).performProductionDuty(subscribeSlot);

    final UInt64 nextSyncCommitteeFirstDutySlot = nextSyncCommitteePeriodStartSlot.minus(1);
    scheduler.onSlot(nextSyncCommitteeFirstDutySlot);
    scheduler.onSyncCommitteeCreationDue(nextSyncCommitteeFirstDutySlot);
    verify(nextDuties).performProductionDuty(nextSyncCommitteeFirstDutySlot);
    verify(duties, never()).performProductionDuty(nextSyncCommitteeFirstDutySlot);
  }

  /**
   * Weird corner case where clocks are out of sync between validator client and beacon chain. We
   * get a block imported notice for the next slot prior to getting onSlot from local clock
   */
  @Test
  void shouldUseNextPeriodForDutiesWhenOnSlotNotYetCalled() {
    when(earlySubscribeRandomSource.randomEpochCount(SYNC_COMMITTEE_SUBNET_COUNT)).thenReturn(5);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(5);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);
    final UInt64 nextSyncCommitteePeriodStartSlot =
        spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch);
    final SyncCommitteeScheduledDuties nextDuties = createScheduledDuties();

    // Trigger calculation of duties for both current and next periods
    scheduler.onSlot(subscribeSlot);
    getRequestedDutiesForSyncCommitteePeriod(0).complete(Optional.of(duties));
    getRequestedDutiesForSyncCommitteePeriod(1).complete(Optional.of(nextDuties));

    // Unexpectedly jump ahead to the next committee period without getting a slot event first
    scheduler.onSyncCommitteeCreationDue(nextSyncCommitteePeriodStartSlot);
    scheduler.onContributionCreationDue(nextSyncCommitteePeriodStartSlot);

    // Should use duties from the next period
    verify(nextDuties).performProductionDuty(nextSyncCommitteePeriodStartSlot);
    verify(nextDuties).performAggregationDuty(nextSyncCommitteePeriodStartSlot);

    // Should not perform duties if the epoch is past the end of the next sync committee period
    final UInt64 tooFarInFutureEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(
            nextSyncCommitteePeriodStartEpoch);
    final UInt64 tooFarInFutureSlot = spec.computeStartSlotAtEpoch(tooFarInFutureEpoch);
    scheduler.onSyncCommitteeCreationDue(tooFarInFutureSlot);
    scheduler.onContributionCreationDue(tooFarInFutureSlot);

    verify(duties, never()).performProductionDuty(tooFarInFutureSlot);
    verify(duties, never()).performAggregationDuty(tooFarInFutureSlot);
    verify(nextDuties, never()).performProductionDuty(tooFarInFutureSlot);
    verify(nextDuties, never()).performAggregationDuty(tooFarInFutureSlot);
  }

  @Test
  void shouldRecalculateDutiesWhenBeaconNodeRestarts() {
    scheduler.onSlot(UInt64.ZERO);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));

    // Reconnecting the event stream may mean the node restarted so recalculate duties to ensure
    // subscriptions are refreshed
    scheduler.onPossibleMissedEvents();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));
  }

  @Test
  void shouldRecalculateDutiesOnHeadUpdateWithSlotPriorToAltairActivation() {
    // simulates altair activation at slot 1
    Spec mockedSpec = spy(spec);
    SyncCommitteeScheduler schedulerWithMockedSpec =
        new SyncCommitteeScheduler(
            new StubMetricsSystem(), mockedSpec, dutyLoader, earlySubscribeRandomSource);
    when(mockedSpec.getSyncCommitteeUtil(UInt64.ZERO)).thenReturn(Optional.empty());

    schedulerWithMockedSpec.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));

    DataStructureUtil dataStructureUtil = new DataStructureUtil(mockedSpec);

    schedulerWithMockedSpec.onHeadUpdate(
        UInt64.ZERO,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());
    verify(dutyLoader, times(2)).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));
  }

  @Test
  void shouldNotRecalculateDutiesOnHeadUpdateWithSlotNonPriorToAltairActivation() {
    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(getRequestEpochForCommitteePeriod(0));

    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    scheduler.onHeadUpdate(
        UInt64.ZERO,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());
    verifyNoMoreInteractions(dutyLoader);
  }

  private SafeFuture<Optional<SyncCommitteeScheduledDuties>>
      getRequestedDutiesForSyncCommitteePeriod(final int syncCommitteePeriod) {
    final UInt64 requestEpoch = getRequestEpochForCommitteePeriod(syncCommitteePeriod);
    return requestedDutiesByEpoch.get(requestEpoch);
  }

  private UInt64 getRequestEpochForCommitteePeriod(final int syncCommitteePeriod) {
    return UInt64.valueOf(epochsPerSyncCommitteePeriod * (syncCommitteePeriod + 1L))
        .minusMinZero(1);
  }

  private SyncCommitteeScheduledDuties createScheduledDuties() {
    final SyncCommitteeScheduledDuties duties = mock(SyncCommitteeScheduledDuties.class);
    when(duties.performProductionDuty(any())).thenReturn(new SafeFuture<>());
    when(duties.performAggregationDuty(any())).thenReturn(new SafeFuture<>());
    return duties;
  }
}

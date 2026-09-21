/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;

/**
 * Scheduled duties for sync committees.
 *
 * <p>Note that because the sync committee period is so long (256 epochs) and we get a full
 * committee period look ahead, there is no concern that duties will be invalidated by re-orgs as
 * they'd have to be at least 256 epochs long to change the duty allocations.
 *
 * <p>Having to reconnect to the beacon chain does cause duties to recalculate though so that any
 * subnet subscriptions are renewed if the reconnection was because the beacon chain restarted.
 */
public class SyncCommitteeScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final MetricsSystem metricsSystem;
  private final Spec spec;
  private final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader;
  private final EarlySubscribeRandomSource earlySubscribeRandomSource;

  private Optional<SyncCommitteePeriod> currentSyncCommitteePeriod = Optional.empty();
  private Optional<SyncCommitteePeriod> nextSyncCommitteePeriod = Optional.empty();
  private UInt64 lastProductionSlot;

  public SyncCommitteeScheduler(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader,
      final EarlySubscribeRandomSource earlySubscribeRandomSource) {
    this.metricsSystem = metricsSystem;
    this.spec = spec;
    this.dutyLoader = dutyLoader;
    this.earlySubscribeRandomSource = earlySubscribeRandomSource;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final Optional<SyncCommitteeUtil> maybeUtils = spec.getSyncCommitteeUtil(slot);
    if (maybeUtils.isEmpty()) {
      return;
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeUtils.get();
    final UInt64 dutiesEpoch = syncCommitteeUtil.getEpochForDutiesAtSlot(slot);
    if (currentSyncCommitteePeriod.isEmpty()) {
      final SyncCommitteePeriod committeePeriod =
          createSyncCommitteePeriod(
              syncCommitteeUtil,
              syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(dutiesEpoch),
              0);
      committeePeriod.calculateDuties();
      currentSyncCommitteePeriod = Optional.of(committeePeriod);
    }

    if (nextSyncCommitteePeriod.isEmpty()) {
      final UInt64 firstEpochOfNextSyncCommitteePeriod =
          syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(dutiesEpoch);
      final int subscribeEpochsPriorToNextSyncPeriod =
          earlySubscribeRandomSource.randomEpochCount(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT);
      nextSyncCommitteePeriod =
          Optional.of(
              createSyncCommitteePeriod(
                  syncCommitteeUtil,
                  firstEpochOfNextSyncCommitteePeriod,
                  subscribeEpochsPriorToNextSyncPeriod));
    }

    if (isFirstSlotOfEpoch(slot)) {
      this.currentSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::requestSubnetSubscription);
      this.nextSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::requestSubnetSubscription);
    }

    final SyncCommitteePeriod nextSyncCommitteePeriod = this.nextSyncCommitteePeriod.get();
    if (dutiesEpoch.isGreaterThanOrEqualTo(nextSyncCommitteePeriod.subscribeEpoch)) {
      nextSyncCommitteePeriod.calculateDuties();
    }
    if (dutiesEpoch.isGreaterThanOrEqualTo(nextSyncCommitteePeriod.periodStartEpoch)) {
      this.currentSyncCommitteePeriod = this.nextSyncCommitteePeriod;
      this.nextSyncCommitteePeriod = Optional.empty();
    }
  }

  private boolean isFirstSlotOfEpoch(final UInt64 slot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final UInt64 startSlotAtEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    return startSlotAtEpoch.equals(slot);
  }

  private SyncCommitteePeriod createSyncCommitteePeriod(
      final SyncCommitteeUtil syncCommitteeUtil,
      final UInt64 periodStartEpoch,
      final int subscribeEpochsPriorToNextSyncPeriod) {
    return new SyncCommitteePeriod(
        periodStartEpoch,
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(periodStartEpoch),
        subscribeEpochsPriorToNextSyncPeriod);
  }

  @Override
  public void onSyncCommitteeCreationDue(final UInt64 slot) {
    // Check slot being null for the edge case of genesis slot (i.e. slot 0)
    if (lastProductionSlot != null && slot.compareTo(lastProductionSlot) <= 0) {
      LOG.debug(
          "Not producing sync committee message for slot {} because last production slot {} is beyond that.",
          slot,
          lastProductionSlot);
      return;
    }

    lastProductionSlot = slot;
    getDutiesForSlot(slot).ifPresent(duties -> duties.onProductionDue(slot));
  }

  @Override
  public void onContributionCreationDue(final UInt64 slot) {
    getDutiesForSlot(slot).ifPresent(duties -> duties.onAggregationDue(slot));
  }

  private Optional<PendingDuties> getDutiesForSlot(final UInt64 slot) {
    final Optional<SyncCommitteeUtil> maybeUtils = spec.getSyncCommitteeUtil(slot);
    if (maybeUtils.isEmpty()) {
      return Optional.empty();
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeUtils.get();
    final UInt64 epoch = syncCommitteeUtil.getEpochForDutiesAtSlot(slot);
    return Stream.of(currentSyncCommitteePeriod, nextSyncCommitteePeriod)
        .flatMap(Optional::stream)
        .filter(period -> period.isCurrentPeriodForEpoch(epoch))
        .findAny()
        .flatMap(period -> period.duties);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {

    // recalculation of sync committee duties on every new block prior to the altair fork activation
    if (spec.getSyncCommitteeUtil(slot).isEmpty()) {
      currentSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
      nextSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
    }
  }

  @Override
  public void onPossibleMissedEvents() {
    currentSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
    nextSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
  }

  @Override
  public void onValidatorsAdded() {
    currentSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
    nextSyncCommitteePeriod.ifPresent(SyncCommitteePeriod::recalculate);
  }

  private class SyncCommitteePeriod {
    private Optional<PendingDuties> duties = Optional.empty();
    private final UInt64 periodStartEpoch;
    private final UInt64 nextPeriodStartEpoch;
    private final UInt64 subscribeEpoch;

    private SyncCommitteePeriod(
        final UInt64 periodStartEpoch,
        final UInt64 nextPeriodStartEpoch,
        final int numberOfEpochsPriorToStartToSubscribe) {
      this.periodStartEpoch = periodStartEpoch;
      this.nextPeriodStartEpoch = nextPeriodStartEpoch;
      this.subscribeEpoch = periodStartEpoch.minusMinZero(numberOfEpochsPriorToStartToSubscribe);
    }

    public boolean isCurrentPeriodForEpoch(final UInt64 epoch) {
      return periodStartEpoch.isLessThanOrEqualTo(epoch) && epoch.isLessThan(nextPeriodStartEpoch);
    }

    public void calculateDuties() {

      // Always use the last epoch in the period since it's the most likely to still be in-memory
      // This also handles the case where the fork slot is within the sync committee period by
      // ensuring that we request an epoch after the fork slot has occurred.
      duties =
          duties.or(
              () ->
                  Optional.of(
                      PendingDuties.calculateDuties(
                          metricsSystem, dutyLoader, nextPeriodStartEpoch.minusMinZero(1))));
    }

    public void requestSubnetSubscription() {
      duties
          .flatMap(PendingDuties::getScheduledDuties)
          .map(SyncCommitteeScheduledDuties.class::cast)
          .ifPresent(SyncCommitteeScheduledDuties::subscribeToSubnets);
    }

    public void recalculate() {
      duties.ifPresent(PendingDuties::recalculate);
    }
  }

  public interface EarlySubscribeRandomSource {
    int randomEpochCount(final int epochsPerSyncCommitteePeriod);
  }
}

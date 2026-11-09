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

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class SyncCommitteeDutyLoader
    extends AbstractDutyLoader<SyncCommitteeDuties, SyncCommitteeScheduledDuties> {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;
  private final ChainHeadTracker chainHeadTracker;
  private final ForkProvider forkProvider;

  private final SettableGauge currentSyncDutyCount;
  private final SettableGauge currentSyncCommitteeLastEpoch;
  private final boolean useDvtEndpoint;

  public SyncCommitteeDutyLoader(
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final ChainHeadTracker chainHeadTracker,
      final ForkProvider forkProvider,
      final MetricsSystem metricsSystem,
      final boolean useDvtEndpoint) {
    super(validators, validatorIndexProvider);
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
    this.chainHeadTracker = chainHeadTracker;
    this.forkProvider = forkProvider;
    this.currentSyncDutyCount =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "scheduled_sync_committee_duties_current",
            "Current number of Sync committee members performing duties");
    this.currentSyncCommitteeLastEpoch =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "current_sync_committee_last_epoch",
            "The final epoch of the current sync committee period");
    this.useDvtEndpoint = useDvtEndpoint;
  }

  @Override
  protected SafeFuture<Optional<SyncCommitteeDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return validatorApiChannel
        .getSyncCommitteeDuties(epoch, validatorIndices)
        .thenPeek(
            maybeDuties ->
                currentSyncDutyCount.set(
                    maybeDuties
                        .filter(duties -> !duties.isExecutionOptimistic())
                        .map(duties -> duties.getDuties().size())
                        .orElse(0)));
  }

  @Override
  protected SafeFuture<SyncCommitteeScheduledDuties> scheduleAllDuties(
      final UInt64 epoch, final SyncCommitteeDuties duties) {
    if (duties.isExecutionOptimistic()) {
      LOG.debug("Skipping Sync committee duties because execution is optimistic.");
      return NodeSyncingException.failedFuture();
    }
    final UInt64 lastEpochInCommitteePeriod =
        spec.getSyncCommitteeUtilRequired(spec.computeStartSlotAtEpoch(epoch))
            .computeFirstEpochOfNextSyncCommitteePeriod(epoch)
            .minusMinZero(1);
    final SyncCommitteeScheduledDuties.Builder dutyBuilder =
        SyncCommitteeScheduledDuties.builder()
            .forkProvider(forkProvider)
            .validatorApiChannel(validatorApiChannel)
            .chainHeadTracker(chainHeadTracker)
            .spec(spec)
            .lastEpochInCommitteePeriod(lastEpochInCommitteePeriod)
            .useDvtEndpoint(useDvtEndpoint);
    duties.getDuties().forEach(duty -> scheduleDuty(dutyBuilder, duty));
    final SyncCommitteeScheduledDuties scheduledDuties = dutyBuilder.build();
    scheduledDuties.subscribeToSubnets();

    currentSyncCommitteeLastEpoch.set(lastEpochInCommitteePeriod.intValue());
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final SyncCommitteeScheduledDuties.Builder dutyBuilder, final SyncCommitteeDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(
            validator ->
                dutyBuilder.committeeAssignments(
                    validator, duty.getValidatorIndex(), duty.getValidatorSyncCommitteeIndices()));
  }

  @Override
  public String getDutyType() {
    return "Sync Committee";
  }
}

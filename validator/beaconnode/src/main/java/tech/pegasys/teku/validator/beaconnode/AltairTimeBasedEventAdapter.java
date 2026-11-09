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

package tech.pegasys.teku.validator.beaconnode;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class AltairTimeBasedEventAdapter extends TimeBasedEventAdapter {

  public AltairTimeBasedEventAdapter(
      final RepeatingTaskScheduler taskScheduler,
      final ValidatorTimingChannel validatorTimingChannel,
      final Runnable onLastSlot,
      final Spec spec) {
    super(
        spec.computeStartSlotAtEpoch(
            spec.getForkSchedule().getFork(SpecMilestone.ALTAIR).getEpoch()),
        taskScheduler,
        validatorTimingChannel,
        onLastSlot,
        spec);
  }

  @Override
  void scheduleDuties(
      final UInt64 nextSlotStartTimeMillis, final Optional<UInt64> expirationTimeMillis) {

    scheduleOnSlot(nextSlotStartTimeMillis, expirationTimeMillis);

    scheduleAll(
        nextSlotStartTimeMillis,
        expirationTimeMillis,
        new ScheduledEvent(
            specVersion.getForkChoiceUtil().getAttestationDueMillis(),
            this::onAttestationCreationDue),
        new ScheduledEvent(
            specVersion.getForkChoiceUtil().getAggregateDueMillis(), this::onAggregationDue),
        new ScheduledEvent(
            specVersion.getForkChoiceUtil().getSyncMessageDueMillis(),
            this::onSyncCommitteeCreationDue),
        new ScheduledEvent(
            specVersion.getForkChoiceUtil().getContributionDueMillis(),
            this::onContributionCreationDue));
  }
}

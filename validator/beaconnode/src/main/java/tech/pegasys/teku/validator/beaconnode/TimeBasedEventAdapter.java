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

package tech.pegasys.teku.validator.beaconnode;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.RepeatingTask;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class TimeBasedEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 firstSlot;
  private final UInt64 millisPerSlot;
  private final Runnable onLastSlot;
  private UInt64 genesisTimeMillis;

  protected final ValidatorTimingChannel validatorTimingChannel;
  protected final SpecVersion specVersion;
  protected final RepeatingTaskScheduler taskScheduler;

  public TimeBasedEventAdapter(
      final UInt64 firstSlot,
      final RepeatingTaskScheduler taskScheduler,
      final ValidatorTimingChannel validatorTimingChannel,
      final Runnable onLastSlot,
      final Spec spec) {
    this.firstSlot = firstSlot;
    this.taskScheduler = taskScheduler;
    this.validatorTimingChannel = validatorTimingChannel;
    this.specVersion = spec.atSlot(firstSlot);
    this.onLastSlot = onLastSlot;
    this.millisPerSlot = UInt64.valueOf(spec.getSlotDurationMillis(firstSlot));
  }

  abstract void scheduleDuties(
      UInt64 nextSlotStartTimeMillis, Optional<UInt64> expirationTimeMillis);

  void setGenesisTimeMillis(final UInt64 genesisTimeMillis) {
    this.genesisTimeMillis = genesisTimeMillis;
  }

  protected record ScheduledEvent(int offsetMillis, RepeatingTask handler) {}

  /** Returns the first slot of this adapter's milestone (used for chain expiry computation). */
  protected UInt64 getFirstSlot() {
    return firstSlot;
  }

  protected void scheduleAll(
      final UInt64 nextSlotStartTimeMillis,
      final Optional<UInt64> expirationTimeMillis,
      final ScheduledEvent... events) {

    LOG.debug(
        "Scheduling {} events for {} starting at {} with period {}ms, expiry {}",
        events.length,
        getClass().getSimpleName(),
        nextSlotStartTimeMillis,
        millisPerSlot,
        expirationTimeMillis.map(UInt64::toString).orElse("none"));

    for (final ScheduledEvent event : events) {
      final UInt64 firstInvocation = nextSlotStartTimeMillis.plus(event.offsetMillis());
      scheduleEvent(firstInvocation, event.handler, expirationTimeMillis, Optional.empty());
    }
  }

  private void scheduleEvent(
      final UInt64 firstInvocation,
      final RepeatingTask handler,
      final Optional<UInt64> expirationTimeMillis,
      final Optional<Runnable> onExpired) {

    expirationTimeMillis.ifPresentOrElse(
        expiration ->
            taskScheduler.scheduleRepeatingEventInMillis(
                firstInvocation,
                millisPerSlot,
                handler,
                expiration,
                (__, ___) -> onExpired.ifPresent(Runnable::run)),
        () ->
            taskScheduler.scheduleRepeatingEventInMillis(firstInvocation, millisPerSlot, handler));
  }

  protected void scheduleOnSlot(
      final UInt64 nextSlotStartTimeMillis, final Optional<UInt64> expirationTimeMillis) {
    LOG.debug(
        "Scheduling onSlot event for {} starting at {} with period {}ms, expiry {}",
        getClass().getSimpleName(),
        nextSlotStartTimeMillis,
        millisPerSlot,
        expirationTimeMillis.map(UInt64::toString).orElse("none"));

    scheduleEvent(
        nextSlotStartTimeMillis, this::onStartSlot, expirationTimeMillis, Optional.of(onLastSlot));
  }

  // handlers //

  void onStartSlot(final UInt64 scheduledTimeMillis, final UInt64 actualTimeMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeMillis);
    if (isTooLateInMillis(scheduledTimeMillis, actualTimeMillis)) {
      LOG.warn(
          "Skipping block creation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  void onAttestationCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping attestation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationCreationDue(slot);
  }

  void onAggregationDue(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping aggregation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationAggregationDue(slot);
  }

  void onInclusionListCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping inclusion list creation for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onInclusionListCreationDue(slot);
  }

  void onSyncCommitteeCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping sync committee message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onSyncCommitteeCreationDue(slot);
  }

  void onContributionCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping contribution message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onContributionCreationDue(slot);
  }

  // utils //

  protected UInt64 getCurrentSlotForMillis(final UInt64 millis) {
    return specVersion.miscHelpers().computeSlotAtTimeMillis(genesisTimeMillis, millis);
  }

  boolean isTooLateInMillis(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    return scheduledTimeInMillis.plus(millisPerSlot).isLessThan(actualTimeInMillis);
  }
}

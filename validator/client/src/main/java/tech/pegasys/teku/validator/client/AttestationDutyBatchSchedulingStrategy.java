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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

/**
 * Attestation duty scheduling is batched by slots and delays are added in order to avoid expensive
 * aggregation slot signing in beginning of the epoch when a node is running a large number of
 * validators
 */
public class AttestationDutyBatchSchedulingStrategy
    extends AbstractAttestationDutySchedulingStrategy implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  public record SlotBatchingOptions(
      int currentEpochSlotsToScheduleBeforeDelay,
      Duration currentEpochSchedulingDelay,
      int futureEpochSlotsToScheduleBeforeDelay,
      Duration futureEpochSchedulingDelay) {}

  public static final SlotBatchingOptions DEFAULT_SLOT_BATCHING_OPTIONS =
      new SlotBatchingOptions(4, Duration.ofMillis(500), 1, Duration.ofSeconds(1));

  private final AtomicReference<UInt64> currentSlot = new AtomicReference<>(UInt64.ZERO);

  private final AsyncRunner asyncRunner;
  private final SlotBatchingOptions slotBatchingOptions;

  public AttestationDutyBatchSchedulingStrategy(
      final Spec spec,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final AsyncRunner asyncRunner) {
    this(
        spec,
        forkProvider,
        scheduledDutiesFactory,
        validators,
        beaconCommitteeSubscriptions,
        asyncRunner,
        DEFAULT_SLOT_BATCHING_OPTIONS);
  }

  @VisibleForTesting
  AttestationDutyBatchSchedulingStrategy(
      final Spec spec,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final AsyncRunner asyncRunner,
      final SlotBatchingOptions slotBatchingOptions) {
    super(spec, forkProvider, scheduledDutiesFactory, validators, beaconCommitteeSubscriptions);
    this.asyncRunner = asyncRunner;
    this.slotBatchingOptions = slotBatchingOptions;
  }

  @Override
  public SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties =
        getScheduledDuties(duties);

    // every X amount of slots a delay is added to the scheduling (values are based on if current or
    // future epoch)
    final boolean isCurrentEpoch =
        epoch.isLessThanOrEqualTo(spec.computeEpochAtSlot(currentSlot.get()));
    final int slotsBeforeDelay =
        isCurrentEpoch
            ? slotBatchingOptions.currentEpochSlotsToScheduleBeforeDelay()
            : slotBatchingOptions.futureEpochSlotsToScheduleBeforeDelay();
    final Duration schedulingDelay =
        isCurrentEpoch
            ? slotBatchingOptions.currentEpochSchedulingDelay()
            : slotBatchingOptions.futureEpochSchedulingDelay();

    final Map<UInt64, List<AttesterDuty>> dutiesBySlot =
        duties.getDuties().stream()
            .collect(
                Collectors.groupingBy(AttesterDuty::getSlot, TreeMap::new, Collectors.toList()));

    LOG.info(
        "Scheduling {} attestation duties for epoch {}, batched across {} slot(s) with a {} ms delay every {} slot(s)",
        duties.getDuties().size(),
        epoch,
        dutiesBySlot.size(),
        schedulingDelay.toMillis(),
        slotsBeforeDelay);

    SafeFuture<Void> dutiesScheduling = SafeFuture.COMPLETE;

    int i = 0;
    for (final List<AttesterDuty> dutiesForSlot : dutiesBySlot.values()) {
      // no delay at start
      if (i != 0 && i % slotsBeforeDelay == 0) {
        dutiesScheduling =
            dutiesScheduling.thenCompose(
                __ ->
                    asyncRunner.runAfterDelay(
                        () -> scheduleDuties(scheduledDuties, dutiesForSlot, Optional.empty()),
                        schedulingDelay));
      } else {
        dutiesScheduling =
            dutiesScheduling.thenCompose(
                __ -> scheduleDuties(scheduledDuties, dutiesForSlot, Optional.empty()));
      }
      i++;
    }

    return dutiesScheduling
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot.set(slot);
  }
}

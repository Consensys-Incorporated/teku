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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyDefaultSchedulingStrategy
    extends AbstractAttestationDutySchedulingStrategy implements ValidatorTimingChannel {

  private final ValidatorApiChannel validatorApiChannel;
  private final boolean useDvtEndpoint;
  private final AtomicReference<UInt64> currentSlot = new AtomicReference<>(UInt64.ZERO);
  private final ConcurrentMap<UInt64, DvtAttestationAggregations> pendingDvtAggregationsByEpoch =
      new ConcurrentHashMap<>();

  public AttestationDutyDefaultSchedulingStrategy(
      final Spec spec,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final ValidatorApiChannel validatorApiChannel,
      final boolean useDvtEndpoint) {
    super(spec, forkProvider, scheduledDutiesFactory, validators, beaconCommitteeSubscriptions);
    this.validatorApiChannel = validatorApiChannel;
    this.useDvtEndpoint = useDvtEndpoint;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot.set(slot);
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    pendingDvtAggregationsByEpoch.forEach(
        (epoch, dvtAttestationAggregations) -> {
          if (epoch.isLessThanOrEqualTo(currentEpoch)) {
            dvtAttestationAggregations.activate();
          }
        });
    pendingDvtAggregationsByEpoch
        .entrySet()
        .removeIf(entry -> entry.getKey().isLessThan(currentEpoch));
  }

  @Override
  public SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties =
        getScheduledDuties(duties);

    final Optional<DvtAttestationAggregations> dvtAttestationAggregations =
        createDvtAttestationAggregations(epoch, duties.getDuties().size());

    final SafeFuture<Void> dutiesScheduling =
        scheduleDuties(scheduledDuties, duties.getDuties(), dvtAttestationAggregations)
            .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
    if (dvtAttestationAggregations.isPresent()) {
      dutiesScheduling.finishStackTrace();
      return SafeFuture.completedFuture(scheduledDuties);
    }
    return dutiesScheduling.<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties);
  }

  private Optional<DvtAttestationAggregations> createDvtAttestationAggregations(
      final UInt64 epoch, final int expectedDutiesCount) {
    if (!useDvtEndpoint || expectedDutiesCount == 0) {
      cancelPendingDvtAttestationAggregations(epoch);
      return Optional.empty();
    }

    final DvtAttestationAggregations dvtAttestationAggregations =
        new DvtAttestationAggregations(validatorApiChannel, epoch, expectedDutiesCount);
    final DvtAttestationAggregations previous =
        pendingDvtAggregationsByEpoch.put(epoch, dvtAttestationAggregations);
    if (previous != null) {
      previous.cancel();
    }
    if (isCurrentOrPastEpoch(epoch)) {
      dvtAttestationAggregations.activate();
    }
    return Optional.of(dvtAttestationAggregations);
  }

  private void cancelPendingDvtAttestationAggregations(final UInt64 epoch) {
    final DvtAttestationAggregations previous = pendingDvtAggregationsByEpoch.remove(epoch);
    if (previous != null) {
      previous.cancel();
    }
  }

  private boolean isCurrentOrPastEpoch(final UInt64 epoch) {
    return epoch.isLessThanOrEqualTo(spec.computeEpochAtSlot(currentSlot.get()));
  }
}

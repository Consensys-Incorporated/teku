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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;

public class AttestationDutyScheduler extends AbstractDutyScheduler {
  private static final Logger LOG = LogManager.getLogger();
  private static final int LOOKAHEAD_EPOCHS = 1;

  private final AtomicInteger nextAttestationSlot = new AtomicInteger();

  public AttestationDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader<?> dutyLoader, final Spec spec) {
    super(metricsSystem, "attestation", dutyLoader, spec);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_attestation_duties_current",
        "Current number of pending attestation duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(PendingDuties::countDuties).sum());

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "next_attestation_slot",
        "Next slot that a validator has an attestation production duty scheduled",
        nextAttestationSlot::get);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);

    if (slot.intValue() >= nextAttestationSlot.get()) {
      getNextAttestationSlotScheduled().ifPresent(nextAttestationSlot::set);
    }
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    onProductionDue(slot);
  }

  @Override
  protected Bytes32 getExpectedDependentRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final UInt64 headEpoch,
      final UInt64 dutyEpoch) {
    checkArgument(
        dutyEpoch.isGreaterThanOrEqualTo(headEpoch),
        "Attempting to calculate dependent root for duty epoch %s that is before the updated head epoch %s",
        dutyEpoch,
        headEpoch);
    if (headEpoch.equals(dutyEpoch)) {
      LOG.debug("headEpoch {} - returning previousDutyDependentRoot", () -> headEpoch);
      return previousDutyDependentRoot;
    } else if (headEpoch.increment().equals(dutyEpoch)) {
      LOG.debug("dutyEpoch (next epoch) {} - returning currentDutyDependentRoot", () -> dutyEpoch);
      return currentDutyDependentRoot;
    } else {
      LOG.debug(
          "headBlockRoot returned - dutyEpoch {}, headEpoch {}", () -> dutyEpoch, () -> headEpoch);
      return headBlockRoot;
    }
  }

  @Override
  int getLookAheadEpochs(final UInt64 epoch) {
    return LOOKAHEAD_EPOCHS;
  }

  @VisibleForTesting
  @SuppressWarnings({"RawUseOfParameterized", "unchecked"})
  Optional<Integer> getNextAttestationSlotScheduled() {
    // Find all attestation production duties scheduled slots, then returning the earliest
    return dutiesByEpoch.values().stream()
        .map(PendingDuties::getScheduledDuties)
        .flatMap(Optional::stream)
        .filter(sd -> sd instanceof SlotBasedScheduledDuties)
        .map(
            sd -> {
              final Optional<UInt64> nextAttestationProductionDutySlot =
                  ((SlotBasedScheduledDuties) sd).getNextAttestationProductionDutyScheduledSlot();
              return nextAttestationProductionDutySlot.map(UInt64::intValue);
            })
        .flatMap(Optional::stream)
        .min(Integer::compareTo);
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class PayloadTimelinessCommitteeDutyScheduler extends AbstractDutyScheduler {

  private static final Logger LOG = LogManager.getLogger();
  private static final int LOOKAHEAD_EPOCHS = 1;

  private Optional<UInt64> currentSlot = Optional.empty();

  public PayloadTimelinessCommitteeDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader<?> dutyLoader, final Spec spec) {
    super(metricsSystem, "payload_attestation", dutyLoader, spec);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_payload_attestation_duties_current",
        "Current number of pending payload attestation duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(PendingDuties::countDuties).sum());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = Optional.of(slot);
    super.onSlot(slot);
  }

  /**
   * A payload timeliness attestation is only meaningful for the current slot, and peers ignore any
   * other, so a payload which became available for an earlier slot, for example while the beacon
   * node catches up, must not start the duty.
   */
  @Override
  public void onPayloadAttestationCreationDue(final UInt64 slot) {
    if (currentSlot.map(slot::equals).orElse(false)) {
      onProductionDue(slot);
    }
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

  @Override
  protected boolean shouldScheduleDutiesAtEpoch(final UInt64 epochNumber) {
    return spec.atEpoch(epochNumber).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
  }
}

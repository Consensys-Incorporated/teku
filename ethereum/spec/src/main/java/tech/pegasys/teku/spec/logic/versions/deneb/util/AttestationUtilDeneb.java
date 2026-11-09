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

package tech.pegasys.teku.spec.logic.versions.deneb.util;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.phase0.util.AttestationUtilPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilDeneb extends AttestationUtilPhase0 {

  public AttestationUtilDeneb(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   * [IGNORE] attestation.data.slot is equal to or earlier than the current_slot (with a
   * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot &lt;= current_slot (a
   * client MAY queue future attestation for processing at the appropriate slot).
   *
   * <p>[IGNORE] the epoch of attestation.data.slot is either the current or previous epoch (with a
   * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. compute_epoch_at_slot(attestation.data.slot)
   * in (get_previous_epoch(state), get_current_epoch(state))
   */
  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotInclusionGossipValidation(
      final Attestation attestation,
      final UInt64 genesisTimeSeconds,
      final UInt64 currentTimeMillis) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    if (isAttestationSlotAfterCurrentTime(attestationSlot, genesisTimeSeconds, currentTimeMillis)) {
      return Optional.of(
          isFromFarFuture(attestation, genesisTimeSeconds, currentTimeMillis)
              ? SlotInclusionGossipValidationResult.IGNORE
              : SlotInclusionGossipValidationResult.SAVE_FOR_FUTURE);
    }
    if (!isAttestationSlotInCurrentOrPreviousEpoch(
        attestationSlot, genesisTimeSeconds, currentTimeMillis)) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }
    return Optional.empty();
  }

  @VisibleForTesting
  boolean isAttestationSlotAfterCurrentTime(
      final UInt64 attestationSlot,
      final UInt64 genesisTimeSeconds,
      final UInt64 currentTimeMillis) {
    final UInt64 attestationSlotTimeMillis =
        secondsToMillis(miscHelpers.computeTimeAtSlot(genesisTimeSeconds, attestationSlot));
    return attestationSlotTimeMillis.isGreaterThan(
        currentTimeMillis.plus(specConfig.getMaximumGossipClockDisparity()));
  }

  @VisibleForTesting
  boolean isAttestationSlotInCurrentOrPreviousEpoch(
      final UInt64 attestationSlot,
      final UInt64 genesisTimeSeconds,
      final UInt64 currentTimeMillis) {
    final UInt64 attestationEpoch = miscHelpers.computeEpochAtSlot(attestationSlot);
    final UInt64 epochInclusiveEndSlotOffset = UInt64.valueOf(specConfig.getSlotsPerEpoch() - 1);
    return miscHelpers.isCurrentTimeWithinInclusiveSlotRange(
            miscHelpers.computeStartSlotAtEpoch(attestationEpoch.plus(1)),
            epochInclusiveEndSlotOffset,
            genesisTimeSeconds,
            currentTimeMillis)
        || miscHelpers.isCurrentTimeWithinInclusiveSlotRange(
            miscHelpers.computeStartSlotAtEpoch(attestationEpoch),
            epochInclusiveEndSlotOffset,
            genesisTimeSeconds,
            currentTimeMillis);
  }
}

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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.ATTESTATION_INCLUSION_RANGE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class ActiveValidatorTrackerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  private final ActiveValidatorTracker tracker = new ActiveValidatorTracker(spec);

  @Test
  void shouldUpdateValidatorCountAtStartOfEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    tracker.onSlot(epochStartSlot);
  }

  @Test
  void shouldNotCountDuplicateValidators() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);

    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    tracker.onSlot(epochStartSlot);
  }

  @Test
  void shouldPruneValidatorCountsAtTheEndOfAttestationInclusionRangeEpochs() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 afterInclusionRangeStartSlot =
        spec.computeStartSlotAtEpoch(epoch.plus(ATTESTATION_INCLUSION_RANGE).plus(1));

    // For the purpose of testing, we get the slots out of order, so all the requests get dropped
    tracker.onSlot(afterInclusionRangeStartSlot);
    tracker.onSlot(epochStartSlot);
  }

  @Test
  void shouldNotPruneBeforeTheEndOfAttestationInclusionRangeEpochs() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 rightBeforeInclusionRangeStartSlot =
        spec.computeStartSlotAtEpoch(epoch.plus(ATTESTATION_INCLUSION_RANGE));

    // For the purpose of testing, we get the slots out of order, to see if the requests get dropped
    tracker.onSlot(rightBeforeInclusionRangeStartSlot);
    tracker.onSlot(epochStartSlot);
  }
}

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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

/**
 * Builds the expected duty events for a slot based on its milestone, advances time to each offset,
 * verifies the events fire, and verifies milestone-inappropriate events do NOT fire.
 */
class SlotEventVerifierTestUtil {

  /**
   * @return the total millis advanced within the slot (from slot start)
   */
  static long verifySlotEvents(
      final Spec spec,
      final StubTimeProvider timeProvider,
      final StubAsyncRunner asyncRunner,
      final ValidatorTimingChannel validatorTimingChannel,
      final UInt64 slot) {
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();
    final TreeMap<Integer, List<Runnable>> expectedByOffset = new TreeMap<>();

    // All milestones: attestation + aggregation
    addExpected(
        expectedByOffset,
        spec.getAttestationDueMillis(slot),
        () -> verify(validatorTimingChannel, times(1)).onAttestationCreationDue(slot));
    addExpected(
        expectedByOffset,
        spec.getAggregateDueMillis(slot),
        () -> verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(slot));

    // ALTAIR+: sync committee + contribution
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      addExpected(
          expectedByOffset,
          spec.getSyncMessageDueMillis(slot),
          () -> verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(slot));
      addExpected(
          expectedByOffset,
          spec.getContributionDueMillis(slot),
          () -> verify(validatorTimingChannel, times(1)).onContributionCreationDue(slot));
    }

    // GLOAS: payload attestation
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      addExpected(
          expectedByOffset,
          spec.getPayloadAttestationDueMillis(slot).orElseThrow(),
          () -> verify(validatorTimingChannel, times(1)).onPayloadAttestationCreationDue(slot));
    }

    // HEZE: inclusion list production
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE)) {
      addExpected(
          expectedByOffset,
          0,
          () -> verify(validatorTimingChannel, times(1)).onInclusionListCreationDue(slot));
    }

    // Walk through the slot, advancing to each unique offset and verifying
    long advancedMillis = 0;
    for (final Map.Entry<Integer, List<Runnable>> entry : expectedByOffset.entrySet()) {
      final long offsetMillis = entry.getKey();
      timeProvider.advanceTimeByMillis(offsetMillis - advancedMillis);
      asyncRunner.executeDueActionsRepeatedly();
      entry.getValue().forEach(Runnable::run);
      advancedMillis = offsetMillis;
    }

    // Verify events that should NOT fire for this milestone
    if (!milestone.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      verify(validatorTimingChannel, never()).onSyncCommitteeCreationDue(slot);
      verify(validatorTimingChannel, never()).onContributionCreationDue(slot);
    }
    if (!milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      verify(validatorTimingChannel, never()).onPayloadAttestationCreationDue(slot);
    }
    if (!milestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE)) {
      verify(validatorTimingChannel, never()).onInclusionListCreationDue(slot);
    }

    return advancedMillis;
  }

  private static void addExpected(
      final Map<Integer, List<Runnable>> map, final int offset, final Runnable verifier) {
    map.computeIfAbsent(offset, k -> new ArrayList<>()).add(verifier);
  }
}

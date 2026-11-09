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

import static tech.pegasys.teku.beacon.pow.api.Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToFollowDistance;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class Eth1VotingPeriod {

  private final UInt64 cacheDuration;
  private final Spec spec;

  public Eth1VotingPeriod(final Spec spec) {
    this.spec = spec;
    this.cacheDuration =
        calculateEth1DataCacheDurationPriorToFollowDistance(spec.getGenesisSpecConfig());
  }

  public UInt64 getSpecRangeLowerBound(final UInt64 slot, final UInt64 genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot,
        genesisTime,
        spec.getEth1FollowDistance(slot).times(spec.getSecondsPerEth1Block(slot)).times(2));
  }

  public UInt64 getSpecRangeUpperBound(final UInt64 slot, final UInt64 genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot,
        genesisTime,
        spec.getEth1FollowDistance(slot).times(spec.getSecondsPerEth1Block(slot)));
  }

  public long getTotalSlotsInVotingPeriod(final UInt64 slot) {
    return spec.getEpochsPerEth1VotingPeriod(slot) * spec.getSlotsPerEpoch(slot);
  }

  public UInt64 computeVotingPeriodStartSlot(final UInt64 slot) {
    return slot.minus(slot.mod(getTotalSlotsInVotingPeriod(slot)));
  }

  private UInt64 secondsBeforeCurrentVotingPeriodStartTime(
      final UInt64 slot, final UInt64 genesisTime, final UInt64 valueToSubtract) {
    final UInt64 currentVotingPeriodStartTime = getVotingPeriodStartTime(slot, genesisTime);
    if (currentVotingPeriodStartTime.isGreaterThan(valueToSubtract)) {
      return currentVotingPeriodStartTime.minus(valueToSubtract);
    } else {
      return UInt64.ZERO;
    }
  }

  private UInt64 getVotingPeriodStartTime(final UInt64 slot, final UInt64 genesisTime) {
    final UInt64 eth1VotingPeriodStartSlot = computeVotingPeriodStartSlot(slot);
    return spec.computeTimeAtSlot(eth1VotingPeriodStartSlot, genesisTime);
  }

  public UInt64 getCacheDurationInSeconds() {
    return cacheDuration;
  }
}

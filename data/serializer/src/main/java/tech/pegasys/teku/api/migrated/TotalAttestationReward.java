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

package tech.pegasys.teku.api.migrated;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.DetailedRewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;

public class TotalAttestationReward {

  private final long validatorIndex;
  private final long head;
  private final long target;
  private final long source;
  private final Optional<UInt64> inclusionDelay;
  private final long inactivity;

  public TotalAttestationReward(
      final long validatorIndex,
      final long head,
      final long target,
      final long source,
      final Optional<UInt64> inclusionDelay,
      final long inactivity) {
    this.validatorIndex = validatorIndex;
    this.head = head;
    this.target = target;
    this.source = source;
    this.inclusionDelay = inclusionDelay;
    this.inactivity = inactivity;
  }

  public TotalAttestationReward(
      final long validatorIndex, final RewardAndPenalty rewardAndPenalty) {
    this.validatorIndex = validatorIndex;

    final DetailedRewardAndPenalty detailedRewardAndPenalty =
        rewardAndPenalty
            .asDetailed()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "TotalAttestationRewards requires a DetailedRewardAndPenalty instance"));

    this.head =
        detailedRewardAndPenalty.getReward(RewardComponent.HEAD).longValue()
            - detailedRewardAndPenalty.getPenalty(RewardComponent.HEAD).longValue();
    this.target =
        detailedRewardAndPenalty.getReward(RewardComponent.TARGET).longValue()
            - detailedRewardAndPenalty.getPenalty(RewardComponent.TARGET).longValue();
    this.source =
        detailedRewardAndPenalty.getReward(RewardComponent.SOURCE).longValue()
            - detailedRewardAndPenalty.getPenalty(RewardComponent.SOURCE).longValue();
    this.inactivity =
        detailedRewardAndPenalty.getReward(RewardComponent.INACTIVITY).longValue()
            - detailedRewardAndPenalty.getPenalty(RewardComponent.INACTIVITY).longValue();

    // Inclusion delay will always be empty because we don't support phase0 on the Rewards API
    this.inclusionDelay = Optional.empty();
  }

  public long getValidatorIndex() {
    return validatorIndex;
  }

  public long getHead() {
    return head;
  }

  public long getTarget() {
    return target;
  }

  public long getSource() {
    return source;
  }

  public Optional<UInt64> getInclusionDelay() {
    return inclusionDelay;
  }

  public long getInactivity() {
    return inactivity;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TotalAttestationReward that = (TotalAttestationReward) o;
    return validatorIndex == that.validatorIndex
        && head == that.head
        && target == that.target
        && source == that.source
        && inclusionDelay.equals(that.inclusionDelay)
        && inactivity == that.inactivity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, head, target, source, inclusionDelay, inactivity);
  }
}

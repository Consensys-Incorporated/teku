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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AggregatedRewardAndPenalty implements RewardAndPenalty {

  private UInt64 reward = UInt64.ZERO;
  private UInt64 penalty = UInt64.ZERO;

  @Override
  public void reward(final RewardComponent component, final UInt64 amount) {
    // Ignoring reward component
    reward(amount);
  }

  @Override
  public void penalize(final RewardComponent component, final UInt64 amount) {
    // Ignoring penalize component
    penalize(amount);
  }

  private void reward(final UInt64 amount) {
    reward = reward.plus(amount);
  }

  private void penalize(final UInt64 amount) {
    penalty = penalty.plus(amount);
  }

  public void add(final AggregatedRewardAndPenalty other) {
    reward(other.reward);
    penalize(other.penalty);
  }

  @Override
  public UInt64 getReward() {
    return reward;
  }

  @Override
  public UInt64 getPenalty() {
    return penalty;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AggregatedRewardAndPenalty delta = (AggregatedRewardAndPenalty) o;
    return Objects.equals(reward, delta.reward) && Objects.equals(penalty, delta.penalty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reward, penalty);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reward", reward)
        .add("penalty", penalty)
        .toString();
  }
}

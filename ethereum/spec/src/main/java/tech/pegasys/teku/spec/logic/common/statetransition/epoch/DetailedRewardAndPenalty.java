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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DetailedRewardAndPenalty implements RewardAndPenalty {

  private final Map<RewardComponent, UInt64> rewards =
      new HashMap<>(RewardComponent.values().length);
  private final Map<RewardComponent, UInt64> penalties =
      new HashMap<>(RewardComponent.values().length);

  public DetailedRewardAndPenalty() {
    Arrays.stream(RewardComponent.values())
        .forEach(
            component -> {
              rewards.put(component, UInt64.ZERO);
              penalties.put(component, UInt64.ZERO);
            });
  }

  @Override
  public void reward(final RewardComponent component, final UInt64 amount) {
    rewards.merge(component, amount, UInt64::plus);
  }

  @Override
  public void penalize(final RewardComponent component, final UInt64 amount) {
    penalties.merge(component, amount, UInt64::plus);
  }

  public UInt64 getReward(final RewardComponent component) {
    return rewards.get(component);
  }

  public UInt64 getPenalty(final RewardComponent component) {
    return penalties.get(component);
  }

  @Override
  public UInt64 getReward() {
    return rewards.values().stream().reduce(UInt64::plus).orElse(UInt64.ZERO);
  }

  @Override
  public UInt64 getPenalty() {
    return penalties.values().stream().reduce(UInt64::plus).orElse(UInt64.ZERO);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DetailedRewardAndPenalty that = (DetailedRewardAndPenalty) o;
    return Objects.equals(rewards, that.rewards) && Objects.equals(penalties, that.penalties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rewards, penalties);
  }

  @Override
  public String toString() {
    return "DetailedRewardAndPenalty{" + "rewards=" + rewards + ", penalties=" + penalties + '}';
  }
}

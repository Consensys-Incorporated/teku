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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;

class DetailedRewardAndPenaltyTest {

  @Test
  public void shouldHaveConsistentTotalReward() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();

    List.of(RewardComponent.TARGET, RewardComponent.SOURCE, RewardComponent.HEAD)
        .forEach(c -> rewardAndPenalty.reward(c, UInt64.ONE));

    assertThat(rewardAndPenalty.getReward()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  public void shouldHaveConsistentRewardPerComponent() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();

    rewardAndPenalty.reward(RewardComponent.TARGET, UInt64.ONE);
    rewardAndPenalty.reward(RewardComponent.SOURCE, UInt64.ONE);
    rewardAndPenalty.reward(RewardComponent.HEAD, UInt64.ONE);

    assertThat(rewardAndPenalty.getReward(RewardComponent.TARGET)).isEqualTo(UInt64.ONE);
    assertThat(rewardAndPenalty.getReward(RewardComponent.SOURCE)).isEqualTo(UInt64.ONE);
    assertThat(rewardAndPenalty.getReward(RewardComponent.HEAD)).isEqualTo(UInt64.ONE);
  }

  @Test
  public void shouldHaveConsistentTotalPenalties() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();

    List.of(
            RewardComponent.TARGET,
            RewardComponent.SOURCE,
            RewardComponent.HEAD,
            RewardComponent.INACTIVITY)
        .forEach(c -> rewardAndPenalty.penalize(c, UInt64.ONE));

    assertThat(rewardAndPenalty.getPenalty()).isEqualTo(UInt64.valueOf(4));
  }

  @Test
  public void shouldHaveConsistentPenaltyPerComponent() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();

    rewardAndPenalty.penalize(RewardComponent.TARGET, UInt64.ONE);
    rewardAndPenalty.penalize(RewardComponent.SOURCE, UInt64.ONE);
    rewardAndPenalty.penalize(RewardComponent.HEAD, UInt64.ONE);
    rewardAndPenalty.penalize(RewardComponent.INACTIVITY, UInt64.ONE);

    assertThat(rewardAndPenalty.getPenalty(RewardComponent.TARGET)).isEqualTo(UInt64.ONE);
    assertThat(rewardAndPenalty.getPenalty(RewardComponent.SOURCE)).isEqualTo(UInt64.ONE);
    assertThat(rewardAndPenalty.getPenalty(RewardComponent.HEAD)).isEqualTo(UInt64.ONE);
    assertThat(rewardAndPenalty.getPenalty(RewardComponent.INACTIVITY)).isEqualTo(UInt64.ONE);
  }

  @Test
  public void shouldBeEqualWhenSameRewardsAndPenaltiesAreApplied() {
    final DetailedRewardAndPenalty rp1 = new DetailedRewardAndPenalty();
    final DetailedRewardAndPenalty rp2 = new DetailedRewardAndPenalty();

    List.of(RewardComponent.TARGET, RewardComponent.SOURCE, RewardComponent.HEAD)
        .forEach(
            c -> {
              rp1.reward(c, UInt64.ONE);
              rp2.reward(c, UInt64.ONE);

              rp1.penalize(c, UInt64.ONE);
              rp2.penalize(c, UInt64.ONE);
            });

    assertThat(rp1).isEqualTo(rp2);
  }
}

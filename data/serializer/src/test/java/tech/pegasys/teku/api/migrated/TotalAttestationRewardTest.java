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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AggregatedRewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.DetailedRewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;

class TotalAttestationRewardTest {

  @Test
  public void failsToCreateWithAggregatedRewardsAndPenalty() {
    final AggregatedRewardAndPenalty rewardAndPenalty = new AggregatedRewardAndPenalty();
    assertThatThrownBy(() -> new TotalAttestationReward(1, rewardAndPenalty))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void successfullyReturnRewardComponents() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();

    rewardAndPenalty.reward(RewardComponent.HEAD, UInt64.valueOf(10));
    rewardAndPenalty.reward(RewardComponent.SOURCE, UInt64.valueOf(10));
    rewardAndPenalty.reward(RewardComponent.TARGET, UInt64.valueOf(10));
    rewardAndPenalty.reward(RewardComponent.INACTIVITY, UInt64.valueOf(10));

    rewardAndPenalty.penalize(RewardComponent.HEAD, UInt64.valueOf(5));
    rewardAndPenalty.penalize(RewardComponent.SOURCE, UInt64.valueOf(4));
    rewardAndPenalty.penalize(RewardComponent.TARGET, UInt64.valueOf(3));
    rewardAndPenalty.penalize(RewardComponent.INACTIVITY, UInt64.valueOf(2));

    final TotalAttestationReward totalAttestationReward =
        new TotalAttestationReward(1L, rewardAndPenalty);

    // Assert that we are correctly calculating reward - penalty for each component
    assertThat(totalAttestationReward.getHead()).isEqualTo(10L - 5L);
    assertThat(totalAttestationReward.getSource()).isEqualTo(10L - 4L);
    assertThat(totalAttestationReward.getTarget()).isEqualTo(10L - 3L);
    assertThat(totalAttestationReward.getInactivity()).isEqualTo(10L - 2L);
  }

  @Test
  public void supportNegativeHeadSourceAndTargetValues() {
    final DetailedRewardAndPenalty rewardAndPenalty = new DetailedRewardAndPenalty();
    rewardAndPenalty.reward(RewardComponent.HEAD, UInt64.valueOf(5));
    rewardAndPenalty.penalize(RewardComponent.HEAD, UInt64.valueOf(10));

    final TotalAttestationReward totalAttestationReward =
        new TotalAttestationReward(1L, rewardAndPenalty);

    assertThat(totalAttestationReward.getHead()).isEqualTo(5L - 10L);
  }
}

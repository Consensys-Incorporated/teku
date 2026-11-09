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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface RewardAndPenalty {

  enum RewardComponent {
    HEAD,
    SOURCE,
    TARGET,
    INCLUSION_DELAY,
    INACTIVITY
  }

  void reward(RewardComponent component, UInt64 amount);

  void penalize(RewardComponent component, UInt64 amount);

  UInt64 getReward();

  UInt64 getPenalty();

  default boolean isZero() {
    return getReward().isZero() && getPenalty().isZero();
  }

  default Optional<DetailedRewardAndPenalty> asDetailed() {
    if (this instanceof DetailedRewardAndPenalty detailedRewardAndPenalty) {
      return Optional.of(detailedRewardAndPenalty);
    } else {
      return Optional.empty();
    }
  }
}

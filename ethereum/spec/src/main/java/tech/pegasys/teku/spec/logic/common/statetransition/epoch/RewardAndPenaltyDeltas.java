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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RewardAndPenaltyDeltas {
  private final List<RewardAndPenalty> deltas;

  public static RewardAndPenaltyDeltas detailed(final int validatorCount) {
    return new RewardAndPenaltyDeltas(validatorCount, DetailedRewardAndPenalty::new);
  }

  public static RewardAndPenaltyDeltas aggregated(final int validatorCount) {
    return new RewardAndPenaltyDeltas(validatorCount, AggregatedRewardAndPenalty::new);
  }

  private RewardAndPenaltyDeltas(
      final int validatorCount, final Supplier<RewardAndPenalty> rewardAndPenaltySupplier) {
    this.deltas = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      deltas.add(rewardAndPenaltySupplier.get());
    }
  }

  public RewardAndPenalty getDelta(final int validatorIndex) {
    return deltas.get(validatorIndex);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("deltas", deltas).toString();
  }
}

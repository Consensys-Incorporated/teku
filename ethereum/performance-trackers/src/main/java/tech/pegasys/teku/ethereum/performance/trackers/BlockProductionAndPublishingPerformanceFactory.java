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

package tech.pegasys.teku.ethereum.performance.trackers;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionAndPublishingPerformanceFactory {

  private final TimeProvider timeProvider;
  private final Function<UInt64, UInt64> slotTimeCalculator;
  private final boolean enabled;
  private final Map<Flow, Integer> lateProductionEventThresholds;
  private final Map<Flow, Integer> latePublishingEventThresholds;
  private final Optional<BlockProductionMetrics> blockProductionMetrics;

  public BlockProductionAndPublishingPerformanceFactory(
      final TimeProvider timeProvider,
      final Function<UInt64, UInt64> slotTimeCalculator,
      final boolean enabled,
      final int lateProductionEventLocalThreshold,
      final int lateProductionEventBuilderThreshold,
      final int latePublishingEventLocalThreshold,
      final int latePublishingEvenBuilderThreshold,
      final Optional<BlockProductionMetrics> blockProductionMetrics) {
    this.timeProvider = timeProvider;
    this.slotTimeCalculator = slotTimeCalculator;
    this.enabled = enabled;
    this.blockProductionMetrics = blockProductionMetrics;
    this.lateProductionEventThresholds =
        Map.of(
            Flow.LOCAL,
            lateProductionEventLocalThreshold,
            Flow.BUILDER,
            lateProductionEventBuilderThreshold);
    this.latePublishingEventThresholds =
        Map.of(
            Flow.LOCAL,
            latePublishingEventLocalThreshold,
            Flow.BUILDER,
            latePublishingEvenBuilderThreshold);
  }

  public BlockProductionPerformance createForProduction(final UInt64 slot) {
    if (enabled) {
      return new BlockProductionPerformanceImpl(
          timeProvider,
          slot,
          slotTimeCalculator.apply(slot),
          lateProductionEventThresholds,
          blockProductionMetrics.orElse(BlockProductionMetrics.NOOP));
    } else {
      return BlockProductionPerformance.NOOP;
    }
  }

  public BlockPublishingPerformance createForPublishing(final UInt64 slot) {
    if (enabled) {
      return new BlockPublishingPerformanceImpl(
          timeProvider, slot, slotTimeCalculator.apply(slot), latePublishingEventThresholds);
    } else {
      return BlockPublishingPerformance.NOOP;
    }
  }
}

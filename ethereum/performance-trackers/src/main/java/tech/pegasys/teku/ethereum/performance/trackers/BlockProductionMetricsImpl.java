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

import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionMetricsImpl implements BlockProductionMetrics {
  private final MetricsCountersByIntervals metricsCountersByIntervals;
  private final SettableLabelledGauge latestDelayGauge;

  public BlockProductionMetricsImpl(
      final MetricsCountersByIntervals metricsCountersByIntervals,
      final SettableLabelledGauge latestDelayGauge) {
    this.metricsCountersByIntervals = metricsCountersByIntervals;
    this.latestDelayGauge = latestDelayGauge;
  }

  @Override
  public void recordValue(final UInt64 value, final String stage) {
    metricsCountersByIntervals.recordValue(value, stage);
    latestDelayGauge.set(value.doubleValue(), stage);
  }
}

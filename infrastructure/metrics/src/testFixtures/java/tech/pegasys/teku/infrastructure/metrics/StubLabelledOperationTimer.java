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

package tech.pegasys.teku.infrastructure.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import org.assertj.core.util.VisibleForTesting;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

public class StubLabelledOperationTimer extends StubMetric
    implements LabelledMetric<OperationTimer> {

  private final Map<List<String>, StubOperationTimer> timers = new HashMap<>();
  private final Supplier<Long> timeProvider;

  protected StubLabelledOperationTimer(
      final MetricCategory category, final String name, final String help) {
    super(category, name, help);
    this.timeProvider = System::currentTimeMillis;
  }

  @VisibleForTesting
  StubLabelledOperationTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Long> timeProvider) {
    super(category, name, help);
    this.timeProvider = timeProvider;
  }

  @Override
  public OperationTimer labels(final String... labels) {
    return timers.computeIfAbsent(
        List.of(labels),
        __ -> new StubOperationTimer(getCategory(), getName(), getHelp(), timeProvider));
  }

  /**
   * Return the average time of operations marked by the timer.
   *
   * @param labels the labels matching the timer
   * @return the average time of operations marked by the timer.
   * @throws IllegalArgumentException if the provided labels do not correspond to an existing timer.
   */
  public OptionalDouble getAverageDuration(final String... labels) {
    final StubOperationTimer operationTimer = timers.get(List.of(labels));
    if (operationTimer == null) {
      throw new IllegalArgumentException("Attempting to get time from a non-existing timer");
    }
    return operationTimer.getAverageDuration();
  }

  public Set<Long> getDurations(final String... labels) {
    final StubOperationTimer operationTimer = timers.get(List.of(labels));
    if (operationTimer == null) {
      throw new IllegalArgumentException("Attempting to get time from a non-existing timer");
    }
    return operationTimer.getDurations();
  }
}

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

import java.util.HashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

public class StubOperationTimer extends StubMetric implements OperationTimer {

  private final Random idGenerator = new Random();
  private final Supplier<Long> timeProvider;
  private final Map<Long, Long> durations = new ConcurrentHashMap<>();

  StubOperationTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Long> timeProvider) {
    super(category, name, help);
    this.timeProvider = timeProvider;
  }

  @Override
  public TimingContext startTimer() {
    return new StubTimingContext(timeProvider.get());
  }

  public OptionalDouble getAverageDuration() {
    return durations.values().stream().mapToLong(v -> v).average();
  }

  public Set<Long> getDurations() {
    return new HashSet<>(durations.values());
  }

  public OptionalLong getDurationsByTimingContextId(final Long id) {
    final Long maybeDuration = durations.get(id);
    return maybeDuration == null ? OptionalLong.empty() : OptionalLong.of(maybeDuration);
  }

  private class StubTimingContext implements TimingContext {

    private final long id = idGenerator.nextLong();
    private final long startTime;

    public StubTimingContext(final long startTime) {
      this.startTime = startTime;
    }

    @Override
    public double stopTimer() {
      final long duration = timeProvider.get() - startTime;
      durations.put(id, duration);
      return duration;
    }
  }
}

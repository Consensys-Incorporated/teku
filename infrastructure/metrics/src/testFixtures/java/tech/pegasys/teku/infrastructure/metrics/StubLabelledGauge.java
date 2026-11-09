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

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class StubLabelledGauge extends StubMetric implements LabelledSuppliedMetric {

  private final Map<List<String>, DoubleSupplier> suppliers = new ConcurrentHashMap<>();

  protected StubLabelledGauge(final MetricCategory category, final String name, final String help) {
    super(category, name, help);
  }

  @Override
  public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    final DoubleSupplier oldValue = suppliers.putIfAbsent(List.of(labelValues), valueSupplier);
    if (oldValue != null) {
      throw new IllegalArgumentException(
          "Attempting to create two gauges with the same name and labels");
    }
  }

  public OptionalDouble getValue(final String... labels) {
    final DoubleSupplier doubleSupplier = suppliers.get(List.of(labels));
    return doubleSupplier != null
        ? OptionalDouble.of(doubleSupplier.getAsDouble())
        : OptionalDouble.empty();
  }
}

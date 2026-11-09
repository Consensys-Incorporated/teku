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

import com.google.common.util.concurrent.AtomicDouble;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class SettableLabelledGauge {

  private final Map<List<String>, AtomicDouble> valueHolders = new ConcurrentHashMap<>();
  private final LabelledSuppliedMetric labelledGauge;

  private SettableLabelledGauge(final LabelledSuppliedMetric labelledGauge) {
    this.labelledGauge = labelledGauge;
  }

  public static SettableLabelledGauge create(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      final String name,
      final String help,
      final String... labels) {
    final LabelledSuppliedMetric labelledGauge =
        metricsSystem.createLabelledSuppliedGauge(category, name, help, labels);
    return new SettableLabelledGauge(labelledGauge);
  }

  public void set(final double value, final String... labels) {
    final AtomicDouble valueHolder =
        valueHolders.computeIfAbsent(
            List.of(labels),
            __ -> {
              final AtomicDouble holder = new AtomicDouble();
              labelledGauge.labels(holder::get, labels);
              return holder;
            });

    valueHolder.set(value);
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsQuantileHistogramTest {

  private static final TekuMetricCategory CATEGORY = TekuMetricCategory.BEACON;
  private ObservableMetricsSystem metricsSystem;

  @BeforeEach
  void setup() {
    metricsSystem = new PrometheusMetricsSystem(Set.of(CATEGORY), true);
  }

  @AfterEach
  void shutdown() {
    metricsSystem.shutdown();
  }

  @Test
  void shouldReportValuesWithNoSpecifiedUpperLimit() {
    final MetricsQuantileHistogram histogram =
        MetricsQuantileHistogram.create(CATEGORY, metricsSystem, "test", "Test help", 3, List.of());

    for (int i = 1; i <= 100; i++) {
      histogram.recordValue(i);
    }
    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations()
            .filter(ob -> ob.category() == CATEGORY)
            .collect(Collectors.toMap(Observation::labels, Observation::value));
    assertThat(values)
        .contains(
            entry(key(List.of("0.5")), 50d),
            entry(key(List.of("0.95")), 95d),
            entry(key(List.of("0.99")), 99d),
            entry(key(List.of("1.0")), 100d));
  }

  @Test
  void shouldReportValuesWithUpperLimit() {
    final MetricsQuantileHistogram histogram =
        MetricsQuantileHistogram.create(
            CATEGORY, metricsSystem, "test", "Test help", 2, 80, List.of());

    for (int i = 1; i <= 100; i++) {
      histogram.recordValue(i);
    }
    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations()
            .filter(ob -> ob.category() == CATEGORY)
            .collect(Collectors.toMap(Observation::labels, Observation::value));
    assertThat(values)
        .contains(
            entry(key(List.of("0.5")), 50d),
            entry(key(List.of("0.95")), 80d),
            entry(key(List.of("0.99")), 80d),
            entry(key(List.of("1.0")), 80d));
  }

  private static List<String> key(final List<String> labelValues) {
    final List<String> key = new ArrayList<>();
    key.add(MetricsQuantileHistogram.QUANTILE_LABEL);
    key.addAll(labelValues);
    return key;
  }
}

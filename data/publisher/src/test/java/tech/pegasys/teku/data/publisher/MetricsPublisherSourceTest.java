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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class MetricsPublisherSourceTest {
  private final PrometheusMetricsSystem metricsSystem = mock(PrometheusMetricsSystem.class);

  @Test
  void shouldReadCpuSeconds() {
    when(metricsSystem.streamObservations()).thenReturn(getObservations().stream());
    final MetricsPublisherSource source = new PrometheusMetricsPublisherSource(metricsSystem);
    assertThat(source.getCpuSecondsTotal()).isEqualTo(11);
    assertThat(source.getMemoryProcessBytes()).isEqualTo(55L);
  }

  @Test
  void shouldReadMemoryBytes() {
    when(metricsSystem.streamObservations()).thenReturn(getObservations().stream());
    final MetricsPublisherSource source = new PrometheusMetricsPublisherSource(metricsSystem);
    assertThat(source.getMemoryProcessBytes()).isEqualTo(55L);
  }

  @Test
  void shouldReadValidatorCounts() {
    when(metricsSystem.streamObservations()).thenReturn(getObservations().stream());
    final MetricsPublisherSource source = new PrometheusMetricsPublisherSource(metricsSystem);
    assertThat(source.getValidatorsActive()).isEqualTo(44L);
    assertThat(source.getValidatorsTotal()).isEqualTo(110L);
  }

  private List<Observation> getObservations() {
    return List.of(
        new Observation(StandardMetricCategory.PROCESS, "cpu_seconds_total", 11.0, null),
        new Observation(
            StandardMetricCategory.JVM, "memory_pool_bytes_used", 22.0, List.of("heap")),
        new Observation(
            StandardMetricCategory.JVM, "memory_pool_bytes_used", 33.0, List.of("nonheap")),
        new Observation(
            TekuMetricCategory.VALIDATOR,
            "local_validator_counts",
            44.0,
            List.of("active_ongoing")),
        new Observation(
            TekuMetricCategory.VALIDATOR,
            "local_validator_counts",
            66.0,
            List.of("pending_queued")));
  }
}

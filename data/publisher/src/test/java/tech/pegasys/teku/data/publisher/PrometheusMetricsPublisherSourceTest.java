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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class PrometheusMetricsPublisherSourceTest {

  private PrometheusMetricsSystem prometheusMetricsSystem;
  private PrometheusMetricsPublisherSource prometheusMetricsPublisherSource;

  @BeforeEach
  public void setUp() {
    prometheusMetricsSystem = mock(PrometheusMetricsSystem.class);
  }

  @Test
  public void shouldSumInboundAndOutboundPeers() {
    final Observation observation1 =
        new Observation(TekuMetricCategory.BEACON, "peer_count", 1, List.of("inbound"));
    final Observation observation2 =
        new Observation(TekuMetricCategory.BEACON, "peer_count", 1, List.of("outbound"));
    when(prometheusMetricsSystem.streamObservations())
        .thenReturn(Stream.of(observation1, observation2));
    prometheusMetricsPublisherSource =
        new PrometheusMetricsPublisherSource(prometheusMetricsSystem);

    assertThat(prometheusMetricsPublisherSource.getPeerCount()).isEqualTo(2);
  }

  @Test
  public void peerCoundMetricWithoutDirectionLabelShouldBeIgnored() {
    // We should never have a unlabeled peer_count metric, this is here just for sanity
    final Observation observation =
        new Observation(TekuMetricCategory.BEACON, "peer_count", 1, List.of());

    when(prometheusMetricsSystem.streamObservations()).thenReturn(Stream.of(observation));
    prometheusMetricsPublisherSource =
        new PrometheusMetricsPublisherSource(prometheusMetricsSystem);

    assertThat(prometheusMetricsPublisherSource.getPeerCount()).isEqualTo(0);
  }
}

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class MetricsPublisherManagerTest {

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
  private final MetricsEndpoint metricsEndpoint = mock(MetricsEndpoint.class);
  private final MetricsConfig metricsConfig = mock(MetricsConfig.class);
  private final MetricsPublisher metricsPublisher = mock(MetricsPublisher.class);
  private final PrometheusMetricsSystem prometheusMetricsSystem =
      mock(PrometheusMetricsSystem.class);

  private MetricsPublisherManager publisherManager;

  @BeforeEach
  void setup() throws IOException {
    when(metricsConfig.getPublicationInterval()).thenReturn(1);
    when(metricsEndpoint.getMetricsSystem()).thenReturn(prometheusMetricsSystem);
    when(metricsEndpoint.getMetricConfig()).thenReturn(metricsConfig);
    when(metricsConfig.getMetricsEndpoint())
        .thenReturn(Optional.of(URI.create("http://host.com/").toURL()));
  }

  @Test
  public void shouldRunPublisherEveryXSeconds(@TempDir final Path tempDir) throws IOException {
    when(prometheusMetricsSystem.streamObservations())
        .thenReturn(metricsStream())
        .thenReturn(metricsStream());
    publisherManager =
        new MetricsPublisherManager(
            asyncRunnerFactory, timeProvider, metricsEndpoint, metricsPublisher, tempDir.toFile());
    assertThat(asyncRunnerFactory.getStubAsyncRunners().size()).isEqualTo(0);
    verify(metricsPublisher, times(0)).publishMetrics(anyString());

    assertThat(publisherManager.doStart()).isEqualTo(SafeFuture.COMPLETE);
    assertThat(asyncRunnerFactory.getStubAsyncRunners().size()).isEqualTo(1);

    asyncRunnerFactory.getStubAsyncRunners().get(0).executeQueuedActions();
    verify(metricsPublisher, times(1)).publishMetrics(anyString());

    asyncRunnerFactory.getStubAsyncRunners().get(0).executeQueuedActions();
    verify(metricsPublisher, times(2)).publishMetrics(anyString());
  }

  @Test
  public void shouldStopGracefully(@TempDir final Path tempDir) throws IOException {
    publisherManager =
        new MetricsPublisherManager(
            asyncRunnerFactory, timeProvider, metricsEndpoint, metricsPublisher, tempDir.toFile());
    assertThat(publisherManager.doStart()).isEqualTo(SafeFuture.COMPLETE);
    assertThat(publisherManager.doStop()).isEqualTo(SafeFuture.COMPLETE);
    asyncRunnerFactory.getStubAsyncRunners().get(0).executeQueuedActions();
    verify(metricsPublisher, never()).publishMetrics(anyString());
  }

  private Stream<Observation> metricsStream() {
    return Stream.of(
        new Observation(
            TekuMetricCategory.VALIDATOR,
            "local_validator_counts",
            44.0,
            List.of("active_ongoing")));
  }
}

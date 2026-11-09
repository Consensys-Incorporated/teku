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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;

public class MetricsPublisherManager extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final long intervalBetweenPublications;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final MetricsDataFactory metricsDataFactory;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Optional<HttpUrl> metricsUrl;

  private final MetricsPublisher metricsPublisher;
  private volatile Cancellable publisherTask;

  MetricsPublisherManager(
      final AsyncRunnerFactory asyncRunnerFactory,
      final TimeProvider timeProvider,
      final MetricsEndpoint metricsEndpoint,
      final MetricsPublisher metricsPublisher,
      final File beaconNodeDataDirectory) {
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.metricsUrl = metricsEndpoint.getMetricConfig().getMetricsEndpoint().map(HttpUrl::get);
    this.metricsDataFactory =
        new MetricsDataFactory(
            metricsEndpoint.getMetricsSystem(), timeProvider, beaconNodeDataDirectory);
    this.intervalBetweenPublications = metricsEndpoint.getMetricConfig().getPublicationInterval();
    this.metricsPublisher = metricsPublisher;
  }

  public MetricsPublisherManager(
      final AsyncRunnerFactory asyncRunnerFactory,
      final TimeProvider timeProvider,
      final MetricsEndpoint metricsEndpoint,
      final File beaconNodeDataDirectory) {
    this(
        asyncRunnerFactory,
        timeProvider,
        metricsEndpoint,
        new MetricsPublisher(
            new OkHttpClient(),
            metricsEndpoint.getMetricConfig().getMetricsEndpoint().map(HttpUrl::get).orElse(null)),
        beaconNodeDataDirectory);
  }

  @Override
  public SafeFuture<?> start() {
    if (metricsUrl.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    return doStart();
  }

  private void publishMetrics() throws IOException {
    List<BaseMetricData> clientData = metricsDataFactory.getMetricData();
    if (!clientData.isEmpty()) {
      metricsPublisher.publishMetrics(objectMapper.writeValueAsString(clientData));
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (metricsUrl.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    AsyncRunner asyncRunner = asyncRunnerFactory.create("MetricPublisher", 1);
    publisherTask =
        asyncRunner.runWithFixedDelay(
            this::publishMetrics,
            Duration.ofSeconds(intervalBetweenPublications),
            (err) -> {
              if (Throwables.getRootCause(err) instanceof IOException
                  && !(Throwables.getRootCause(err) instanceof JsonProcessingException)) {
                LOG.warn("Error publishing metrics to remote service: " + err.getMessage());
              } else {
                LOG.warn("Error publishing metrics to remote service", err);
              }
            });
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    if (publisherTask != null) {
      publisherTask.cancel();
      publisherTask = null;
    }
    return SafeFuture.COMPLETE;
  }
}

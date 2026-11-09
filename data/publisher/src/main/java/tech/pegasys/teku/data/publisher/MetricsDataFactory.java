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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class MetricsDataFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final MetricsSystem metricsSystem;
  private final TimeProvider timeProvider;
  private final File beaconNodeDataDirectory;

  public MetricsDataFactory(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final File beaconNodeDataDirectory) {
    this.metricsSystem = metricsSystem;
    this.timeProvider = timeProvider;
    this.beaconNodeDataDirectory = beaconNodeDataDirectory;
  }

  List<BaseMetricData> getMetricData(final MetricsPublisherSource source) {
    final List<BaseMetricData> metricList = new ArrayList<>();
    final long timeMillis = timeProvider.getTimeInMillis().longValue();
    if (source.isValidatorPresent()) {
      metricList.add(new ValidatorMetricData(timeMillis, source));
    }
    if (source.isBeaconNodePresent()) {
      metricList.add(new BeaconNodeMetricData(timeMillis, source));
    }
    metricList.add(new SystemMetricData(timeMillis, beaconNodeDataDirectory));

    return metricList;
  }

  public List<BaseMetricData> getMetricData() {
    if (metricsSystem instanceof final PrometheusMetricsSystem prometheusMetricsSystem) {
      return getMetricData(new PrometheusMetricsPublisherSource(prometheusMetricsSystem));
    } else {
      LOG.error("Prometheus metric system not found, cannot export metrics data.");
      return new ArrayList<>();
    }
  }
}

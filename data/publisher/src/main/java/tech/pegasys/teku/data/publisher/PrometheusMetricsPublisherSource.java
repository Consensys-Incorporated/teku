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

import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;
import static org.hyperledger.besu.metrics.StandardMetricCategory.PROCESS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR;

import java.util.List;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class PrometheusMetricsPublisherSource implements MetricsPublisherSource {
  private long cpuSecondsTotal;
  private long memoryProcessBytes;
  private long headSlot;
  private int validatorsTotal;
  private int validatorsActive;
  private int inboundPeerCount;
  private int outboundPeerCount;
  private boolean isBeaconNodePresent;
  private boolean isEth2Synced;
  private boolean isEth1Connected;

  public PrometheusMetricsPublisherSource(final PrometheusMetricsSystem metricsSystem) {
    metricsSystem.streamObservations().forEach(this::storeObservationIfNeeded);
  }

  @Override
  public long getCpuSecondsTotal() {
    return cpuSecondsTotal;
  }

  @Override
  public long getMemoryProcessBytes() {
    return memoryProcessBytes;
  }

  @Override
  public int getValidatorsTotal() {
    return validatorsTotal;
  }

  @Override
  public int getValidatorsActive() {
    return validatorsActive;
  }

  @Override
  public long getHeadSlot() {
    return headSlot;
  }

  @Override
  public boolean isValidatorPresent() {
    return validatorsTotal > 0;
  }

  @Override
  public boolean isBeaconNodePresent() {
    return isBeaconNodePresent;
  }

  @Override
  public boolean isEth2Synced() {
    return isEth2Synced;
  }

  @Override
  public boolean isEth1Connected() {
    return isEth1Connected;
  }

  @Override
  public int getPeerCount() {
    return inboundPeerCount + outboundPeerCount;
  }

  @Override
  public long getGossipBytesTotalSent() {
    return 0L;
  }

  @Override
  public long getGossipBytesTotalReceived() {
    return 0L;
  }

  private void storeObservationIfNeeded(final Observation observation) {
    MetricCategory category = observation.category();
    if (category.equals(PROCESS)) {
      readProcessCategoryItem(observation);
    } else if (category.equals(VALIDATOR)) {
      readValidatorCategoryItem(observation);
    } else if (category.equals(JVM)) {
      readJvmCategoryItem(observation);
    } else if (category.equals(BEACON)) {
      readBeaconCategoryItem(observation);
    }
  }

  private void readBeaconCategoryItem(final Observation observation) {
    isBeaconNodePresent = true;
    switch (observation.metricName()) {
      case "head_slot" -> headSlot = getLongValue(observation.value());
      case "eth1_request_queue_size" -> isEth1Connected = true;
      case "peer_count" -> {
        if (observation.labels().contains("inbound")) {
          inboundPeerCount = getIntValue(observation.value());
        } else if (observation.labels().contains("outbound")) {
          outboundPeerCount = getIntValue(observation.value());
        }
      }
      case "node_syncing_active" -> isEth2Synced = getIntValue(observation.value()) == 0;
    }
  }

  private void readProcessCategoryItem(final Observation observation) {
    if ("cpu_seconds_total".equals(observation.metricName())) {
      cpuSecondsTotal = getLongValue(observation.value());
    }
  }

  private void readJvmCategoryItem(final Observation observation) {
    if ("memory_pool_bytes_used".equals(observation.metricName())) {
      addToMemoryPoolBytesUsed(observation.value());
    }
  }

  private void readValidatorCategoryItem(final Observation observation) {
    if ("local_validator_counts".equals(observation.metricName())) {
      addToLocalValidators(observation.labels(), observation.value());
    }
  }

  private void addToLocalValidators(final List<String> labels, final Object value) {
    if (value instanceof Number number) {
      if (labels.contains("active_ongoing")) {
        validatorsActive = number.intValue();
      }
      validatorsTotal += number.intValue();
    }
  }

  private void addToMemoryPoolBytesUsed(final Object observedValue) {
    if (observedValue instanceof Number number) {
      memoryProcessBytes += number.longValue();
    }
  }

  private long getLongValue(final Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new IllegalArgumentException("Unexpected value type: " + value.getClass());
  }

  private int getIntValue(final Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw new IllegalArgumentException("Unexpected value type: " + value.getClass());
  }
}

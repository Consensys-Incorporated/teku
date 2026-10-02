/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.remote.eventsource;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

/**
 * Counters shared by every event stream. A handler is created per beacon node, so the metrics have
 * to be registered once up front rather than by each handler.
 */
record EventStreamMetrics(
    Counter invalidEventCounter,
    Counter disconnectCounter,
    Counter timeoutCounter,
    Counter errorCounter) {

  static EventStreamMetrics create(final MetricsSystem metricsSystem) {
    final Counter invalidEventCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            "event_stream_invalid_events_total",
            "Event stream Invalid Events");
    final LabelledMetric<Counter> eventSourceMetrics =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            "event_stream_disconnections_total",
            "Event stream disconnect status counters",
            "reason");
    return new EventStreamMetrics(
        invalidEventCounter,
        eventSourceMetrics.labels("disconnect"),
        eventSourceMetrics.labels("timeout"),
        eventSourceMetrics.labels("error"));
  }
}

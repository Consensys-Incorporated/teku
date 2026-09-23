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

package tech.pegasys.teku.ethereum.performance.trackers;

import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance.COMPLETE_LABEL;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.BEACON_BLOCK_BODY_PREPARATION_STARTED;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.BEACON_BLOCK_BODY_PREPARED;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.BEACON_BLOCK_CREATED;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.BUILDER_BID_VALIDATED;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.BUILDER_GET_HEADER;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.GET_ATTESTATIONS_FOR_BLOCK;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.GET_PAYLOAD_ATTESTATIONS_FOR_BLOCK;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.LOCAL_GET_PAYLOAD;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.PREPARATION_APPLY_DEFERRED_ATTESTATIONS;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.PREPARATION_ON_TICK;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.PREPARATION_PROCESS_HEAD;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.RETRIEVE_STATE;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.STATE_HASHING;
import static tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformanceImpl.STATE_TRANSITION;

import java.util.List;
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface BlockProductionMetrics {

  BlockProductionMetrics NOOP =
      new BlockProductionMetrics() {
        @Override
        public void recordValue(UInt64 value, String stage) {
          // Do nothing
        }
      };

  static BlockProductionMetricsImpl create(final MetricsSystem metricsSystem) {

    final Map<List<String>, List<Long>> eventsAndBoundaries =
        Map.of(
            List.of(COMPLETE_LABEL),
            List.of(50L, 100L, 250L, 500L, 1000L, 2000L, 3000L, 4000L),
            List.of(), // default
            List.of(50L, 100L, 250L, 500L, 1000L, 2000L, 3000L, 4000L, 5000L, 8000L, 12000L));

    final MetricsCountersByIntervals metricsCountersByIntervals =
        MetricsCountersByIntervals.create(
            TekuMetricCategory.VALIDATOR,
            metricsSystem,
            "block_production_delay_counter_total",
            "Counter of blocks production in different time frames in each import stages",
            List.of("stage"),
            eventsAndBoundaries);

    List<String> blockProductionStages =
        List.of(
            PREPARATION_ON_TICK,
            PREPARATION_APPLY_DEFERRED_ATTESTATIONS,
            PREPARATION_PROCESS_HEAD,
            RETRIEVE_STATE,
            BEACON_BLOCK_BODY_PREPARATION_STARTED,
            GET_ATTESTATIONS_FOR_BLOCK,
            GET_PAYLOAD_ATTESTATIONS_FOR_BLOCK,
            BEACON_BLOCK_BODY_PREPARED,
            LOCAL_GET_PAYLOAD,
            BUILDER_GET_HEADER,
            BUILDER_BID_VALIDATED,
            BEACON_BLOCK_CREATED,
            STATE_TRANSITION,
            STATE_HASHING,
            COMPLETE_LABEL);

    blockProductionStages.forEach(stage -> metricsCountersByIntervals.initCounters(List.of(stage)));

    final SettableLabelledGauge latestDelayGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "block_production_delay_latest",
            "Milliseconds delay at each stage of block production for the last imported block",
            "stage");
    return new BlockProductionMetricsImpl(metricsCountersByIntervals, latestDelayGauge);
  }

  void recordValue(UInt64 value, String stage);
}

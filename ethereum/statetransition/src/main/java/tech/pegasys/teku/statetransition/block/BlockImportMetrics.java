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

package tech.pegasys.teku.statetransition.block;

import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.ARRIVAL_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.COMPLETED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.GOSSIP_VALIDATION_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PRESTATE_RETRIEVED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PROCESSED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.SUCCESS_RESULT_METRIC_LABEL_VALUE;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TOTAL_PROCESSING_TIME_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_COMMITTED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_PREPARED_EVENT_LABEL;

import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;

public class BlockImportMetrics {
  private final MetricsCountersByIntervals metricsCountersByIntervals;
  private final SettableLabelledGauge latestDelayGauge;

  public BlockImportMetrics(
      final MetricsCountersByIntervals metricsCountersByIntervals,
      final SettableLabelledGauge latestDelayGauge) {
    this.metricsCountersByIntervals = metricsCountersByIntervals;
    this.latestDelayGauge = latestDelayGauge;
  }

  public static BlockImportMetrics create(final MetricsSystem metricsSystem) {

    final Map<List<String>, List<Long>> eventsAndBoundaries =
        Map.of(
            List.of(
                TOTAL_PROCESSING_TIME_LABEL,
                FailureReason.UNKNOWN_PARENT.name().toLowerCase(Locale.ROOT)),
            List.of(50L, 100L, 250L, 500L, 1000L, 2000L),
            List.of(ARRIVAL_EVENT_LABEL),
            List.of(500L, 1000L, 1500L, 2000L, 3000L, 4000L, 5000L, 8000L, 12000L),
            List.of(TOTAL_PROCESSING_TIME_LABEL),
            List.of(500L, 1000L, 1500L, 2000L, 3000L, 4000L, 5000L, 8000L, 12000L),
            List.of(), // default
            List.of(50L, 100L, 250L, 500L, 1000L, 2000L));

    final MetricsCountersByIntervals metricsCountersByIntervals =
        MetricsCountersByIntervals.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_delay_counter_total",
            "Counter of blocks falling in different time frames in each import stages",
            List.of("stage", "result"),
            eventsAndBoundaries);

    // init all combinations counters
    Streams.concat(
            Arrays.stream(FailureReason.values()).map(FailureReason::name).map(String::toLowerCase),
            Stream.of(SUCCESS_RESULT_METRIC_LABEL_VALUE))
        .forEach(
            result ->
                List.of(
                        ARRIVAL_EVENT_LABEL,
                        GOSSIP_VALIDATION_EVENT_LABEL,
                        PRESTATE_RETRIEVED_EVENT_LABEL,
                        PROCESSED_EVENT_LABEL,
                        TRANSACTION_PREPARED_EVENT_LABEL,
                        TRANSACTION_COMMITTED_EVENT_LABEL,
                        EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL,
                        COMPLETED_EVENT_LABEL,
                        TOTAL_PROCESSING_TIME_LABEL)
                    .forEach(
                        stage -> metricsCountersByIntervals.initCounters(List.of(stage, result))));

    final SettableLabelledGauge latestDelayGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "block_import_delay_latest",
            "Milliseconds delay at each stage of block import for the last imported block",
            "stage");
    return new BlockImportMetrics(metricsCountersByIntervals, latestDelayGauge);
  }

  public void recordValue(final UInt64 value, final String stage, final String result) {
    metricsCountersByIntervals.recordValue(value, stage, result);
    latestDelayGauge.set(value.doubleValue(), stage);
  }
}

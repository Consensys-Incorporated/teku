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

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.Locale;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockImportPerformance {
  private static final Logger LOG = LogManager.getLogger();

  public static final String ARRIVAL_EVENT_LABEL = "arrival";
  public static final String GOSSIP_VALIDATION_EVENT_LABEL = "gossip_validation";
  public static final String PRESTATE_RETRIEVED_EVENT_LABEL = "pre-state_retrieved";
  public static final String PROCESSED_EVENT_LABEL = "processed";
  public static final String TRANSACTION_PREPARED_EVENT_LABEL = "transaction_prepared";
  public static final String TRANSACTION_COMMITTED_EVENT_LABEL = "transaction_committed";
  public static final String COMPLETED_EVENT_LABEL = "completed";

  public static final String TOTAL_PROCESSING_TIME_LABEL = "total_processing_time";

  public static final String SUCCESS_RESULT_METRIC_LABEL_VALUE = "success";

  public static final String EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL =
      "execution_payload_result_received";

  public static final String DATA_AVAILABILITY_CHECKED_LABEL = "data_availability_checked";

  public static final String BEGIN_IMPORTING_LABEL = "begin_importing";

  private final BlockImportMetrics blockImportMetrics;
  private final PerformanceTracker performanceTracker;

  private UInt64 timeWarningLimitTimeStamp;
  private UInt64 timeAtSlotStartTimeStamp;

  public BlockImportPerformance(
      final TimeProvider timeProvider, final BlockImportMetrics blockImportMetrics) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.blockImportMetrics = blockImportMetrics;
  }

  public void arrival(
      final RecentChainData recentChainData,
      final UInt64 slot,
      final Optional<UInt64> arrivalTimestamp) {
    timeAtSlotStartTimeStamp = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
    timeWarningLimitTimeStamp =
        timeAtSlotStartTimeStamp.plus(recentChainData.getSpec().getAttestationDueMillis(slot));
    if (arrivalTimestamp.isPresent()) {
      performanceTracker.addEvent(ARRIVAL_EVENT_LABEL, arrivalTimestamp.get());
    } else {
      LOG.trace("Block arrival time missed for slot {}, setting current", slot);
      performanceTracker.addEvent(ARRIVAL_EVENT_LABEL);
    }
  }

  public void gossipValidation() {
    performanceTracker.addEvent(GOSSIP_VALIDATION_EVENT_LABEL);
  }

  public void preStateRetrieved() {
    performanceTracker.addEvent(PRESTATE_RETRIEVED_EVENT_LABEL);
  }

  public void postStateCreated() {
    performanceTracker.addEvent(PROCESSED_EVENT_LABEL);
  }

  public void transactionReady() {
    performanceTracker.addEvent(TRANSACTION_PREPARED_EVENT_LABEL);
  }

  public void transactionCommitted() {
    performanceTracker.addEvent(TRANSACTION_COMMITTED_EVENT_LABEL);
  }

  public void executionResultReceived() {
    performanceTracker.addEvent(EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL);
  }

  public void dataAvailabilityChecked() {
    performanceTracker.addEvent(DATA_AVAILABILITY_CHECKED_LABEL);
  }

  public void beginImporting() {
    performanceTracker.addEvent(BEGIN_IMPORTING_LABEL);
  }

  public void processingComplete(
      final EventLogger eventLogger,
      final SignedBeaconBlock block,
      final BlockImportResult blockImportResult) {
    final UInt64 importCompletedTimestamp = performanceTracker.addEvent(COMPLETED_EVENT_LABEL);
    final boolean isLateEvent = importCompletedTimestamp.isGreaterThan(timeWarningLimitTimeStamp);
    final String resultMetricLabelValue =
        blockImportResult.isSuccessful()
            ? SUCCESS_RESULT_METRIC_LABEL_VALUE
            : blockImportResult.getFailureReason().name().toLowerCase(Locale.ROOT);
    performanceTracker.report(
        timeAtSlotStartTimeStamp,
        isLateEvent,
        (event, stepDuration) ->
            blockImportMetrics.recordValue(stepDuration, event.getLeft(), resultMetricLabelValue),
        totalProcessingDuration ->
            blockImportMetrics.recordValue(
                totalProcessingDuration, TOTAL_PROCESSING_TIME_LABEL, resultMetricLabelValue),
        (totalDuration, timings) ->
            eventLogger.lateBlockImport(
                block.getRoot(),
                block.getSlot(),
                block.getProposerIndex(),
                timings,
                resultMetricLabelValue));
  }
}

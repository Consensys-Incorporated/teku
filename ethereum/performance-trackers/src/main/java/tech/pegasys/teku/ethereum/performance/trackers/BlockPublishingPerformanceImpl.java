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

package tech.pegasys.teku.ethereum.performance.trackers;

import java.util.Map;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockPublishingPerformanceImpl implements BlockPublishingPerformance {

  private final PerformanceTracker performanceTracker;
  private final UInt64 slot;
  private final UInt64 slotTime;
  private final Map<Flow, Integer> lateThresholds;

  private volatile Flow flow = Flow.LOCAL;

  BlockPublishingPerformanceImpl(
      final TimeProvider timeProvider,
      final UInt64 slot,
      final UInt64 slotTime,
      final Map<Flow, Integer> lateThresholds) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.lateThresholds = lateThresholds;
    this.slot = slot;
    this.slotTime = slotTime;
    performanceTracker.addEvent("start");
  }

  @Override
  public void complete() {
    final UInt64 completionTime = performanceTracker.addEvent(COMPLETE_LABEL);
    final boolean isLateEvent =
        completionTime.minusMinZero(slotTime).isGreaterThan(lateThresholds.get(flow));
    performanceTracker.report(
        slotTime,
        isLateEvent,
        (event, stepDuration) -> {},
        totalDuration -> {},
        (totalDuration, timings) ->
            EventLogger.EVENT_LOG.slowBlockPublishingEvent(slot, totalDuration, timings));
  }

  @Override
  public void builderGetPayload() {
    performanceTracker.addEvent("builder_get_payload");
    // set the flow to BUILDER when builderGetPayload has been called
    flow = Flow.BUILDER;
  }

  @Override
  public void blobSidecarsPrepared() {
    performanceTracker.addEvent("blob_sidecars_prepared");
  }

  @Override
  public void blobSidecarsImportCompleted() {
    performanceTracker.addEvent("blob_sidecars_imported");
  }

  @Override
  public void blockPublishingInitiated() {
    performanceTracker.addEvent("block_publishing_initiated");
  }

  @Override
  public void blobSidecarsPublishingInitiated() {
    performanceTracker.addEvent("blob_sidecars_publishing_initiated");
  }

  @Override
  public void dataColumnSidecarsPublishingInitiated() {
    performanceTracker.addEvent("data_column_sidecars_publishing_initiated");
  }

  @Override
  public void blockImportCompleted() {
    performanceTracker.addEvent("block_import_completed");
  }
}

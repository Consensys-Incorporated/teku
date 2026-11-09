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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TickProcessingPerformance {

  public static final String COMPLETE_LABEL = "complete";
  public static final int LATE_EVENT_MS = 500;
  private final PerformanceTracker performanceTracker;
  private final UInt64 startTime;

  public TickProcessingPerformance(final TimeProvider timeProvider, final UInt64 startTime) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.startTime = startTime;
    performanceTracker.addEvent("start");
  }

  public void tickProcessorComplete() {
    performanceTracker.addEvent("tick_processor_complete");
  }

  public void deferredAttestationsApplied() {
    performanceTracker.addEvent("deferred_attestations_applied");
  }

  public void complete() {
    final UInt64 completionTime = performanceTracker.addEvent(COMPLETE_LABEL);
    final boolean isLateEvent = completionTime.minusMinZero(startTime).isGreaterThan(LATE_EVENT_MS);
    performanceTracker.report(
        startTime,
        isLateEvent,
        (event, stepDuration) -> {},
        totalDuration -> {},
        (totalDuration, timings) ->
            EventLogger.EVENT_LOG.slowTickEvent(startTime, totalDuration, timings));
  }

  public void startSlotComplete() {
    performanceTracker.addEvent("start_slot_complete");
  }

  public void attestationsDueComplete() {
    performanceTracker.addEvent("attestations_due_complete");
  }

  public void precomputeEpochComplete() {
    performanceTracker.addEvent("precompute_epoch_complete");
  }

  public void forkChoiceTriggerUpdated() {
    performanceTracker.addEvent("forkchoice_trigger_updated");
  }

  public void forkChoiceNotifierUpdated() {
    performanceTracker.addEvent("forkchoice_notifier_updated");
  }

  public void payloadAttestationsDueComplete() {
    performanceTracker.addEvent("payload_attestations_due_complete");
  }
}

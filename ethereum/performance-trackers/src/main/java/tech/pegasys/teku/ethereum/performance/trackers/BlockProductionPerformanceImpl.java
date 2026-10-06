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

import java.util.Map;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionPerformanceImpl implements BlockProductionPerformance {

  public static final String PREPARATION_ON_TICK = "preparation_on_tick";
  public static final String PREPARATION_APPLY_DEFERRED_ATTESTATIONS =
      "preparation_apply_deferred_attestations";
  public static final String PREPARATION_PROCESS_HEAD = "preparation_process_head";
  public static final String BEACON_BLOCK_BODY_PREPARED = "beacon_block_body_prepared";
  public static final String RETRIEVE_STATE = "retrieve_state";
  public static final String LATE_BLOCK_REORG_PREPARATION_COMPLETED =
      "late_block_reorg_preparation_completed";
  public static final String VALIDATOR_BLOCK_REQUESTED = "validator_block_requested";
  public static final String LOCAL_GET_PAYLOAD = "local_get_payload";
  public static final String BUILDER_GET_HEADER = "builder_get_header";
  public static final String BUILDER_BID_VALIDATED = "builder_bid_validated";
  public static final String BEACON_BLOCK_CREATED = "beacon_block_created";
  public static final String STATE_TRANSITION = "state_transition";
  public static final String STATE_HASHING = "state_hashing";
  public static final String GET_ATTESTATIONS_FOR_BLOCK = "get_attestations_for_block";
  public static final String GET_PAYLOAD_ATTESTATIONS_FOR_BLOCK =
      "get_payload_attestations_for_block";
  public static final String BEACON_BLOCK_BODY_PREPARATION_STARTED =
      "beacon_block_body_preparation_started";
  public static final String TOTAL_PRODUCTION_TIME_LABEL = "total_production_time";
  private final PerformanceTracker performanceTracker;
  private final UInt64 slot;
  private final UInt64 slotTime;
  private final Map<Flow, Integer> lateThresholds;
  private final BlockProductionMetrics blockProductionMetrics;
  private volatile Flow flow = Flow.LOCAL;

  BlockProductionPerformanceImpl(
      final TimeProvider timeProvider,
      final UInt64 slot,
      final UInt64 slotTime,
      final Map<Flow, Integer> lateThresholds,
      final BlockProductionMetrics blockProductionMetrics) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.lateThresholds = lateThresholds;
    this.slot = slot;
    this.slotTime = slotTime;
    this.blockProductionMetrics = blockProductionMetrics;
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
        (event, stepDuration) -> blockProductionMetrics.recordValue(stepDuration, event.getLeft()),
        totalDuration ->
            blockProductionMetrics.recordValue(totalDuration, TOTAL_PRODUCTION_TIME_LABEL),
        (totalDuration, timings) ->
            EventLogger.EVENT_LOG.slowBlockProductionEvent(slot, totalDuration, timings));
  }

  @Override
  public void prepareOnTick() {
    performanceTracker.addEvent(PREPARATION_ON_TICK);
  }

  @Override
  public void prepareApplyDeferredAttestations() {
    performanceTracker.addEvent(PREPARATION_APPLY_DEFERRED_ATTESTATIONS);
  }

  @Override
  public void prepareProcessHead() {
    performanceTracker.addEvent(PREPARATION_PROCESS_HEAD);
  }

  @Override
  public void beaconBlockBodyPrepared() {
    performanceTracker.addEvent(BEACON_BLOCK_BODY_PREPARED);
  }

  @Override
  public void lateBlockReorgPreparationCompleted() {
    performanceTracker.addEvent(LATE_BLOCK_REORG_PREPARATION_COMPLETED);
  }

  @Override
  public void validatorBlockRequested() {
    performanceTracker.addEvent(VALIDATOR_BLOCK_REQUESTED);
  }

  @Override
  public void getState() {
    performanceTracker.addEvent(RETRIEVE_STATE);
  }

  @Override
  public void engineGetPayload() {
    performanceTracker.addEvent(LOCAL_GET_PAYLOAD);
  }

  @Override
  public void builderGetHeader() {
    performanceTracker.addEvent(BUILDER_GET_HEADER);
    // set the flow to BUILDER when builderGetHeader has been called
    flow = Flow.BUILDER;
  }

  @Override
  public void builderBidValidated() {
    performanceTracker.addEvent(BUILDER_BID_VALIDATED);
  }

  @Override
  public void beaconBlockCreated() {
    performanceTracker.addEvent(BEACON_BLOCK_CREATED);
  }

  @Override
  public void stateTransition() {
    performanceTracker.addEvent(STATE_TRANSITION);
  }

  @Override
  public void stateHashing() {
    performanceTracker.addEvent(STATE_HASHING);
  }

  @Override
  public void getAttestationsForBlock() {
    performanceTracker.addEvent(GET_ATTESTATIONS_FOR_BLOCK);
  }

  @Override
  public void getPayloadAttestationsForBlock() {
    performanceTracker.addEvent(GET_PAYLOAD_ATTESTATIONS_FOR_BLOCK);
  }

  @Override
  public void beaconBlockBodyPreparationStarted() {
    performanceTracker.addEvent(BEACON_BLOCK_BODY_PREPARATION_STARTED);
  }
}

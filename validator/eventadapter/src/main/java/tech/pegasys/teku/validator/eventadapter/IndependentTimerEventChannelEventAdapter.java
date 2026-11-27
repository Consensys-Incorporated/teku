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

package tech.pegasys.teku.validator.eventadapter;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;

public class IndependentTimerEventChannelEventAdapter
    implements ChainHeadChannel, ReceivedExecutionPayloadEventsChannel, BeaconChainEventAdapter {

  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final boolean generateEarlyAttestations;
  private final ValidatorTimingChannel validatorTimingChannel;
  private final EventChannels eventChannels;

  /**
   * The block of the head last reported by fork choice, which the payload timeliness attestation
   * will be about. An execution payload for any other block says nothing about it, so it must not
   * bring the duty forward.
   */
  private volatile Optional<Bytes32> lastReportedHead = Optional.empty();

  public IndependentTimerEventChannelEventAdapter(
      final EventChannels eventChannels,
      final boolean generateEarlyAttestations,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.eventChannels = eventChannels;
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.validatorTimingChannel = validatorTimingChannel;
    this.generateEarlyAttestations = generateEarlyAttestations;
  }

  @Override
  public SafeFuture<Void> start() {
    return timeBasedEventAdapter
        .start()
        .thenRun(
            () -> {
              eventChannels.subscribe(ChainHeadChannel.class, this);
              eventChannels.subscribe(ReceivedExecutionPayloadEventsChannel.class, this);
            });
  }

  @Override
  public SafeFuture<Void> stop() {
    return timeBasedEventAdapter.stop();
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    lastReportedHead = Optional.of(bestBlockRoot);
    validatorTimingChannel.onHeadUpdate(
        slot, previousDutyDependentRoot, currentDutyDependentRoot, bestBlockRoot);
    if (generateEarlyAttestations) {
      validatorTimingChannel.onAttestationCreationDue(slot);
    }
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {}

  /**
   * Payload timeliness attestations should be sent as soon as the execution payload and its data
   * are available, rather than waiting for the deadline which the time based adapter schedules. A
   * payload for any block other than the head is ignored, so that the duty is not performed early
   * against a block which is not the one being attested to.
   */
  @Override
  public void onExecutionPayloadAvailable(final SignedExecutionPayloadEnvelope executionPayload) {
    if (lastReportedHead.map(executionPayload.getBeaconBlockRoot()::equals).orElse(false)) {
      validatorTimingChannel.onPayloadAttestationCreationDue(executionPayload.getSlot());
    }
  }

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {}
}

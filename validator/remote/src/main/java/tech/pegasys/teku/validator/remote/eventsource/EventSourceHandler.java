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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandler implements BackgroundEventHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorTimingChannel validatorTimingChannel;
  private final EventStreamMetrics metrics;
  private final boolean generateEarlyAttestations;
  private final ValidatorLogger validatorLogger;
  private final String beaconNodeEndpoint;

  /**
   * Whether a stream to this beacon node is currently open. Only a stream which was opened and then
   * failed tells us anything about the events the beacon node delivers: never reaching the node at
   * all is reported elsewhere.
   */
  private final AtomicBoolean connected = new AtomicBoolean(false);

  /**
   * Whether the current connection has delivered a head event. A beacon node which does not accept
   * the subscription still accepts the connection, so a stream which connects and then never
   * delivers anything is the only symptom, and without this it would leave the validator client
   * silently running on timer driven duties alone. A handler belongs to a single beacon node, so
   * one node failing to deliver events says nothing about the others.
   */
  private final AtomicBoolean headEventReceivedSinceConnected = new AtomicBoolean(false);

  private final Spec spec;

  public EventSourceHandler(
      final ValidatorTimingChannel validatorTimingChannel,
      final EventStreamMetrics metrics,
      final boolean generateEarlyAttestations,
      final Spec spec,
      final ValidatorLogger validatorLogger,
      final String beaconNodeEndpoint) {
    this.validatorTimingChannel = validatorTimingChannel;
    this.metrics = metrics;
    this.generateEarlyAttestations = generateEarlyAttestations;
    this.spec = spec;
    this.validatorLogger = validatorLogger;
    this.beaconNodeEndpoint = beaconNodeEndpoint;
  }

  @Override
  public void onOpen() {
    connected.set(true);
    headEventReceivedSinceConnected.set(false);
    validatorLogger.connectedToBeaconNodeEventStream();
    // We might have missed some events while connecting or reconnected so ensure the duties are
    // recalculated
    validatorTimingChannel.onPossibleMissedEvents();
  }

  @Override
  public void onClosed() {
    metrics.disconnectCounter().inc();
    LOG.info("Beacon node event stream closed");
  }

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) {
    LOG.trace("Received {} event from beacon node {}", event, messageEvent.getOrigin());
    try {
      final EventType eventType = EventType.valueOf(event);
      if (eventType == EventType.head) {
        headEventReceivedSinceConnected.set(true);
      }
      switch (eventType) {
        case head -> handleHeadEvent(messageEvent.getData());
        case attester_slashing -> handleAttesterSlashingEvent(messageEvent.getData());
        case proposer_slashing -> handleProposerSlashingEvent(messageEvent.getData());
        default -> LOG.warn("Received unexpected event type: " + event);
      }
    } catch (final IllegalArgumentException | JsonProcessingException e) {
      metrics.invalidEventCounter().inc();
      LOG.warn(
          "Received invalid event from beacon node. Event type: {} Event data: {}",
          event,
          messageEvent.getData(),
          e);
    }
  }

  private void handleHeadEvent(final String data) throws JsonProcessingException {
    final HeadEvent headEvent = JsonUtil.parse(data, HeadEvent.TYPE_DEFINITION);
    validatorTimingChannel.onHeadUpdate(
        headEvent.slot(),
        headEvent.previousDutyDependentRoot(),
        headEvent.currentDutyDependentRoot(),
        headEvent.block());
    if (generateEarlyAttestations) {
      validatorTimingChannel.onAttestationCreationDue(headEvent.slot());
    }
  }

  private void handleAttesterSlashingEvent(final String data) throws JsonProcessingException {
    final DeserializableTypeDefinition<AttesterSlashing> attesterSlashingTypeDefinition =
        spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema().getJsonTypeDefinition();
    final AttesterSlashing attesterSlashing = JsonUtil.parse(data, attesterSlashingTypeDefinition);
    validatorTimingChannel.onAttesterSlashing(attesterSlashing);
  }

  private void handleProposerSlashingEvent(final String data) throws JsonProcessingException {
    final DeserializableTypeDefinition<ProposerSlashing> proposerSlashingTypeDefinition =
        new ProposerSlashing.ProposerSlashingSchema().getJsonTypeDefinition();
    final ProposerSlashing proposerSlashing = JsonUtil.parse(data, proposerSlashingTypeDefinition);
    validatorTimingChannel.onProposerSlashing(proposerSlashing);
  }

  @Override
  public void onComment(final String comment) {}

  @Override
  public void onError(final Throwable t) {
    final boolean wasConnected = connected.getAndSet(false);
    if (wasConnected) {
      warnIfNoHeadEventsWereReceived();
    }
    if (Throwables.getRootCause(t) instanceof SocketTimeoutException) {
      metrics.timeoutCounter().inc();
      LOG.info(
          "Timed out waiting for events from beacon node event stream. "
              + "Reconnecting. This is normal if the beacon node is still syncing.");
    } else {
      metrics.errorCounter().inc();
      validatorLogger.beaconNodeEventStreamConnectionError();
    }
  }

  private void warnIfNoHeadEventsWereReceived() {
    if (!headEventReceivedSinceConnected.get()) {
      validatorLogger.noHeadEventsReceivedFromBeaconNodeEventStream(beaconNodeEndpoint);
    }
  }
}

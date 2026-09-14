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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import java.net.SocketTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandler implements BackgroundEventHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorTimingChannel validatorTimingChannel;
  private final Counter disconnectCounter;
  private final Counter invalidEventCounter;
  private final Counter timeoutCounter;
  private final Counter errorCounter;
  private final boolean generateEarlyAttestations;

  private final Spec spec;

  public EventSourceHandler(
      final ValidatorTimingChannel validatorTimingChannel,
      final MetricsSystem metricsSystem,
      final boolean generateEarlyAttestations,
      final Spec spec) {
    this.validatorTimingChannel = validatorTimingChannel;
    invalidEventCounter =
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
    disconnectCounter = eventSourceMetrics.labels("disconnect");
    timeoutCounter = eventSourceMetrics.labels("timeout");
    errorCounter = eventSourceMetrics.labels("error");
    this.generateEarlyAttestations = generateEarlyAttestations;
    this.spec = spec;
  }

  @Override
  public void onOpen() {
    VALIDATOR_LOGGER.connectedToBeaconNodeEventStream();
    // We might have missed some events while connecting or reconnected so ensure the duties are
    // recalculated
    validatorTimingChannel.onPossibleMissedEvents();
  }

  @Override
  public void onClosed() {
    disconnectCounter.inc();
    LOG.info("Beacon node event stream closed");
  }

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) {
    LOG.trace("Received {} event from beacon node {}", event, messageEvent.getOrigin());
    try {
      final EventType eventType = EventType.valueOf(event);
      switch (eventType) {
        case head -> handleHeadEvent(messageEvent.getData());
        case head_v2 -> handleHeadV2Event(messageEvent.getData());
        case attester_slashing -> handleAttesterSlashingEvent(messageEvent.getData());
        case proposer_slashing -> handleProposerSlashingEvent(messageEvent.getData());
        default -> LOG.warn("Received unexpected event type: " + event);
      }
    } catch (final IllegalArgumentException | JsonProcessingException e) {
      invalidEventCounter.inc();
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

  private void handleHeadV2Event(final String data) throws JsonProcessingException {
    final HeadV2Event headEvent = JsonUtil.parse(data, HeadV2Event.TYPE_DEFINITION);
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
    if (Throwables.getRootCause(t) instanceof SocketTimeoutException) {
      timeoutCounter.inc();
      LOG.info(
          "Timed out waiting for events from beacon node event stream. "
              + "Reconnecting. This is normal if the beacon node is still syncing.");
    } else {
      errorCounter.inc();
      VALIDATOR_LOGGER.beaconNodeEventStreamConnectionError();
    }
  }
}

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.launchdarkly.eventsource.MessageEvent;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandlerTest {

  final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);
  final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private static final String ENDPOINT = "http://localhost:5051";

  private final EventSourceHandler handler = createHandler(true);

  @Test
  void onOpen_shouldNotifyOfPotentialMissedEvents() {
    handler.onOpen();

    verify(validatorTimingChannel).onPossibleMissedEvents();
  }

  @Test
  void onMessage_shouldHandleHeadEvent() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadEvent event =
        new HeadEvent(
            slot,
            blockRoot,
            dataStructureUtil.randomBytes32(),
            false,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            false);
    handler.onMessage(
        EventType.head.name(),
        new MessageEvent(JsonUtil.serialize(event, HeadEvent.TYPE_DEFINITION)));

    verify(validatorTimingChannel)
        .onHeadUpdate(
            eq(slot), eq(previousDutyDependentRoot), eq(currentDutyDependentRoot), eq(blockRoot));
    verify(validatorTimingChannel).onAttestationCreationDue(slot);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleAttesterSlashingEvent() throws Exception {
    final IndexedAttestation indexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    final IndexedAttestation indexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    final AttesterSlashingSchema attesterSlashingSchema =
        spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema();
    final AttesterSlashing attesterSlashing =
        attesterSlashingSchema.create(indexedAttestation1, indexedAttestation2);

    handler.onMessage(
        EventType.attester_slashing.name(),
        new MessageEvent(
            JsonUtil.serialize(attesterSlashing, attesterSlashingSchema.getJsonTypeDefinition())));

    verify(validatorTimingChannel).onAttesterSlashing(eq(attesterSlashing));
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleProposerSlashingEvent() throws Exception {
    final SignedBeaconBlockHeader blockHeader1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    final SignedBeaconBlockHeader blockHeader2 = dataStructureUtil.randomSignedBeaconBlockHeader();

    final ProposerSlashing proposerSlashing = new ProposerSlashing(blockHeader1, blockHeader2);

    handler.onMessage(
        EventType.proposer_slashing.name(),
        new MessageEvent(
            JsonUtil.serialize(
                proposerSlashing,
                new ProposerSlashing.ProposerSlashingSchema().getJsonTypeDefinition())));

    verify(validatorTimingChannel).onProposerSlashing(eq(proposerSlashing));
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleInvalidMessage() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final HeadEvent event =
        new HeadEvent(
            slot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false);
    // Head message with a reorg type
    final MessageEvent messageEvent =
        new MessageEvent(JsonUtil.serialize(event, HeadEvent.TYPE_DEFINITION));
    assertDoesNotThrow(() -> handler.onMessage(EventType.chain_reorg.name(), messageEvent));
    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleUnparsableMessage() {
    // Head message with a reorg type
    final MessageEvent messageEvent = new MessageEvent("{this isn't json!}");
    assertDoesNotThrow(() -> handler.onMessage(EventType.chain_reorg.name(), messageEvent));
    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void onHeadEvent_shouldNotGenerateEarlyAttestationsIfNotEnabled() throws Exception {
    final EventSourceHandler onTimeHandler = createHandler(false);

    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadEvent event =
        new HeadEvent(
            slot,
            blockRoot,
            dataStructureUtil.randomBytes32(),
            false,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            false);

    final MessageEvent messageEvent =
        new MessageEvent(JsonUtil.serialize(event, HeadEvent.TYPE_DEFINITION));
    onTimeHandler.onMessage(EventType.head.name(), messageEvent);

    verify(validatorTimingChannel)
        .onHeadUpdate(
            eq(slot), eq(previousDutyDependentRoot), eq(currentDutyDependentRoot), eq(blockRoot));
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onError_shouldNotWarnWhenTheStreamWasNeverOpened() {
    // failing to reach the beacon node at all says nothing about the events it would deliver
    handler.onError(new IOException("connection refused"));

    verify(validatorLogger, never()).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldNotWarnRepeatedlyWhileTheBeaconNodeIsUnreachable() throws Exception {
    handler.onOpen();
    handler.onError(new IOException("connection reset"));

    // reconnect attempts which never get a stream open must stay quiet
    handler.onError(new IOException("connection refused"));
    handler.onError(new IOException("connection refused"));

    verify(validatorLogger, times(1)).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldWarnWhenTheStreamDeliveredNoHeadEvents() {
    handler.onOpen();

    handler.onError(new IOException("connection reset"));

    verify(validatorLogger).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldNotWarnWhenTheStreamDeliveredAHeadEvent() throws Exception {
    handler.onOpen();
    handler.onMessage(EventType.head.name(), headEvent());

    handler.onError(new IOException("connection reset"));

    verify(validatorLogger, never()).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldNotBeSatisfiedByEventsOtherThanHeadEvents() throws Exception {
    final AttesterSlashingSchema attesterSlashingSchema =
        spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema();
    final AttesterSlashing attesterSlashing =
        attesterSlashingSchema.create(
            dataStructureUtil.randomIndexedAttestation(),
            dataStructureUtil.randomIndexedAttestation());
    handler.onOpen();
    handler.onMessage(
        EventType.attester_slashing.name(),
        new MessageEvent(
            JsonUtil.serialize(attesterSlashing, attesterSlashingSchema.getJsonTypeDefinition())));

    handler.onError(new IOException("connection reset"));

    verify(validatorLogger).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldWarnAgainWhenAReconnectedStreamDeliversNoHeadEvents() throws Exception {
    handler.onOpen();
    handler.onMessage(EventType.head.name(), headEvent());
    handler.onError(new IOException("connection reset"));

    // a reconnect starts over, what the previous connection delivered proves nothing about this one
    handler.onOpen();
    handler.onError(new IOException("connection reset"));

    verify(validatorLogger).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  private EventSourceHandler createHandler(final boolean generateEarlyAttestations) {
    return new EventSourceHandler(
        validatorTimingChannel,
        EventStreamMetrics.create(metricsSystem),
        generateEarlyAttestations,
        spec,
        validatorLogger,
        ENDPOINT);
  }

  private MessageEvent headEvent() throws Exception {
    final HeadEvent event =
        new HeadEvent(
            UInt64.valueOf(134),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false);
    return new MessageEvent(JsonUtil.serialize(event, HeadEvent.TYPE_DEFINITION));
  }
}

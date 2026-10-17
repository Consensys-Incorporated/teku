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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.remote.eventsource.HeadV2Event.Data;

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
  void onMessage_shouldHandleHeadV2Event() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadV2Event event =
        new HeadV2Event(
            new Data(
                slot,
                blockRoot,
                dataStructureUtil.randomBytes32(),
                false,
                previousDutyDependentRoot,
                currentDutyDependentRoot,
                false,
                PayloadStatus.PAYLOAD_STATUS_PENDING.toString()));
    handler.onMessage(
        EventType.head_v2.name(),
        new MessageEvent(JsonUtil.serialize(event, HeadV2Event.TYPE_DEFINITION)));

    verify(validatorTimingChannel)
        .onHeadUpdate(
            eq(slot), eq(previousDutyDependentRoot), eq(currentDutyDependentRoot), eq(blockRoot));
    verify(validatorTimingChannel).onAttestationCreationDue(slot);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @ParameterizedTest(name = "onMessage_shouldAcceptPayloadStatus({0})InHeadV2Event")
  @EnumSource(PayloadStatus.class)
  void onMessage_shouldAcceptAnyPayloadStatusInHeadV2Event(final PayloadStatus payloadStatus)
      throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadV2Event event =
        new HeadV2Event(
            new Data(
                slot,
                blockRoot,
                dataStructureUtil.randomBytes32(),
                false,
                previousDutyDependentRoot,
                currentDutyDependentRoot,
                false,
                payloadStatus.toString()));
    handler.onMessage(
        EventType.head_v2.name(),
        new MessageEvent(JsonUtil.serialize(event, HeadV2Event.TYPE_DEFINITION)));

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
  void onHeadV2Event_shouldNotGenerateEarlyAttestationsIfNotEnabled() throws Exception {
    final EventSourceHandler onTimeHandler = createHandler(false);

    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadV2Event event =
        new HeadV2Event(
            new Data(
                slot,
                blockRoot,
                dataStructureUtil.randomBytes32(),
                false,
                previousDutyDependentRoot,
                currentDutyDependentRoot,
                false,
                PayloadStatus.PAYLOAD_STATUS_PENDING.toString()));

    final MessageEvent messageEvent =
        new MessageEvent(JsonUtil.serialize(event, HeadV2Event.TYPE_DEFINITION));
    onTimeHandler.onMessage(EventType.head_v2.name(), messageEvent);

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
  void onError_shouldNotWarnWhenTheStreamDeliveredAHeadV2Event() throws Exception {
    // once Gloas is scheduled the validator client subscribes to head_v2 instead of head
    handler.onOpen();
    handler.onMessage(EventType.head_v2.name(), headV2Event());

    handler.onError(new IOException("connection reset"));

    verify(validatorLogger, never()).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onError_shouldWarnWhenTheStreamDeliveredOnlyInvalidHeadEvents() {
    handler.onOpen();
    handler.onMessage(EventType.head.name(), new MessageEvent("{\"not\":\"a head event\"}"));

    handler.onError(new IOException("connection reset"));

    verify(validatorLogger).noHeadEventsReceivedFromBeaconNodeEventStream(ENDPOINT);
  }

  @Test
  void onMessage_shouldCountHandledEventsByType() throws Exception {
    handler.onMessage(EventType.head.name(), headEvent());
    handler.onMessage(EventType.head.name(), headEvent());
    handler.onMessage(EventType.head_v2.name(), headV2Event());

    assertThat(getEventCount(EventType.head)).isEqualTo(2);
    assertThat(getEventCount(EventType.head_v2)).isEqualTo(1);
  }

  @Test
  void onMessage_shouldNotCountInvalidEvents() {
    handler.onMessage(EventType.head.name(), new MessageEvent("{\"not\":\"a head event\"}"));

    assertThat(getEventCount(EventType.head)).isZero();
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

  @Test
  void onMessage_shouldRequestPayloadAttestationWhenTheHeadPayloadBecomesAvailable()
      throws Exception {
    // Example payloads from https://github.com/ethereum/beacon-APIs apis/eventstream/index.yaml,
    // which use the same block root for the head and for its execution payload
    handler.onMessage(EventType.head_v2.name(), new MessageEvent(SPEC_HEAD_V2_EVENT));
    clearInvocations(validatorTimingChannel);

    handler.onMessage(
        EventType.execution_payload_available.name(),
        new MessageEvent(SPEC_EXECUTION_PAYLOAD_AVAILABLE_EVENT));

    verify(validatorTimingChannel).onPayloadAttestationCreationDue(UInt64.valueOf(10));
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldNotRequestPayloadAttestationForAPayloadOfAnotherBlock() throws Exception {
    // the payload belongs to a block which is not the head, so it says nothing about the block the
    // payload timeliness attestation will be about
    handler.onMessage(EventType.head_v2.name(), headV2Event());
    clearInvocations(validatorTimingChannel);

    handler.onMessage(
        EventType.execution_payload_available.name(),
        new MessageEvent(SPEC_EXECUTION_PAYLOAD_AVAILABLE_EVENT));

    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldNotRequestPayloadAttestationBeforeAnyHeadIsKnown() throws Exception {
    handler.onMessage(
        EventType.execution_payload_available.name(),
        new MessageEvent(SPEC_EXECUTION_PAYLOAD_AVAILABLE_EVENT));

    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldCountAnInvalidExecutionPayloadAvailableEventWithoutThrowing() {
    final MessageEvent messageEvent = new MessageEvent("{\"slot\": \"10\"}");

    assertDoesNotThrow(
        () -> handler.onMessage(EventType.execution_payload_available.name(), messageEvent));

    verifyNoInteractions(validatorTimingChannel);
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.VALIDATOR, "event_stream_invalid_events_total"))
        .isEqualTo(1);
  }

  private EventSourceHandler createHandler(final boolean generateEarlyAttestations) {
    return new EventSourceHandler(
        validatorTimingChannel,
        metricsSystem,
        generateEarlyAttestations,
        spec,
        validatorLogger,
        ENDPOINT);
  }

  private long getEventCount(final EventType eventType) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.VALIDATOR, "event_stream_events_total", eventType.name());
  }

  private static final String SPEC_BLOCK_ROOT =
      "0x9a2fefd2fdb57f74993c7780ea5b9030d2897b615b89f808011ca5aebed54eaf";

  private static final String SPEC_EXECUTION_PAYLOAD_AVAILABLE_EVENT =
      "{\"slot\": \"10\", \"block_root\": \"" + SPEC_BLOCK_ROOT + "\"}";

  private static final String SPEC_HEAD_V2_EVENT =
      """
      {"version": "gloas", "data":{\
      "slot":"10", \
      "block":\""""
          + SPEC_BLOCK_ROOT
          + """
          ", \
          "state":"0x600e852a08c1200654ddf11025f1ceacb3c2e74bdd5c630cde0838b2591b69f9", \
          "payload_status": "full", \
          "epoch_transition":false, \
          "current_epoch_dependent_root":\
          "0x5e0043f107cb57913498fbf2f99ff55e730bf1e151f02f221e977c91a90a0e91", \
          "next_epoch_dependent_root":\
          "0x1111111111111111111111111111111111111111111111111111111111111111", \
          "execution_optimistic": false}}""";

  private MessageEvent headV2Event() throws Exception {
    final HeadV2Event event =
        new HeadV2Event(
            new Data(
                UInt64.valueOf(134),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                false,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                false,
                PayloadStatus.PAYLOAD_STATUS_PENDING.toString()));
    return new MessageEvent(JsonUtil.serialize(event, HeadV2Event.TYPE_DEFINITION));
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

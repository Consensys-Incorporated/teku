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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.launchdarkly.eventsource.MessageEvent;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
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

  private final EventSourceHandler handler =
      new EventSourceHandler(validatorTimingChannel, metricsSystem, true, spec);

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
    final EventSourceHandler onTimeHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, false, spec);

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
    final EventSourceHandler onTimeHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, false, spec);

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
}

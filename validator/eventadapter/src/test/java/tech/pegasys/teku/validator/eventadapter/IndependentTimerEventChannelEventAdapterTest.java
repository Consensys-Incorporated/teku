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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;

class IndependentTimerEventChannelEventAdapterTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);
  private final BeaconChainEventAdapter timeBasedEventAdapter = mock(BeaconChainEventAdapter.class);

  private final IndependentTimerEventChannelEventAdapter adapter =
      new IndependentTimerEventChannelEventAdapter(
          eventChannels, true, timeBasedEventAdapter, validatorTimingChannel);

  @Test
  void shouldSubscribeToHeadAndExecutionPayloadEvents() {
    when(timeBasedEventAdapter.start()).thenReturn(SafeFuture.COMPLETE);

    assertThat(adapter.start()).isCompleted();

    verify(eventChannels).subscribe(ChainHeadChannel.class, adapter);
    verify(eventChannels).subscribe(ReceivedExecutionPayloadEventsChannel.class, adapter);
  }

  @Test
  void shouldRequestPayloadAttestationWhenTheHeadPayloadBecomesAvailable() {
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(10);
    headUpdatedTo(executionPayload.getBeaconBlockRoot());

    adapter.onExecutionPayloadAvailable(executionPayload);

    verify(validatorTimingChannel).onPayloadAttestationCreationDue(executionPayload.getSlot());
  }

  @Test
  void shouldNotRequestPayloadAttestationForAPayloadOfAnotherBlock() {
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(10);
    // the payload belongs to a block which is not the head, so it says nothing about the block the
    // payload timeliness attestation will be about
    headUpdatedTo(dataStructureUtil.randomBytes32());
    clearInvocations(validatorTimingChannel);

    adapter.onExecutionPayloadAvailable(executionPayload);

    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void shouldNotRequestPayloadAttestationBeforeAnyHeadIsKnown() {
    adapter.onExecutionPayloadAvailable(dataStructureUtil.randomSignedExecutionPayloadEnvelope(10));

    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void shouldNotRequestPayloadAttestationBeforeTheDataIsAvailable() {
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(10);
    // the payload is for the head, so only the availability of its data is left to decide whether
    // the duty is brought forward
    headUpdatedTo(executionPayload.getBeaconBlockRoot());
    clearInvocations(validatorTimingChannel);

    // the payload passed gossip validation, but its data has not been checked yet
    adapter.onExecutionPayloadValidated(executionPayload);
    // and importing it adds nothing, the attestation was already requested when it became available
    adapter.onExecutionPayloadImported(executionPayload, false);

    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void shouldIgnoreThePayloadStatusOfAHeadUpdate() {
    // payload status is decided by the beacon node when each duty is produced, the validator
    // client only needs the head to know whether its duties are still valid
    headUpdatedTo(dataStructureUtil.randomBytes32());

    verify(validatorTimingChannel).onHeadUpdate(any(), any(), any(), any());
    verify(validatorTimingChannel).onAttestationCreationDue(any());
  }

  private void headUpdatedTo(final Bytes32 blockRoot) {
    adapter.chainHeadUpdated(
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        blockRoot,
        false,
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.empty(),
        Optional.empty());
  }
}

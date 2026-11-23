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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.libp2p.core.pubsub.ValidationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.SingleAttestationTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.statetransition.pq.timing.PqTimingEvents;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SingleAttestationTopicHandlerTest
    extends AbstractTopicHandlerTest<ValidatableAttestation> {

  private static final int SUBNET_ID = 1;
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);

  @Override
  protected Eth2TopicHandler<?> createHandler() {
    return createHandler(false);
  }

  private Eth2TopicHandler<?> createHandler(final boolean recordPqTiming) {
    return SingleAttestationTopicHandler.createHandler(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkInfo,
        forkDigest,
        GossipTopicName.getAttestationSubnetTopicName(SUBNET_ID),
        spec.getGenesisSchemaDefinitions().getAttestationSchema(),
        SUBNET_ID,
        DebugDataDumper.NOOP,
        recordPqTiming);
  }

  @Test
  public void handleMessage_recordsPqTimingWhenAsked(@TempDir final Path timingRoot)
      throws Exception {
    final Eth2TopicHandler<?> timedHandler = createHandler(true);
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(getChainHead()), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    final PreparedGossipMessage message =
        timedHandler.prepareMessage(
            gossipEncoding.encode(attestation.getAttestation()), Optional.empty());
    PqTimingEvents.start(timingRoot, false);

    final SafeFuture<ValidationResult> result = timedHandler.handleMessage(message);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    final List<JsonNode> events = PqTimingEvents.stopAndRead(timingRoot);
    assertThat(events).hasSize(2);
    final JsonNode received = events.get(0);
    assertThat(received.get("kind").asText()).isEqualTo("message_received");
    assertThat(received.get("message_kind").asText()).isEqualTo("individual");
    assertThat(received.get("message_id").asText()).isEqualTo(message.getMessageId().toHexString());
    assertThat(received.get("slot").asLong())
        .isEqualTo(attestation.getData().getSlot().longValue());
    assertThat(received.get("committee").asLong())
        .isEqualTo(attestation.getData().getIndex().longValue());
    assertThat(received.get("subnet").asInt()).isEqualTo(SUBNET_ID);
    assertThat(received.get("signer_count").asInt()).isEqualTo(1);
    assertThat(received.get("message_bytes").asInt())
        .isEqualTo(attestation.getAttestation().sszSerialize().size());
    assertThat(received.get("vote_data_root").asText())
        .isEqualTo(attestation.getData().hashTreeRoot().toHexString());
    final JsonNode validated = events.get(1);
    assertThat(validated.get("kind").asText()).isEqualTo("validated");
    assertThat(validated.get("message_id").asText())
        .isEqualTo(message.getMessageId().toHexString());
    assertThat(validated.get("outcome").asText()).isEqualTo("ignored");
  }

  @Test
  public void handleMessage_recordsNoPqTimingUnlessAsked(@TempDir final Path timingRoot)
      throws Exception {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(getChainHead()), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    PqTimingEvents.start(timingRoot, false);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(attestation.getAttestation()), Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    assertThat(PqTimingEvents.stopAndRead(timingRoot)).isEmpty();
  }

  @Test
  public void handleMessage_valid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = getChainHead();
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_invalid_wrongFork() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState =
        storageSystem.chainBuilder().getBlockAndStateAtSlot(wrongForkSlot);
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(processor);
  }

  @Test
  public void handleMessage_ignored() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = getChainHead();
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_saveForFuture() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = getChainHead();
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = getChainHead();
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromNetwork(
            spec, attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidAttestation_invalidSSZ() {
    final Bytes serialized = Bytes.fromHexString("0x3456");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    assertThat(topicHandler.getTopic())
        .isEqualTo(
            "/eth2/" + forkDigest.toUnprefixedHexString() + "/beacon_attestation_1/ssz_snappy");
  }
}

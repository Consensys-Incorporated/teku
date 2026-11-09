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

import io.libp2p.core.pubsub.ValidationResult;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.SingleAttestationTopicHandler;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SingleAttestationTopicHandlerTest
    extends AbstractTopicHandlerTest<ValidatableAttestation> {

  private static final int SUBNET_ID = 1;
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);

  @Override
  protected Eth2TopicHandler<?> createHandler() {
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
        DebugDataDumper.NOOP);
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

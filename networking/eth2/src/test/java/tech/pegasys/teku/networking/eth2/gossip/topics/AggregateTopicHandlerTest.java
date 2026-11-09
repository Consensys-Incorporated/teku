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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AggregateTopicHandlerTest extends AbstractTopicHandlerTest<ValidatableAttestation> {

  @Override
  protected Eth2TopicHandler<?> createHandler() {
    return new AggregateGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            processor,
            DebugDataDumper.NOOP)
        .getTopicHandler();
  }

  @Test
  public void handleMessage_validAggregate() {
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof(validSlot));
    when(processor.process(aggregate, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof()), Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_invalidAggregate_wrongFork() {
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof(wrongForkSlot));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof()), Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(processor);
  }

  @Test
  public void handleMessage_savedForFuture() {
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof(validSlot));
    when(processor.process(aggregate, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof()), Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_ignoredAggregate() {
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof(validSlot));
    when(processor.process(aggregate, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof()), Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidAggregate() {
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof(validSlot));
    when(processor.process(aggregate, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof()), Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    assertThat(topicHandler.getTopic())
        .isEqualTo(
            "/eth2/"
                + forkDigest.toUnprefixedHexString()
                + "/beacon_aggregate_and_proof/ssz_snappy");
  }
}

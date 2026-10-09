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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class BlockTopicHandlerTest extends AbstractTopicHandlerTest<SignedBeaconBlock> {

  @Override
  protected Eth2TopicHandler<SignedBeaconBlock> createHandler() {
    return new BlockGossipManager(
            recentChainData,
            spec,
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
  public void handleMessage_validBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = chainBuilder.generateBlockAtSlot(nextSlot).getBlock();
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(eq(block), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_validFutureBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = chainBuilder.generateBlockAtSlot(nextSlot).getBlock();
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(eq(block), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBlock_unknownPreState() {
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(validSlot);
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(eq(block), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBlock_wrongFork() {
    final SignedBeaconBlock prevForkBlock =
        storageSystem.chainBuilder().getBlockAtSlot(wrongForkSlot);
    Bytes serialized = gossipEncoding.encode(prevForkBlock);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(processor);
  }

  @Test
  public void handleMessage_invalidBlock_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidBlock_wrongProposer() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block =
        chainBuilder
            .generateBlockAtSlot(nextSlot, BlockOptions.create().setWrongProposer(true))
            .getBlock();
    Bytes serialized = gossipEncoding.encode(block);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/" + forkDigest.toUnprefixedHexString() + "/beacon_block/ssz_snappy");
  }
}

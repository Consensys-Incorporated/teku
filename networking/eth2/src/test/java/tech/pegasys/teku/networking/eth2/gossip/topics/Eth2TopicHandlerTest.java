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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class Eth2TopicHandlerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
  private final Bytes blockBytes = GossipEncoding.SSZ_SNAPPY.encode(block);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final DebugDataDumper debugDataDumper = mock(DebugDataDumper.class);

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  public void handleMessage_valid() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_invalid() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.reject("Nope")),
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();
    verify(debugDataDumper)
        .saveGossipRejectedMessage(eq(topicHandler.getTopic()), any(), any(), any());
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_ignore() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.IGNORE),
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBytes() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);
    final Bytes invalidBytes = Bytes.fromHexString("0x0102");
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(invalidBytes, Optional.empty()));
    verify(debugDataDumper)
        .saveGossipMessageDecodingError(eq(topicHandler.getTopic()), any(), any(), any());
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_decodingException() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);
    topicHandler.setDeserializer(
        (b) -> {
          throw new DecodingException("oops");
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    verify(debugDataDumper)
        .saveGossipMessageDecodingError(eq(topicHandler.getTopic()), any(), any(), any());
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedDecodingException() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);
    topicHandler.setDeserializer(
        (b) -> {
          throw new CompletionException(new DecodingException("oops"));
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    verify(debugDataDumper)
        .saveGossipMessageDecodingError(eq(topicHandler.getTopic()), any(), any(), any());
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_decodingExceptionWithCause() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);
    topicHandler.setDeserializer(
        (b) -> {
          throw new DecodingException("oops", new RuntimeException("oops"));
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    verify(debugDataDumper)
        .saveGossipMessageDecodingError(eq(topicHandler.getTopic()), any(), any(), any());
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_rejectedExecution() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new RejectedExecutionException("No more capacity");
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedRejectedExecution() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new CompletionException(new RejectedExecutionException("No more capacity"));
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_rejectedExecutionWithRootCause() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new RejectedExecutionException("No more capacity", new NullPointerException());
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_serviceCapacityExceededExecution() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new ServiceCapacityExceededException("No more capacity");
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedServiceCapacityExceededExecution() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new CompletionException(
                  new ServiceCapacityExceededException("No more capacity"));
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_unknownError() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> {
              throw new NullPointerException();
            },
            debugDataDumper);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes, Optional.empty()));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void getMaxMessageSize_shouldAllowWorstCaseSnappyExpansionOfMaxPayload() {
    final MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            recentChainData,
            spec,
            asyncRunner,
            (b, __) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT),
            debugDataDumper);

    // The gossip size gate runs against the compressed payload, so it must allow snappy's
    // worst-case expansion of a MAX_PAYLOAD_SIZE payload (max_compressed_len from the spec)
    final int maxPayloadSize = spec.getNetworkingConfig().getMaxPayloadSize();
    assertThat(topicHandler.getMaxMessageSize())
        .isEqualTo(maxPayloadSize + 32 + maxPayloadSize / 6);
  }

  private static class MockEth2TopicHandler extends Eth2TopicHandler<SignedBeaconBlock> {
    private final Bytes4 forkDigest;
    private Deserializer<SignedBeaconBlock> deserializer;
    private static final GossipEncoding GOSSIP_ENCODING = GossipEncoding.SSZ_SNAPPY;

    protected MockEth2TopicHandler(
        final RecentChainData recentChainData,
        final Spec spec,
        final AsyncRunner asyncRunner,
        final OperationProcessor<SignedBeaconBlock> processor,
        final DebugDataDumper debugDataDumper) {
      super(
          recentChainData,
          asyncRunner,
          processor,
          GOSSIP_ENCODING,
          recentChainData.getForkDigestByMilestone(SpecMilestone.PHASE0).orElseThrow(),
          "test",
          new OperationMilestoneValidator<>(
              spec, spec.getForkSchedule().getFork(UInt64.ZERO), message -> UInt64.ZERO),
          spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema(),
          spec.getNetworkingConfig(),
          debugDataDumper);
      this.forkDigest =
          recentChainData.getForkDigestByMilestone(SpecMilestone.PHASE0).orElseThrow();
      deserializer =
          (bytes) ->
              getGossipEncoding()
                  .decodeMessage(
                      bytes, spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema());
    }

    public void setDeserializer(final Deserializer<SignedBeaconBlock> deserializer) {
      this.deserializer = deserializer;
    }

    @Override
    public SignedBeaconBlock deserialize(final PreparedGossipMessage message)
        throws DecodingException {
      return deserializer.deserialize(message);
    }

    @Override
    public Bytes4 getForkDigest() {
      return forkDigest;
    }

    @Override
    public GossipEncoding getGossipEncoding() {
      return GOSSIP_ENCODING;
    }
  }

  private interface Deserializer<T> {
    T deserialize(final PreparedGossipMessage bytes) throws DecodingException;
  }
}

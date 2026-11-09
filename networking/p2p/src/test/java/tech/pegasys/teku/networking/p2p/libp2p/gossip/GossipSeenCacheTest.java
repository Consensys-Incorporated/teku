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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import com.google.protobuf.ByteString;
import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.core.security.SecureChannel;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipRouterEventListener;
import io.libp2p.transport.implementation.StreamOverNetty;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pubsub.pb.Rpc;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.spec.TestSpecFactory;

/**
 * Feeds inbound publish RPCs through the pipeline of the router that {@link
 * LibP2PGossipNetworkBuilder} builds for production, so the seen cache it configures (and the
 * {@link FastMessageId} fast id it is keyed by) is exercised where jvm-libp2p consults it.
 */
class GossipSeenCacheTest {
  private static final String TOPIC = "/eth2/aabbccdd/beacon_block/ssz_snappy";

  private final PeerId peerId = PeerId.random();
  private final EmbeddedChannel channel = new EmbeddedChannel();
  private final GossipTopicHandlers topicHandlers = new GossipTopicHandlers();
  private final RecordingListener listener = new RecordingListener();
  private final GossipRouter router =
      new LibP2PGossipNetworkBuilder() {}.timeProvider(StubTimeProvider.withTimeInSeconds(1_000))
          .defaultMessageFactory(mock(PreparedGossipMessageFactory.class))
          .createGossipRouter(
              GossipConfig.builder().build(),
              TestSpecFactory.createMinimalPhase0().getNetworkingConfig(),
              topic -> true,
              topicHandlers);

  @BeforeEach
  void setUp() {
    topicHandlers.add(TOPIC, new UniqueIdTopicHandler());
    router.getEventBroadcaster().getListeners().add(listener);
    router.initHandler(msg -> CompletableFuture.completedFuture(ValidationResult.Valid));
    router.subscribe(TOPIC);

    final Connection connection = mock(Connection.class);
    when(connection.secureSession())
        .thenReturn(new SecureChannel.Session(PeerId.random(), peerId, mock(PubKey.class), null));
    router.addPeer(new StreamOverNetty(channel, connection, false));
  }

  @AfterEach
  void tearDown() {
    channel.finishAndReleaseAll();
  }

  @Test
  void recognisesADuplicateDeliveredAsASecondMessageInstance() throws Exception {
    final Rpc.Message message = message(Bytes.fromHexString("0x0102030405"));

    receive(message);
    waitFor(() -> assertThat(listener.unseen).hasSize(1));

    // A second copy arrives as a fresh Rpc.Message / PubsubMessage instance with the same bytes.
    receive(message.toBuilder().build());
    waitFor(() -> assertThat(listener.seen).hasSize(1));
    assertThat(listener.unseen).hasSize(1);

    // The hit came from the fast id: the cached copy carries the first delivery's message id even
    // though the topic handler assigned the second delivery a different one.
    assertThat(listener.seen.get(0).getMessageId())
        .isEqualTo(listener.unseen.get(0).getMessageId());
  }

  @Test
  void treatsAMessageWithADifferentPayloadAsUnseen() throws Exception {
    receive(message(Bytes.fromHexString("0x0102030405")));
    waitFor(() -> assertThat(listener.unseen).hasSize(1));

    receive(message(Bytes.fromHexString("0x0102030406")));
    waitFor(() -> assertThat(listener.unseen).hasSize(2));
    // every message is reported as either seen or unseen when its RPC is processed, so once both
    // unseen notifications are in nothing can still mark one of them as seen
    assertThat(listener.seen).isEmpty();
  }

  private static Rpc.Message message(final Bytes payload) {
    return Rpc.Message.newBuilder()
        .addTopicIDs(TOPIC)
        .setData(ByteString.copyFrom(payload.toArrayUnsafe()))
        .build();
  }

  /** Encodes the RPC with the channel's own outbound codec and feeds the bytes back inbound. */
  private void receive(final Rpc.Message message) {
    assertThat(channel.writeOutbound(Rpc.RPC.newBuilder().addPublish(message).build())).isTrue();
    final ByteBuf encoded = channel.readOutbound();
    channel.writeInbound(encoded);
  }

  /**
   * Gives every prepared message a message id of its own, so a duplicate can only be recognised
   * through the seen cache's fast id and never through the (slow) message id fallback.
   */
  private static class UniqueIdTopicHandler implements TopicHandler {
    private final AtomicInteger nextId = new AtomicInteger();

    @Override
    public PreparedGossipMessage prepareMessage(
        final Bytes payload, final Optional<UInt64> arrivalTimestamp) {
      final Bytes32 messageId = Bytes32.leftPad(Bytes.ofUnsignedInt(nextId.incrementAndGet()));
      return new PreparedGossipMessage() {
        @Override
        public Bytes getMessageId() {
          return messageId;
        }

        @Override
        public DecodedMessageResult getDecodedMessage() {
          return DecodedMessageResult.successful(payload);
        }

        @Override
        public Bytes getOriginalMessage() {
          return payload;
        }

        @Override
        public Optional<UInt64> getArrivalTimestamp() {
          return arrivalTimestamp;
        }
      };
    }

    @Override
    public SafeFuture<ValidationResult> handleMessage(final PreparedGossipMessage message) {
      return SafeFuture.completedFuture(ValidationResult.Valid);
    }

    @Override
    public int getMaxMessageSize() {
      return 1024;
    }
  }

  private static class RecordingListener implements GossipRouterEventListener {
    private final List<PubsubMessage> unseen = new CopyOnWriteArrayList<>();
    private final List<PubsubMessage> seen = new CopyOnWriteArrayList<>();

    @Override
    public void notifyUnseenMessage(
        @NotNull final PeerId peerId, @NotNull final PubsubMessage msg) {
      unseen.add(msg);
    }

    @Override
    public void notifySeenMessage(
        @NotNull final PeerId peerId,
        @NotNull final PubsubMessage msg,
        @NotNull final Optional<ValidationResult> validationResult) {
      seen.add(msg);
    }

    @Override
    public void notifyDisconnected(@NotNull final PeerId peerId) {}

    @Override
    public void notifyConnected(@NotNull final PeerId peerId, @NotNull final Multiaddr addr) {}

    @Override
    public void notifyUnseenValidMessage(
        @NotNull final PeerId peerId, @NotNull final PubsubMessage msg) {}

    @Override
    public void notifyMeshed(@NotNull final PeerId peerId, @NotNull final String topic) {}

    @Override
    public void notifyPruned(@NotNull final PeerId peerId, @NotNull final String topic) {}

    @Override
    public void notifyRouterMisbehavior(@NotNull final PeerId peerId, final int count) {}

    @Override
    public void notifySlowPeer(@NotNull final PeerId peerId) {}
  }
}

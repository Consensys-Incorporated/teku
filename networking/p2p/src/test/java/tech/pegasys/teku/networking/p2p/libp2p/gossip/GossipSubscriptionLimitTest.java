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
import static tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory.MAX_INBOUND_SUBSCRIPTIONS_PER_RPC;
import static tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory.MAX_OUTBOUND_SUBSCRIPTIONS_PER_RPC;
import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder.MAX_SUBSCRIBED_TOPICS;

import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.security.SecureChannel;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.transport.implementation.StreamOverNetty;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pubsub.pb.Rpc;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.spec.TestSpecFactory;

/**
 * Feeds inbound subscription RPCs through the pipeline of the router that {@link
 * LibP2PGossipNetworkBuilder} builds for production, so the limits are exercised where jvm-libp2p
 * enforces them rather than compared as constants.
 */
class GossipSubscriptionLimitTest {
  private static final String RELEVANT_PREFIX = "/eth2/aabbccdd/";

  private final PeerId peerId = PeerId.random();
  private final EmbeddedChannel channel = new EmbeddedChannel();
  private final GossipRouter router =
      new LibP2PGossipNetworkBuilder() {}.createGossipRouter(
          GossipConfig.builder().build(),
          TestSpecFactory.createMinimalPhase0().getNetworkingConfig(),
          topic -> topic.startsWith(RELEVANT_PREFIX),
          new GossipTopicHandlers());

  @BeforeEach
  void connectPeer() {
    final Connection connection = mock(Connection.class);
    when(connection.secureSession())
        .thenReturn(new SecureChannel.Session(PeerId.random(), peerId, mock(PubKey.class), null));
    router.addPeer(new StreamOverNetty(channel, connection, false));
  }

  @AfterEach
  void disconnectPeer() {
    channel.finishAndReleaseAll();
  }

  @ParameterizedTest
  @ValueSource(ints = {MAX_OUTBOUND_SUBSCRIPTIONS_PER_RPC + 1, MAX_INBOUND_SUBSCRIPTIONS_PER_RPC})
  void acceptsAnnouncementWithinInboundLimit(final int topicCount) throws Exception {
    receive(subscribe(0, topicCount));

    assertThat(peerTopics()).hasSize(topicCount);
  }

  @Test
  void dropsWholeAnnouncementAboveInboundLimit() throws Exception {
    receive(subscribe(0, MAX_INBOUND_SUBSCRIPTIONS_PER_RPC + 1));

    assertThat(peerTopics()).isEmpty();
  }

  @Test
  void dropsAnnouncementTakingPeerAboveMaxSubscribedTopics() throws Exception {
    for (int from = 0; from < MAX_SUBSCRIBED_TOPICS; from += MAX_INBOUND_SUBSCRIPTIONS_PER_RPC) {
      receive(
          subscribe(
              from, Math.min(MAX_INBOUND_SUBSCRIPTIONS_PER_RPC, MAX_SUBSCRIBED_TOPICS - from)));
    }
    assertThat(peerTopics()).hasSize(MAX_SUBSCRIBED_TOPICS);

    receive(subscribe(MAX_SUBSCRIBED_TOPICS, 1));

    assertThat(peerTopics()).hasSize(MAX_SUBSCRIBED_TOPICS);
  }

  @Test
  void ignoresIrrelevantTopicsWithoutDroppingTheAnnouncement() throws Exception {
    receive(
        subscribe(0, 2).toBuilder()
            .addSubscriptions(
                Rpc.RPC.SubOpts.newBuilder().setSubscribe(true).setTopicid("/other/topic"))
            .build());

    assertThat(peerTopics()).containsExactlyInAnyOrder(topic(0), topic(1));
  }

  private static Rpc.RPC subscribe(final int from, final int count) {
    final Rpc.RPC.Builder rpc = Rpc.RPC.newBuilder();
    IntStream.range(from, from + count)
        .forEach(
            i ->
                rpc.addSubscriptions(
                    Rpc.RPC.SubOpts.newBuilder().setSubscribe(true).setTopicid(topic(i))));
    return rpc.build();
  }

  private static String topic(final int i) {
    return RELEVANT_PREFIX + "topic_" + i + "/ssz_snappy";
  }

  /** Encodes the RPC with the channel's own outbound codec and feeds the bytes back inbound. */
  private void receive(final Rpc.RPC rpc) {
    assertThat(channel.writeOutbound(rpc)).isTrue();
    final ByteBuf encoded = channel.readOutbound();
    channel.writeInbound(encoded);
  }

  private Set<String> peerTopics() throws Exception {
    return waitFor(router.getPeerTopics()).getOrDefault(peerId, Set.of());
  }
}

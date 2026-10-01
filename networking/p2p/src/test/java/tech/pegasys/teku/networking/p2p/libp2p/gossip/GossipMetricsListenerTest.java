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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P_GOSSIP;

import com.google.protobuf.ByteString;
import io.libp2p.core.PeerId;
import io.libp2p.pubsub.DefaultPubsubMessage;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipRouterEventBroadcaster;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Test;
import pubsub.pb.Rpc;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

class GossipMetricsListenerTest {

  private static final String ATTESTATION_SUBNET_0 =
      "/eth2/aabbccdd/beacon_attestation_0/ssz_snappy";
  private static final String ATTESTATION_SUBNET_1 =
      "/eth2/aabbccdd/beacon_attestation_1/ssz_snappy";
  private static final String BEACON_BLOCK = "/eth2/aabbccdd/beacon_block/ssz_snappy";

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final GossipMetricsListener listener = new GossipMetricsListener(metricsSystem);

  @Test
  void countsFirstSeenMessagesAndTheirBytesByTopicShape() {
    listener.notifyUnseenMessage(PeerId.random(), message(ATTESTATION_SUBNET_0, 100));
    listener.notifyUnseenMessage(PeerId.random(), message(ATTESTATION_SUBNET_1, 50));

    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_topic_msg_recv_counts", "beacon_attestation"))
        .isEqualTo(2);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_topic_msg_recv_bytes", "beacon_attestation"))
        .isEqualTo(150);
  }

  @Test
  void countsDuplicateAcceptedAndInvalidMessagesSeparately() {
    listener.notifySeenMessage(PeerId.random(), message(BEACON_BLOCK, 1), Optional.empty());
    listener.notifyUnseenValidMessage(PeerId.random(), message(BEACON_BLOCK, 1));
    listener.notifyUnseenInvalidMessage(PeerId.random(), message(BEACON_BLOCK, 1));

    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_duplicate_messages_total", "beacon_block"))
        .isEqualTo(1);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_accepted_messages_per_topic", "beacon_block"))
        .isEqualTo(1);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_invalid_messages_per_topic", "beacon_block"))
        .isEqualTo(1);
  }

  @Test
  void countsMisbehaviourAndSlowPeerEvents() {
    listener.notifyRouterMisbehavior(PeerId.random(), 5);
    listener.notifySlowPeer(PeerId.random());

    assertThat(
            metricsSystem.getCounterValue(
                LIBP2P_GOSSIP, "gossipsub_router_misbehaviour_events_total"))
        .isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(LIBP2P_GOSSIP, "gossipsub_slow_peer_events_total"))
        .isEqualTo(1);
  }

  @Test
  void meshGaugeSumsPeersAcrossEverySubnetOfATopicShape() {
    listener.notifyMeshed(PeerId.random(), ATTESTATION_SUBNET_0);
    listener.notifyMeshed(PeerId.random(), ATTESTATION_SUBNET_1);
    listener.notifyMeshed(PeerId.random(), BEACON_BLOCK);

    assertThat(meshPeerCount("beacon_attestation")).isEqualTo(2);
    assertThat(meshPeerCount("beacon_block")).isEqualTo(1);
  }

  @Test
  void pruneRemovesThePeerFromTheMeshGauge() {
    final PeerId peer = PeerId.random();
    listener.notifyMeshed(peer, BEACON_BLOCK);
    assertThat(meshPeerCount("beacon_block")).isEqualTo(1);

    listener.notifyPruned(peer, BEACON_BLOCK);

    assertThat(meshPeerCount("beacon_block")).isZero();
    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_mesh_peer_churn_events", "beacon_block"))
        .isEqualTo(1);
  }

  @Test
  void meshingTheSamePeerTwiceCountsItOnce() {
    final PeerId peer = PeerId.random();
    listener.notifyMeshed(peer, BEACON_BLOCK);
    listener.notifyMeshed(peer, BEACON_BLOCK);

    assertThat(meshPeerCount("beacon_block")).isEqualTo(1);
  }

  /**
   * GossipRouter.onPeerDisconnected drops the peer from every mesh without emitting a prune, so
   * tracking only meshed/pruned would leak mesh peers on every disconnect.
   */
  @Test
  void disconnectRemovesThePeerFromEveryMesh() {
    final PeerId leaving = PeerId.random();
    final PeerId staying = PeerId.random();
    listener.notifyMeshed(leaving, ATTESTATION_SUBNET_0);
    listener.notifyMeshed(leaving, BEACON_BLOCK);
    listener.notifyMeshed(staying, BEACON_BLOCK);

    listener.notifyDisconnected(leaving);

    assertThat(meshPeerCount("beacon_attestation")).isZero();
    assertThat(meshPeerCount("beacon_block")).isEqualTo(1);
  }

  /** StubLabelledGauge throws if the same label set is registered twice. */
  @Test
  void registersOneGaugeSeriesPerTopicShapeRegardlessOfSubnetCount() {
    listener.notifyMeshed(PeerId.random(), ATTESTATION_SUBNET_0);
    listener.notifyMeshed(PeerId.random(), ATTESTATION_SUBNET_1);
    listener.notifyMeshed(PeerId.random(), "/eth2/aabbccdd/beacon_attestation_2/ssz_snappy");

    assertThat(meshPeerCount("beacon_attestation")).isEqualTo(3);
  }

  @Test
  void unparseableTopicIsBucketedRatherThanMintingASeries() {
    listener.notifyUnseenMessage(PeerId.random(), message("garbage-topic", 10));

    assertThat(
            metricsSystem.getLabelledCounterValue(
                LIBP2P_GOSSIP, "gossipsub_topic_msg_recv_counts", GossipTopicShape.OTHER))
        .isEqualTo(1);
  }

  @Test
  void doesNotAttachWhenTheCategoryIsDisabled() {
    // StubMetricsSystem reports no enabled categories.
    assertThat(GossipMetricsListener.attachTo(metricsSystem, mock(GossipRouter.class))).isEmpty();
  }

  @Test
  void attachesAndRegistersItselfWhenTheCategoryIsEnabled() {
    final GossipRouterEventBroadcaster broadcaster = new GossipRouterEventBroadcaster();
    final GossipRouter router = mock(GossipRouter.class);
    when(router.getEventBroadcaster()).thenReturn(broadcaster);

    final Optional<GossipMetricsListener> attached =
        GossipMetricsListener.attachTo(new CategoryEnabledMetricsSystem(), router);

    assertThat(attached).isPresent();
    assertThat(broadcaster.getListeners()).containsExactly(attached.orElseThrow());
  }

  /** StubMetricsSystem reports no enabled categories, so the gate needs one that does. */
  private static class CategoryEnabledMetricsSystem extends StubMetricsSystem {
    @Override
    public Set<MetricCategory> getEnabledCategories() {
      return Set.of(LIBP2P_GOSSIP);
    }
  }

  private double meshPeerCount(final String topicShape) {
    return metricsSystem
        .getLabelledGauge(LIBP2P_GOSSIP, "gossipsub_mesh_peer_counts")
        .getValue(topicShape)
        .orElseThrow();
  }

  private static PubsubMessage message(final String topic, final int payloadSize) {
    return new DefaultPubsubMessage(
        Rpc.Message.newBuilder()
            .addTopicIDs(topic)
            .setData(ByteString.copyFrom(new byte[payloadSize]))
            .build());
  }
}

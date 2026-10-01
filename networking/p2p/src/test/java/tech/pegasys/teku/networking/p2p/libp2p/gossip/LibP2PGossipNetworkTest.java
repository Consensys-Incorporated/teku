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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P_GOSSIP;

import io.libp2p.core.PeerId;
import io.libp2p.pubsub.PubsubProtocol;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipRouterEventBroadcaster;
import java.util.Set;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

class LibP2PGossipNetworkTest {
  private static final String TOPIC = "/eth2/aabbccdd/beacon_attestation_0/ssz_snappy";
  private final GossipRouter router = mock(GossipRouter.class);
  private final GossipRouterEventBroadcaster broadcaster = new GossipRouterEventBroadcaster();
  private final StubMetricsSystem metricsSystem =
      new StubMetricsSystem() {
        @Override
        public Set<MetricCategory> getEnabledCategories() {
          return Set.of(LIBP2P_GOSSIP);
        }
      };

  @Test
  void subscriptionInitializesMeshGaugeBeforeAnyPeerJoins() {
    final LibP2PGossipNetwork network = createNetwork(metricsSystem);

    network.subscribe(TOPIC, mock(TopicHandler.class));

    verify(router).subscribe(TOPIC);
    assertThat(meshPeerCount()).isZero();
  }

  @Test
  void subscriptionsAndMeshEventsShareOneGaugePerShape() {
    final LibP2PGossipNetwork network = createNetwork(metricsSystem);
    network.subscribe(TOPIC, mock(TopicHandler.class));
    network.subscribe("/eth2/aabbccdd/beacon_attestation_1/ssz_snappy", mock(TopicHandler.class));
    assertThat(meshPeerCount()).isZero();

    final PeerId peer = PeerId.random();
    broadcaster.notifyMeshed(peer, TOPIC);
    assertThat(meshPeerCount()).isEqualTo(1);

    broadcaster.notifyPruned(peer, TOPIC);
    assertThat(meshPeerCount()).isZero();

    broadcaster.notifyMeshed(peer, TOPIC);
    broadcaster.notifyDisconnected(peer);
    assertThat(meshPeerCount()).isZero();
  }

  @Test
  void subscriptionWorksWithGossipMetricsDisabled() {
    final LibP2PGossipNetwork network = createNetwork(new StubMetricsSystem());

    network.subscribe(TOPIC, mock(TopicHandler.class));

    verify(router).subscribe(TOPIC);
    assertThat(broadcaster.getListeners()).isEmpty();
  }

  private double meshPeerCount() {
    return metricsSystem
        .getLabelledGauge(LIBP2P_GOSSIP, "gossipsub_mesh_peer_counts")
        .getValue("beacon_attestation")
        .orElseThrow();
  }

  private LibP2PGossipNetwork createNetwork(final StubMetricsSystem metrics) {
    when(router.getEventBroadcaster()).thenReturn(broadcaster);
    when(router.getProtocol()).thenReturn(PubsubProtocol.Gossip_V_1_2);
    return new LibP2PGossipNetworkBuilder() {
      @Override
      protected GossipRouter createGossipRouter(
          final GossipConfig gossipConfig,
          final NetworkingSpecConfig networkingSpecConfig,
          final GossipTopicFilter gossipTopicFilter,
          final GossipTopicHandlers topicHandlers) {
        return router;
      }
    }.metricsSystem(metrics)
        .gossipConfig(GossipConfig.builder().build())
        .networkingSpecConfig(mock(NetworkingSpecConfig.class))
        .defaultMessageFactory(mock(PreparedGossipMessageFactory.class))
        .gossipTopicFilter(mock(GossipTopicFilter.class))
        .timeProvider(mock(TimeProvider.class))
        .build();
  }
}

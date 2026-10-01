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

import io.libp2p.core.PeerId;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipTopicScoreParams;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class LibP2PGossipNetwork implements GossipNetwork {

  private static final Logger LOG = LogManager.getLogger();

  static final PubsubRouterMessageValidator STRICT_FIELDS_VALIDATOR = new GossipWireValidator();
  static final Function0<Long> NULL_SEQNO_GENERATOR = () -> null;

  private final MetricsSystem metricsSystem;
  private final Gossip gossip;
  private final PubsubPublisherApi publisher;
  private final GossipTopicHandlers topicHandlers;
  private final Optional<GossipMetricsListener> gossipMetricsListener;

  public LibP2PGossipNetwork(
      final MetricsSystem metricsSystem,
      final Gossip gossip,
      final PubsubPublisherApi publisher,
      final GossipTopicHandlers topicHandlers,
      final Optional<GossipMetricsListener> gossipMetricsListener) {
    this.metricsSystem = metricsSystem;
    this.gossip = gossip;
    this.publisher = publisher;
    this.topicHandlers = topicHandlers;
    this.gossipMetricsListener = gossipMetricsListener;
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return SafeFuture.of(
        publisher.publish(Unpooled.wrappedBuffer(data.toArrayUnsafe()), new Topic(topic)));
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    topicHandlers.add(topic, topicHandler);
    final Topic libP2PTopic = new Topic(topic);
    final GossipHandler gossipHandler =
        new GossipHandler(metricsSystem, libP2PTopic, publisher, topicHandler);
    final PubsubSubscription subscription = gossip.subscribe(gossipHandler, libP2PTopic);
    gossipMetricsListener.ifPresent(listener -> listener.onTopicSubscribed(topic));
    return new LibP2PTopicChannel(gossipHandler, subscription);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    final Map<PeerId, Set<Topic>> peerTopics = gossip.getPeerTopics().join();
    final Map<String, Collection<NodeId>> result = new HashMap<>();
    for (Map.Entry<PeerId, Set<Topic>> peerTopic : peerTopics.entrySet()) {
      final LibP2PNodeId nodeId = new LibP2PNodeId(peerTopic.getKey());
      peerTopic
          .getValue()
          .forEach(
              topic -> result.computeIfAbsent(topic.getTopic(), __ -> new HashSet<>()).add(nodeId));
    }
    return result;
  }

  @Override
  public void updateGossipTopicScoring(final GossipTopicsScoringConfig config) {
    if (config.isEmpty()) {
      return;
    }

    final Map<String, GossipTopicScoreParams> params =
        config.getTopicConfigs().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> LibP2PParamsFactory.createTopicScoreParams(e.getValue())));
    gossip.updateTopicScoreParams(params);
  }

  public Gossip getGossip() {
    return gossip;
  }
}

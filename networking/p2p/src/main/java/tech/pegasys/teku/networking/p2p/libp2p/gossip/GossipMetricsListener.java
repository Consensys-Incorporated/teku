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
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipRouterEventListener;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

/**
 * Publishes gossipsub router internals as metrics.
 *
 * <p>The {@code topic} label is the topic shape, not the topic - see {@link GossipTopicShape}. No
 * metric carries a peer id. This is done to limit the cardinality of metrics.
 *
 * <p>Threading: every {@code notify*} callback runs synchronously on the gossip event thread, so
 * the callbacks must stay cheap and must not block. Gauge suppliers run on the metrics scrape
 * thread instead, so the state they read is held in concurrent collections. Gauge registration can
 * also run on the subscribing thread.
 */
public class GossipMetricsListener implements GossipRouterEventListener {

  private final LabelledMetric<Counter> messagesReceived;
  private final LabelledMetric<Counter> messageBytesReceived;
  private final LabelledMetric<Counter> duplicateMessages;
  private final LabelledMetric<Counter> acceptedMessages;
  private final LabelledMetric<Counter> invalidMessages;
  private final LabelledMetric<Counter> meshPeerInclusionEvents;
  private final LabelledMetric<Counter> meshPeerChurnEvents;
  private final Counter routerMisbehaviourEvents;
  private final Counter slowPeerEvents;

  private final LabelledSuppliedMetric meshPeerCounts;
  private final Map<String, Set<PeerId>> meshPeersByTopic = new ConcurrentHashMap<>();

  /** Topic shapes already registered as a gauge series, so each registers exactly once. */
  private final Map<String, Boolean> registeredMeshGauges = new ConcurrentHashMap<>();

  public GossipMetricsListener(final MetricsSystem metricsSystem) {
    messagesReceived =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_topic_msg_recv_counts",
            "Number of gossip messages received for the first time, by topic",
            "topic");
    messageBytesReceived =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_topic_msg_recv_bytes",
            "Payload bytes of gossip messages received for the first time, by topic",
            "topic");
    duplicateMessages =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_duplicate_messages_total",
            "Number of gossip messages received that were already seen, by topic",
            "topic");
    acceptedMessages =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_accepted_messages_per_topic",
            "Number of first-seen gossip messages that passed validation, by topic",
            "topic");
    invalidMessages =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_invalid_messages_per_topic",
            "Number of first-seen gossip messages that failed validation, by topic",
            "topic");
    meshPeerInclusionEvents =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_mesh_peer_inclusion_events",
            "Number of times a peer was added to a topic mesh, by topic",
            "topic");
    meshPeerChurnEvents =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_mesh_peer_churn_events",
            "Number of times a peer was pruned from a topic mesh, by topic",
            "topic");
    routerMisbehaviourEvents =
        metricsSystem.createCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_router_misbehaviour_events_total",
            "Number of gossip router misbehaviour penalties applied to peers");
    slowPeerEvents =
        metricsSystem.createCounter(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_slow_peer_events_total",
            "Number of times a peer's outbound queue stayed above the slow-peer threshold");
    meshPeerCounts =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P_GOSSIP,
            "gossipsub_mesh_peer_counts",
            "Number of peers currently in the topic mesh, by topic",
            "topic");
  }

  /**
   * Attaches to {@code router} and starts recording, unless {@link
   * TekuMetricCategory#LIBP2P_GOSSIP} is disabled. Won't even attach the listener if the metric
   * category is disabled.
   */
  public static Optional<GossipMetricsListener> attachTo(
      final MetricsSystem metricsSystem, final GossipRouter router) {
    if (!metricsSystem.isCategoryEnabled(TekuMetricCategory.LIBP2P_GOSSIP)) {
      return Optional.empty();
    }
    final GossipMetricsListener listener = new GossipMetricsListener(metricsSystem);
    router.getEventBroadcaster().getListeners().add(listener);
    return Optional.of(listener);
  }

  /** Publishes zero for a subscribed topic shape even if no peer ever joins its mesh. */
  public void onTopicSubscribed(final String topic) {
    registerMeshGauge(GossipTopicShape.of(topic));
  }

  @Override
  public void notifyUnseenMessage(final PeerId peerId, final PubsubMessage msg) {
    final String shape = shapeOf(msg);
    messagesReceived.labels(shape).inc();
    messageBytesReceived.labels(shape).inc(msg.getSize());
  }

  @Override
  public void notifySeenMessage(
      final PeerId peerId, final PubsubMessage msg, final Optional<ValidationResult> ignored) {
    duplicateMessages.labels(shapeOf(msg)).inc();
  }

  @Override
  public void notifyUnseenValidMessage(final PeerId peerId, final PubsubMessage msg) {
    acceptedMessages.labels(shapeOf(msg)).inc();
  }

  @Override
  public void notifyUnseenInvalidMessage(final PeerId peerId, final PubsubMessage msg) {
    invalidMessages.labels(shapeOf(msg)).inc();
  }

  @Override
  public void notifyMeshed(final PeerId peerId, final String topic) {
    meshPeerInclusionEvents.labels(GossipTopicShape.of(topic)).inc();
    meshPeersByTopic.computeIfAbsent(topic, this::newMeshTopic).add(peerId);
  }

  @Override
  public void notifyPruned(final PeerId peerId, final String topic) {
    meshPeerChurnEvents.labels(GossipTopicShape.of(topic)).inc();
    final Set<PeerId> peers = meshPeersByTopic.get(topic);
    if (peers != null) {
      peers.remove(peerId);
    }
  }

  @Override
  public void notifyRouterMisbehavior(final PeerId peerId, final int count) {
    routerMisbehaviourEvents.inc();
  }

  @Override
  public void notifySlowPeer(final PeerId peerId) {
    slowPeerEvents.inc();
  }

  @Override
  public void notifyConnected(final PeerId peerId, final Multiaddr peerAddress) {}

  @Override
  public void notifyDisconnected(final PeerId peerId) {
    // Mirrors GossipRouter.onPeerDisconnected, which removes the peer from every mesh without
    // emitting a prune event.
    meshPeersByTopic.values().forEach(peers -> peers.remove(peerId));
  }

  private Set<PeerId> newMeshTopic(final String topic) {
    registerMeshGauge(GossipTopicShape.of(topic));
    return ConcurrentHashMap.newKeySet();
  }

  private void registerMeshGauge(final String shape) {
    registeredMeshGauges.computeIfAbsent(
        shape,
        newShape -> {
          meshPeerCounts.labels(() -> countMeshPeers(newShape), newShape);
          return Boolean.TRUE;
        });
  }

  private double countMeshPeers(final String shape) {
    int total = 0;
    for (final Map.Entry<String, Set<PeerId>> entry : meshPeersByTopic.entrySet()) {
      if (shape.equals(GossipTopicShape.of(entry.getKey()))) {
        total += entry.getValue().size();
      }
    }
    return total;
  }

  private static String shapeOf(final PubsubMessage msg) {
    final var topics = msg.getTopics();
    return topics.isEmpty() ? GossipTopicShape.OTHER : GossipTopicShape.of(topics.get(0));
  }
}

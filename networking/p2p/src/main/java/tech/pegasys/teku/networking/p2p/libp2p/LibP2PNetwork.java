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

package tech.pegasys.teku.networking.p2p.libp2p;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;

public class LibP2PNetwork implements P2PNetwork<Peer> {

  private static final Logger LOG = LogManager.getLogger();
  static final int REMOTE_OPEN_STREAMS_RATE_LIMIT = 256;
  static final int REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT = 256;
  static final int MAXIMUM_ACTIVE_INBOUND_RPC_STREAMS = 128;

  private final PrivKey privKey;
  private final NodeId nodeId;
  private final Host host;
  private final PeerManager peerManager;
  private final List<Multiaddr> advertisedAddresses;
  private final GossipNetwork gossipNetwork;
  private final List<Integer> listenPorts;
  private final boolean quicEnabled;

  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  public LibP2PNetwork(
      final PrivKey privKey,
      final NodeId nodeId,
      final Host host,
      final PeerManager peerManager,
      final List<Multiaddr> advertisedAddresses,
      final GossipNetwork gossipNetwork,
      final List<Integer> listenPorts,
      final boolean quicEnabled) {
    this.privKey = privKey;
    this.nodeId = nodeId;
    this.host = host;
    this.peerManager = peerManager;
    this.advertisedAddresses = advertisedAddresses;
    this.gossipNetwork = gossipNetwork;
    this.listenPorts = listenPorts;
    this.quicEnabled = quicEnabled;
  }

  @Override
  public SafeFuture<?> start() {
    if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
      return SafeFuture.failedFuture(new IllegalStateException("Network already started"));
    }
    LOG.info("Starting libp2p network...");
    return SafeFuture.of(host.start())
        .thenApply(
            i -> {
              STATUS_LOG.listeningForLibP2P(getNodeAddresses());
              return null;
            });
  }

  @Override
  public List<String> getNodeAddresses() {
    return advertisedAddresses.stream().map(Multiaddr::toString).toList();
  }

  @Override
  public SafeFuture<Peer> connect(final PeerAddress peer) {
    return peer.as(MultiaddrPeerAddress.class)
        .map(staticPeer -> peerManager.connect(staticPeer, host.getNetwork()))
        .orElseGet(
            () ->
                failedFuture(
                    new IllegalArgumentException(
                        "Unsupported peer address: " + peer.getClass().getName())));
  }

  @Override
  public PeerAddress createPeerAddress(final String peerAddress) {
    return MultiaddrPeerAddress.fromAddress(peerAddress);
  }

  @Override
  public Optional<PeerAddress> createPeerAddress(final DiscoveryPeer discoveryPeer) {
    return MultiaddrPeerAddress.fromDiscoveryPeer(discoveryPeer, quicEnabled)
        .map(PeerAddress.class::cast);
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<Peer> subscriber) {
    return peerManager.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    peerManager.unsubscribeConnect(subscriptionId);
  }

  @Override
  public boolean isConnected(final PeerAddress peerAddress) {
    return peerManager.getPeer(peerAddress.getId()).isPresent();
  }

  @Override
  public Bytes getPrivateKey() {
    return Bytes.wrap(privKey.raw());
  }

  @Override
  public Optional<Peer> getPeer(final NodeId id) {
    return peerManager.getPeer(id);
  }

  @Override
  public Stream<Peer> streamPeers() {
    return peerManager.streamPeers();
  }

  @Override
  public NodeId parseNodeId(final String nodeId) {
    return new LibP2PNodeId(PeerId.fromBase58(nodeId));
  }

  @Override
  public int getPeerCount() {
    return peerManager.getPeerCount();
  }

  @Override
  public List<Integer> getListenPorts() {
    return listenPorts;
  }

  @Override
  public SafeFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return SafeFuture.COMPLETE;
    }
    LOG.debug("JvmLibP2PNetwork.stop()");
    return SafeFuture.of(host.stop());
  }

  @Override
  public NodeId getNodeId() {
    return nodeId;
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.empty();
  }

  @Override
  public Optional<UInt256> getDiscoveryNodeId() {
    return Optional.empty();
  }

  @Override
  public Optional<List<String>> getDiscoveryAddresses() {
    return Optional.empty();
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return gossipNetwork.gossip(topic, data);
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    return gossipNetwork.subscribe(topic, topicHandler);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    return gossipNetwork.getSubscribersByTopic();
  }

  @Override
  public void updateGossipTopicScoring(final GossipTopicsScoringConfig config) {
    gossipNetwork.updateGossipTopicScoring(config);
  }

  @FunctionalInterface
  public interface PrivateKeyProvider {
    PrivKey get();
  }

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.empty();
  }
}

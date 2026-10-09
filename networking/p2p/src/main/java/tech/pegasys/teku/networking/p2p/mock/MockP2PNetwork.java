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

package tech.pegasys.teku.networking.p2p.mock;

import io.libp2p.core.PeerId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;

public class MockP2PNetwork<P extends Peer> implements P2PNetwork<P> {
  private final int port = 6000;
  private final NodeId nodeId = new MockNodeId();

  @Override
  public SafeFuture<Peer> connect(final PeerAddress peer) {
    return SafeFuture.failedFuture(new UnsupportedOperationException());
  }

  @Override
  public PeerAddress createPeerAddress(final String peerAddress) {
    return new PeerAddress(new MockNodeId(peerAddress.hashCode()));
  }

  @Override
  public Optional<PeerAddress> createPeerAddress(final DiscoveryPeer discoveryPeer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<P> subscriber) {
    return 0;
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    // Nothing to do
  }

  @Override
  public boolean isConnected(final PeerAddress peerAddress) {
    return false;
  }

  @Override
  public Bytes getPrivateKey() {
    return Bytes.EMPTY;
  }

  @Override
  public Optional<P> getPeer(final NodeId id) {
    return Optional.empty();
  }

  @Override
  public Stream<P> streamPeers() {
    return Stream.empty();
  }

  @Override
  public NodeId parseNodeId(final String nodeId) {
    PeerId peerId = PeerId.fromBase58(nodeId);
    return new MockNodeId(Bytes.wrap(peerId.getBytes()));
  }

  @Override
  public int getPeerCount() {
    return 0;
  }

  @Override
  public List<String> getNodeAddresses() {
    return List.of("/ip4/127.0.0.1/tcp/" + port + "/p2p/" + nodeId.toBase58());
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
  public List<Integer> getListenPorts() {
    return Collections.singletonList(0);
  }

  /** Stops the P2P network layer. */
  @Override
  public SafeFuture<?> stop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    return new MockTopicChannel();
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    // Do nothing
    return SafeFuture.COMPLETE;
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    return Collections.emptyMap();
  }

  @Override
  public void updateGossipTopicScoring(final GossipTopicsScoringConfig config) {}

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.empty();
  }
}

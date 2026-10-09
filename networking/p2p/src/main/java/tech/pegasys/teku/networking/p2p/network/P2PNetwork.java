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

package tech.pegasys.teku.networking.p2p.network;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;

public interface P2PNetwork<T extends Peer> extends GossipNetwork {

  enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  /**
   * Connects to a Peer using a user supplied address. The address format is specific to the network
   * implementation. If a connection already exists for this peer, the future completes with the
   * existing peer.
   *
   * <p>The {@link PeerAddress} must have been created using the {@link #createPeerAddress(String)}
   * method of this same implementation.
   *
   * @param peer Peer to connect to.
   * @return A future which completes when the connection is established, containing the newly
   *     connected peer.
   */
  SafeFuture<Peer> connect(PeerAddress peer);

  /**
   * Parses a peer address in any of this network's supported formats.
   *
   * @param peerAddress the address to parse
   * @return a {@link PeerAddress} which is supported by {@link #connect(PeerAddress)} for
   *     initiating connections
   */
  PeerAddress createPeerAddress(String peerAddress);

  /**
   * Converts a {@link DiscoveryPeer} to a {@link PeerAddress} which can be used with this network's
   * {@link #connect(PeerAddress)} method.
   *
   * @param discoveryPeer the discovery peer to convert
   * @return a {@link PeerAddress} which is supported by {@link #connect(PeerAddress)} for
   *     initiating connections, or empty if the peer advertises neither a QUIC address this network
   *     can dial nor a TCP address
   */
  Optional<PeerAddress> createPeerAddress(DiscoveryPeer discoveryPeer);

  long subscribeConnect(PeerConnectedSubscriber<T> subscriber);

  void unsubscribeConnect(long subscriptionId);

  boolean isConnected(PeerAddress peerAddress);

  Bytes getPrivateKey();

  Optional<T> getPeer(NodeId id);

  Stream<T> streamPeers();

  NodeId parseNodeId(final String nodeId);

  int getPeerCount();

  List<String> getNodeAddresses();

  NodeId getNodeId();

  List<Integer> getListenPorts();

  /**
   * Get the Ethereum Node Record (ENR) for the local node, if one exists.
   *
   * @return the local ENR.
   */
  Optional<String> getEnr();

  Optional<UInt256> getDiscoveryNodeId();

  Optional<List<String>> getDiscoveryAddresses();

  Optional<DiscoveryNetwork<?>> getDiscoveryNetwork();

  /**
   * Starts the P2P network layer.
   *
   * @return
   */
  SafeFuture<?> start();

  /** Stops the P2P network layer. */
  SafeFuture<?> stop();
}

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

package tech.pegasys.teku.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.peer.Eth2PeerWithEnr;
import tech.pegasys.teku.api.provider.Direction;
import tech.pegasys.teku.api.provider.Peer;
import tech.pegasys.teku.api.provider.State;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.node.PeerCountBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class NetworkDataProvider {
  final Function<Optional<UInt256>, Optional<String>> enrLookupFunction;

  private final Eth2P2PNetwork network;

  public NetworkDataProvider(final Eth2P2PNetwork network) {
    this.network = network;
    this.enrLookupFunction =
        maybeNodeId -> {
          if (maybeNodeId.isEmpty()) {
            return Optional.empty();
          }
          final Optional<DiscoveryService> maybeDiscoveryService =
              network.getDiscoveryNetwork().map(DiscoveryNetwork::getDiscoveryService);
          return maybeDiscoveryService.flatMap(
              discoveryService -> discoveryService.lookupEnr(maybeNodeId.get()));
        };
  }

  /**
   * get the Ethereum Node Record of the node
   *
   * @return if discovery is in use, returns the Ethereum Node Record (base64).
   */
  public Optional<String> getEnr() {
    return network.getEnr();
  }

  /**
   * Get the current node
   *
   * @return the node id (base58)
   */
  public String getNodeIdAsBase58() {
    return network.getNodeId().toBase58();
  }

  /**
   * Get the number of Peers
   *
   * @return the the number of peers currently connected to the client
   */
  public long countPeers() {
    return network.streamPeers().count();
  }

  public List<String> getListeningAddresses() {
    return network.getNodeAddresses();
  }

  public List<String> getDiscoveryAddresses() {
    return network.getDiscoveryAddresses().orElseGet(List::of);
  }

  public MetadataMessage getMetadata() {
    return network.getMetadata();
  }

  public List<Peer> getPeers() {
    return network.streamPeers().map(this::toPeer).toList();
  }

  public List<Eth2Peer> getEth2Peers() {
    return network.streamPeers().toList();
  }

  public List<Eth2PeerWithEnr> getEth2PeersWithEnr() {
    return network
        .streamPeers()
        .map(
            eth2Peer ->
                new Eth2PeerWithEnr(
                    eth2Peer, enrLookupFunction.apply(eth2Peer.getDiscoveryNodeId())))
        .toList();
  }

  public PeerCount getPeerCount() {
    long disconnected = 0;
    long connected = 0;

    for (Eth2Peer peer : getEth2Peers()) {
      if (peer.isConnected()) {
        connected++;
      } else {
        disconnected++;
      }
    }

    return new PeerCountBuilder()
        .disconnected(UInt64.valueOf(disconnected))
        .connected(UInt64.valueOf(connected))
        .build();
  }

  public List<Eth2Peer> getPeerScores() {
    return network.streamPeers().toList();
  }

  public Optional<Eth2PeerWithEnr> getEth2PeerById(final String peerId) {
    final NodeId nodeId = network.parseNodeId(peerId);
    return network
        .getPeer(nodeId)
        .map(
            eth2Peer ->
                new Eth2PeerWithEnr(
                    eth2Peer, enrLookupFunction.apply(eth2Peer.getDiscoveryNodeId())));
  }

  private Peer toPeer(final Eth2Peer eth2Peer) {
    final String peerId = eth2Peer.getId().toBase58();
    final String address = eth2Peer.getAddress().toExternalForm();
    final State state = eth2Peer.isConnected() ? State.connected : State.disconnected;
    final Direction direction =
        eth2Peer.connectionInitiatedLocally() ? Direction.outbound : Direction.inbound;

    return new Peer(peerId, null, address, state, direction);
  }

  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return network.getDiscoveryNetwork();
  }
}

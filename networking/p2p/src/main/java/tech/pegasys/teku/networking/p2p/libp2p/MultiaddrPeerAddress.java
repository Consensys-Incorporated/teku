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

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class MultiaddrPeerAddress extends PeerAddress {

  private final Multiaddr multiaddr;
  // Dialed only if a dial to multiaddr fails (e.g. TCP when the peer's QUIC port is unreachable)
  private final Optional<Multiaddr> fallbackMultiaddr;

  protected MultiaddrPeerAddress(final NodeId nodeId, final Multiaddr multiaddr) {
    this(nodeId, multiaddr, Optional.empty());
  }

  protected MultiaddrPeerAddress(
      final NodeId nodeId, final Multiaddr multiaddr, final Optional<Multiaddr> fallbackMultiaddr) {
    super(nodeId);
    this.multiaddr = multiaddr;
    this.fallbackMultiaddr = fallbackMultiaddr;
  }

  @Override
  public String toExternalForm() {
    return multiaddr.toString();
  }

  public static MultiaddrPeerAddress fromAddress(final String address) {
    final Multiaddr multiaddr = Multiaddr.fromString(address);
    return fromMultiaddr(multiaddr);
  }

  public static Optional<MultiaddrPeerAddress> fromDiscoveryPeer(
      final DiscoveryPeer discoveryPeer, final boolean localNodeQuicEnabled) {
    final Optional<Multiaddr> tcpMultiaddr = MultiaddrUtil.fromDiscoveryPeerAsTcp(discoveryPeer);
    return MultiaddrUtil.fromDiscoveryPeer(discoveryPeer, localNodeQuicEnabled)
        .map(
            multiaddr ->
                fromMultiaddr(multiaddr, tcpMultiaddr.filter(tcp -> !tcp.equals(multiaddr))));
  }

  private static MultiaddrPeerAddress fromMultiaddr(final Multiaddr multiaddr) {
    return fromMultiaddr(multiaddr, Optional.empty());
  }

  private static MultiaddrPeerAddress fromMultiaddr(
      final Multiaddr multiaddr, final Optional<Multiaddr> fallbackMultiaddr) {
    final PeerId peerId = multiaddr.getPeerId();
    if (peerId == null) {
      throw new IllegalArgumentException("No peer ID present in multiaddr: " + multiaddr);
    }
    final LibP2PNodeId nodeId = new LibP2PNodeId(peerId);
    return new MultiaddrPeerAddress(nodeId, multiaddr, fallbackMultiaddr);
  }

  public Multiaddr getMultiaddr() {
    return multiaddr;
  }

  public Optional<Multiaddr> getFallbackMultiaddr() {
    return fallbackMultiaddr;
  }

  @Override
  public String toString() {
    return getId() + " (" + getMultiaddr() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final MultiaddrPeerAddress that = (MultiaddrPeerAddress) o;
    return Objects.equals(multiaddr, that.multiaddr)
        && Objects.equals(fallbackMultiaddr, that.fallbackMultiaddr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), multiaddr, fallbackMultiaddr);
  }
}

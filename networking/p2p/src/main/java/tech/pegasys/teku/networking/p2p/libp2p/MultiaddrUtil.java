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

import static io.libp2p.crypto.keys.Secp256k1Kt.unmarshalSecp256k1PublicKey;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class MultiaddrUtil {

  public static Multiaddr fromDiscoveryPeer(
      final DiscoveryPeer peer, final boolean localNodeQuicEnabled) {
    final NodeId nodeId = getNodeId(peer);
    // Only dial a peer over QUIC if this node has the QUIC transport enabled, otherwise we have no
    // QuicTransport to perform the dial and would fail even though the peer also advertises TCP.
    if (localNodeQuicEnabled) {
      return peer.getQuicAddress()
          .map(quicAddr -> fromInetSocketAddressAsQuic(quicAddr, nodeId))
          .orElseGet(() -> fromDiscoveryPeerAsTcp(peer));
    }
    return fromDiscoveryPeerAsTcp(peer);
  }

  public static Multiaddr fromDiscoveryPeerAsTcp(final DiscoveryPeer peer) {
    return fromInetSocketAddress(peer.getNodeAddress(), getNodeId(peer));
  }

  public static Multiaddr fromDiscoveryPeerAsUdp(final DiscoveryPeer peer) {
    return addPeerId(fromInetSocketAddress(peer.getNodeAddress(), "udp"), getNodeId(peer));
  }

  static Multiaddr fromInetSocketAddress(final InetSocketAddress address) {
    return fromInetSocketAddress(address, "tcp");
  }

  static Multiaddr fromInetSocketAddressAsQuic(final InetSocketAddress address) {
    final String addrString =
        String.format(
            "/%s/%s/udp/%d/quic-v1",
            protocol(address.getAddress()),
            address.getAddress().getHostAddress(),
            address.getPort());
    return Multiaddr.fromString(addrString);
  }

  static Multiaddr fromInetSocketAddress(final InetSocketAddress address, final String protocol) {
    final String addrString =
        String.format(
            "/%s/%s/%s/%d",
            protocol(address.getAddress()),
            address.getAddress().getHostAddress(),
            protocol,
            address.getPort());
    return Multiaddr.fromString(addrString);
  }

  public static Multiaddr fromInetSocketAddress(
      final InetSocketAddress address, final NodeId nodeId) {
    return addPeerId(fromInetSocketAddress(address, "tcp"), nodeId);
  }

  public static Multiaddr fromInetSocketAddressAsQuic(
      final InetSocketAddress address, final NodeId nodeId) {
    return addPeerId(fromInetSocketAddressAsQuic(address), nodeId);
  }

  private static Multiaddr addPeerId(final Multiaddr addr, final NodeId nodeId) {
    return addr.withP2P(PeerId.fromBase58(nodeId.toBase58()));
  }

  private static NodeId getNodeId(final DiscoveryPeer peer) {
    final PubKey pubKey = unmarshalSecp256k1PublicKey(peer.getPublicKey().toArrayUnsafe());
    return new LibP2PNodeId(PeerId.fromPubKey(pubKey));
  }

  private static String protocol(final InetAddress address) {
    return address instanceof Inet6Address ? "ip6" : "ip4";
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.MultiaddrComponent;
import io.libp2p.core.multiformats.Protocol;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class MultiaddrUtilTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final SchemaDefinitions SCHEMA_DEFINITIONS = SPEC.getGenesisSchemaDefinitions();
  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x0330FC08314CDD799C1687FFC998249A0342105B9AF300A922F56040DF6E28741C");
  public static final String PEER_ID = "16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5";
  private static final NodeId NODE_ID = new LibP2PNodeId(PeerId.fromBase58(PEER_ID));
  private static final Optional<EnrForkId> ENR_FORK_ID = Optional.empty();
  private static final SszBitvector PERSISTENT_ATTESTATION_SUBNETS =
      SCHEMA_DEFINITIONS.getAttnetsENRFieldSchema().getDefault();
  private static final SszBitvector SYNC_COMMITTEE_SUBNETS =
      SCHEMA_DEFINITIONS.getSyncnetsENRFieldSchema().getDefault();

  @Test
  public void fromInetSocketAddress_shouldConvertIpV4Peer() throws Exception {
    final byte[] ipAddress = {123, 34, 58, 22};
    final int port = 5883;
    final InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(ipAddress), port);
    final Multiaddr result = MultiaddrUtil.fromInetSocketAddress(address);
    assertThat(result).isEqualTo(Multiaddr.fromString("/ip4/123.34.58.22/tcp/5883/"));
    assertThatComponent(result, Protocol.IP4).isEqualTo(ipAddress);
    assertThatComponent(result, Protocol.TCP).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThat(result.getFirstComponent(Protocol.P2P)).isNull();
  }

  @Test
  public void fromInetSocketAddress_shouldConvertIpV6Peer() throws Exception {
    final byte[] ipAddress = Bytes.fromHexString("0x33000004500007800000001200000001").toArray();
    final int port = 5883;
    final InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(ipAddress), port);
    final Multiaddr result = MultiaddrUtil.fromInetSocketAddress(address);
    assertThat(result).isEqualTo(Multiaddr.fromString("/ip6/3300:4:5000:780:0:12:0:1/tcp/5883/"));
    assertThatComponent(result, Protocol.IP6).isEqualTo(ipAddress);
    assertThatComponent(result, Protocol.TCP).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThat(result.getFirstComponent(Protocol.P2P)).isNull();
  }

  @Test
  public void fromDiscoveryPeer_shouldConvertIpV4Peer() throws Exception {
    final byte[] ipAddress = {123, 34, 58, 22};
    final int port = 5883;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            PUB_KEY,
            Bytes32.ZERO,
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), port)),
            Optional.empty(),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    final Multiaddr result = MultiaddrUtil.fromDiscoveryPeer(peer, true).orElseThrow();
    assertThat(result).isEqualTo(Multiaddr.fromString("/ip4/123.34.58.22/tcp/5883/p2p/" + PEER_ID));
    assertThatComponent(result, Protocol.IP4).isEqualTo(ipAddress);
    assertThatComponent(result, Protocol.TCP).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThatComponent(result, Protocol.P2P).isEqualTo(NODE_ID.toBytes().toArrayUnsafe());
  }

  @Test
  public void fromDiscoveryPeer_shouldConvertIpV6Peer() throws Exception {
    final byte[] ipAddress = Bytes.fromHexString("0x33000004500007800000001200000001").toArray();
    final int port = 5883;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            PUB_KEY,
            Bytes32.ZERO,
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), port)),
            Optional.empty(),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    final Multiaddr result = MultiaddrUtil.fromDiscoveryPeer(peer, true).orElseThrow();
    assertThat(result)
        .isEqualTo(Multiaddr.fromString("/ip6/3300:4:5000:780:0:12:0:1/tcp/5883/p2p/" + PEER_ID));
    assertThatComponent(result, Protocol.IP6).isEqualTo(ipAddress);
    assertThatComponent(result, Protocol.TCP).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThatComponent(result, Protocol.P2P).isEqualTo(NODE_ID.toBytes().toArrayUnsafe());
  }

  @Test
  public void fromDiscoveryPeer_shouldConvertRealPeer() throws Exception {
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            Bytes.fromHexString(
                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
            Bytes32.ZERO,
            Optional.of(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000)),
            Optional.empty(),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    final Multiaddr expectedMultiAddr =
        Multiaddr.fromString(
            "/ip4/127.0.0.1/tcp/9000/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF");
    assertThat(MultiaddrUtil.fromDiscoveryPeer(peer, true).orElseThrow())
        .isEqualTo(expectedMultiAddr);
  }

  @Test
  public void fromDiscoveryPeer_shouldPreferQuicWhenQuicAddressIsPresent() throws Exception {
    final byte[] ipAddress = {127, 0, 0, 1};
    final int tcpPort = 9000;
    final int quicPort = 9100;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            Bytes.fromHexString(
                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
            Bytes32.ZERO,
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), tcpPort)),
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), quicPort)),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    final Multiaddr result = MultiaddrUtil.fromDiscoveryPeer(peer, true).orElseThrow();
    assertThat(result)
        .isEqualTo(
            Multiaddr.fromString(
                "/ip4/127.0.0.1/udp/9100/quic-v1/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF"));
  }

  @Test
  public void fromDiscoveryPeer_shouldNotUseQuicWhenLocalNodeQuicDisabled() throws Exception {
    final byte[] ipAddress = {127, 0, 0, 1};
    final int tcpPort = 9000;
    final int quicPort = 9100;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            Bytes.fromHexString(
                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
            Bytes32.ZERO,
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), tcpPort)),
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), quicPort)),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    // The peer advertises QUIC, but this node has QUIC disabled so it must dial over TCP.
    final Multiaddr result = MultiaddrUtil.fromDiscoveryPeer(peer, false).orElseThrow();
    assertThat(result)
        .isEqualTo(
            Multiaddr.fromString(
                "/ip4/127.0.0.1/tcp/9000/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF"));
  }

  @Test
  public void fromDiscoveryPeer_shouldFallBackToTcpWhenNoQuicAddress() throws Exception {
    final byte[] ipAddress = {127, 0, 0, 1};
    final int tcpPort = 9000;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            Bytes.fromHexString(
                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
            Bytes32.ZERO,
            Optional.of(new InetSocketAddress(InetAddress.getByAddress(ipAddress), tcpPort)),
            Optional.empty(),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    final Multiaddr result = MultiaddrUtil.fromDiscoveryPeer(peer, true).orElseThrow();
    assertThat(result)
        .isEqualTo(
            Multiaddr.fromString(
                "/ip4/127.0.0.1/tcp/9000/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF"));
  }

  @Test
  public void fromDiscoveryPeer_shouldReturnEmptyForQuicOnlyPeerWhenLocalNodeQuicDisabled()
      throws Exception {
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            Bytes.fromHexString(
                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
            Bytes32.ZERO,
            Optional.empty(),
            Optional.of(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9100)),
            ENR_FORK_ID,
            PERSISTENT_ATTESTATION_SUBNETS,
            SYNC_COMMITTEE_SUBNETS,
            Optional.empty(),
            Optional.empty());
    // The peer can only be reached over QUIC, which this node has disabled.
    assertThat(MultiaddrUtil.fromDiscoveryPeer(peer, false)).isEmpty();
  }

  @Test
  public void fromUdpAddress_shouldConvertAddressAndPublicKey() throws Exception {
    final Multiaddr expectedMultiAddr =
        Multiaddr.fromString(
            "/ip4/127.0.0.1/udp/9000/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF");
    assertThat(
            MultiaddrUtil.fromUdpAddress(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000),
                Bytes.fromHexString(
                    "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194")))
        .isEqualTo(expectedMultiAddr);
  }

  private AbstractObjectAssert<?, byte[]> assertThatComponent(
      final Multiaddr result, final Protocol p2p) {
    return assertThat(result.getFirstComponent(p2p)).extracting(MultiaddrComponent::getValue);
  }
}

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

import io.libp2p.core.multiformats.Multiaddr;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class MultiaddrPeerAddressTest {

  private static final Spec SPEC = TestSpecFactory.createDefault();
  private static final SchemaDefinitions SCHEMA_DEFINITIONS = SPEC.getGenesisSchemaDefinitions();
  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194");
  private static final String PEER_ID = "16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF";
  private static final Multiaddr TCP_MULTIADDR =
      Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000/p2p/" + PEER_ID);
  private static final Multiaddr QUIC_MULTIADDR =
      Multiaddr.fromString("/ip4/127.0.0.1/udp/9100/quic-v1/p2p/" + PEER_ID);

  @Test
  public void fromDiscoveryPeer_shouldFallBackToTcpWhenDialingQuic() throws Exception {
    final MultiaddrPeerAddress result =
        MultiaddrPeerAddress.fromDiscoveryPeer(createPeer(Optional.of(9100)), true).orElseThrow();
    assertThat(result.getMultiaddr()).isEqualTo(QUIC_MULTIADDR);
    assertThat(result.getFallbackMultiaddr()).contains(TCP_MULTIADDR);
  }

  @Test
  public void fromDiscoveryPeer_shouldHaveNoFallbackWhenPeerHasNoQuicAddress() throws Exception {
    final MultiaddrPeerAddress result =
        MultiaddrPeerAddress.fromDiscoveryPeer(createPeer(Optional.empty()), true).orElseThrow();
    assertThat(result.getMultiaddr()).isEqualTo(TCP_MULTIADDR);
    assertThat(result.getFallbackMultiaddr()).isEmpty();
  }

  @Test
  public void fromDiscoveryPeer_shouldHaveNoFallbackWhenLocalNodeQuicDisabled() throws Exception {
    final MultiaddrPeerAddress result =
        MultiaddrPeerAddress.fromDiscoveryPeer(createPeer(Optional.of(9100)), false).orElseThrow();
    assertThat(result.getMultiaddr()).isEqualTo(TCP_MULTIADDR);
    assertThat(result.getFallbackMultiaddr()).isEmpty();
  }

  @Test
  public void fromDiscoveryPeer_shouldDialQuicWithoutFallbackWhenPeerHasNoTcpAddress()
      throws Exception {
    final MultiaddrPeerAddress result =
        MultiaddrPeerAddress.fromDiscoveryPeer(
                createPeer(Optional.empty(), Optional.of(9100)), true)
            .orElseThrow();
    assertThat(result.getMultiaddr()).isEqualTo(QUIC_MULTIADDR);
    assertThat(result.getFallbackMultiaddr()).isEmpty();
  }

  @Test
  public void fromDiscoveryPeer_shouldBeEmptyWhenLocalNodeCannotDialQuicOnlyPeer()
      throws Exception {
    assertThat(
            MultiaddrPeerAddress.fromDiscoveryPeer(
                createPeer(Optional.empty(), Optional.of(9100)), false))
        .isEmpty();
  }

  @Test
  public void fromAddress_shouldHaveNoFallback() {
    final MultiaddrPeerAddress result = MultiaddrPeerAddress.fromAddress(QUIC_MULTIADDR.toString());
    assertThat(result.getMultiaddr()).isEqualTo(QUIC_MULTIADDR);
    assertThat(result.getFallbackMultiaddr()).isEmpty();
  }

  private static DiscoveryPeer createPeer(final Optional<Integer> quicPort) throws Exception {
    return createPeer(Optional.of(9000), quicPort);
  }

  private static DiscoveryPeer createPeer(
      final Optional<Integer> tcpPort, final Optional<Integer> quicPort) throws Exception {
    final InetAddress ip = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    return new DiscoveryPeer(
        PUB_KEY,
        Bytes32.ZERO,
        tcpPort.map(port -> new InetSocketAddress(ip, port)),
        quicPort.map(port -> new InetSocketAddress(ip, port)),
        Optional.empty(),
        SCHEMA_DEFINITIONS.getAttnetsENRFieldSchema().getDefault(),
        SCHEMA_DEFINITIONS.getSyncnetsENRFieldSchema().getDefault(),
        Optional.empty(),
        Optional.empty());
  }
}

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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.multiformats.Multiaddr;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.DefaultReputationManager;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class LibP2PNetworkBuilderTest {

  // Defaults from NetworkConfig: TCP 9000/9090 (v4/v6), QUIC 9001/9091 (v4/v6)
  private static final NodeId NODE_ID =
      new LibP2PNodeId(PeerId.fromBase58("16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5"));

  private NetworkConfig.Builder singleStackConfig() {
    return NetworkConfig.builder()
        .networkInterface("127.0.0.1")
        .advertisedIp(Optional.of("127.0.0.1"));
  }

  private NetworkConfig.Builder dualStackConfig() {
    return NetworkConfig.builder()
        .networkInterfaces(List.of("127.0.0.1", "::1"))
        .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")));
  }

  // ---- listenPorts ----

  @Test
  void buildListenPorts_singleStackTcpOnly() {
    final NetworkConfig config = singleStackConfig().quicEnabled(false).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000);
  }

  @Test
  void buildListenPorts_singleStackTcpAndQuic() {
    final NetworkConfig config = singleStackConfig().build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000, 9001);
  }

  @Test
  void buildListenPorts_singleStackQuicOnly() {
    // Regression: single-IP QUIC must not report the IPv6 ports.
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9001);
  }

  @Test
  void buildListenPorts_dualStackTcpOnly() {
    final NetworkConfig config = dualStackConfig().quicEnabled(false).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000, 9090);
  }

  @Test
  void buildListenPorts_dualStackSameTcpPortUsesSingleDualStackTcpSocket() {
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .listenPort(9000)
            .listenPortIpv6(9000)
            .quicEnabled(false)
            .build();

    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000);
  }

  @Test
  void buildListenPorts_dualStackTcpAndQuic() {
    // Regression: dual-stack QUIC must report all four ports, not just two.
    final NetworkConfig config = dualStackConfig().build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config))
        .containsExactly(9000, 9001, 9090, 9091);
  }

  // ---- advertised addresses ----

  @Test
  void buildAdvertisedAddresses_quicEnabledTcpDisabled_doesNotAdvertiseTcp() {
    // Regression: a disabled transport must not be advertised to peers (e.g. in the ENR).
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(1);
    assertThat(addresses.get(0)).contains("/udp/9001/quic-v1");
    assertThat(addresses).noneMatch(addr -> addr.contains("/tcp/"));
  }

  @Test
  void buildAdvertisedAddresses_tcpAndQuicEnabled_advertisesBoth() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(2);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
  }

  @Test
  void buildAdvertisedAddresses_dualStackTcpAndQuic_advertisesAllFour() {
    final NetworkConfig config = dualStackConfig().quicEnabled(true).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(4);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9090"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9091/quic-v1"));
  }

  // ---- listen addresses ----

  @Test
  void buildListenAddresses_quicEnabledTcpDisabled_doesNotListenOnTcp() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    final List<String> addresses = List.of(LibP2PNetworkBuilder.buildListenAddresses(config));
    assertThat(addresses).hasSize(1);
    assertThat(addresses.get(0)).contains("/udp/9001/quic-v1");
    assertThat(addresses).noneMatch(addr -> addr.contains("/tcp/"));
  }

  @Test
  void buildListenAddresses_tcpAndQuicEnabled_listensOnBoth() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    final List<String> addresses = List.of(LibP2PNetworkBuilder.buildListenAddresses(config));
    assertThat(addresses).hasSize(2);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
  }

  @Test
  void buildListenAddresses_dualStackSameTcpPortUsesIpv6WildcardTcpSocket() {
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .listenPort(9000)
            .listenPortIpv6(9000)
            .quicEnabled(false)
            .build();

    assertThat(List.of(LibP2PNetworkBuilder.buildListenAddresses(config)))
        .containsExactly("/ip6/::/tcp/9000");
  }

  @Test
  void buildListenAddresses_dualStackSpecificIpv4SameTcpPortUsesIpv6WildcardTcpSocket() {
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("127.0.0.1", "::"))
            .listenPort(9000)
            .listenPortIpv6(9000)
            .quicEnabled(false)
            .build();

    assertThat(List.of(LibP2PNetworkBuilder.buildListenAddresses(config)))
        .containsExactly("/ip6/::/tcp/9000");
  }

  @Test
  void start_dualStackSameTcpPortDoesNotBindTcpPortTwice() throws Exception {
    Assumptions.assumeTrue(canBindIpv6Wildcard());
    final int listenPort = findAvailablePort();
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .listenPort(listenPort)
            .listenPortIpv6(listenPort)
            .quicEnabled(false)
            .build();
    final P2PNetwork<Peer> network = createNetwork(config);

    try {
      assertThatCode(() -> Waiter.waitFor(network.start())).doesNotThrowAnyException();
    } finally {
      Waiter.waitFor(network.stop());
    }
  }

  @Test
  void start_dualStackSpecificIpv4SameTcpPortDoesNotBindTcpPortTwice() throws Exception {
    Assumptions.assumeTrue(canBindIpv6Wildcard());
    final int listenPort = findAvailablePort();
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("127.0.0.1", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .listenPort(listenPort)
            .listenPortIpv6(listenPort)
            .quicEnabled(false)
            .build();
    final P2PNetwork<Peer> network = createNetwork(config);

    try {
      assertThatCode(() -> Waiter.waitFor(network.start())).doesNotThrowAnyException();
    } finally {
      Waiter.waitFor(network.stop());
    }
  }

  @Test
  void createPeerAddress_quicOnlyNetworkHasNoAddressForTcpOnlyPeer() throws Exception {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    final P2PNetwork<Peer> network = createNetwork(config);

    assertThat(network.createPeerAddress(tcpOnlyDiscoveryPeer())).isEmpty();
  }

  @Test
  void createPeerAddress_tcpAndQuicNetworkDialsTcpOnlyPeerOverTcp() throws Exception {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    final P2PNetwork<Peer> network = createNetwork(config);

    assertThat(network.createPeerAddress(tcpOnlyDiscoveryPeer()))
        .map(PeerAddress::toExternalForm)
        .contains(
            "/ip4/127.0.0.1/tcp/9000/p2p/16Uiu2HAmR4wQRGWgCNy5uzx7HfuV59Q6X1MVzBRmvreuHgEQcCnF");
  }

  private static DiscoveryPeer tcpOnlyDiscoveryPeer() throws Exception {
    final SchemaDefinitions schemaDefinitions =
        TestSpecFactory.createMinimalPhase0().getGenesisSchemaDefinitions();
    return new DiscoveryPeer(
        Bytes.fromHexString("0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
        Bytes32.ZERO,
        Optional.of(
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000)),
        Optional.empty(),
        Optional.empty(),
        schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
        schemaDefinitions.getSyncnetsENRFieldSchema().getDefault(),
        Optional.empty(),
        Optional.empty());
  }

  private static List<String> asStrings(final List<Multiaddr> addresses) {
    return addresses.stream().map(Multiaddr::toString).toList();
  }

  private static P2PNetwork<Peer> createNetwork(final NetworkConfig config) {
    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
    final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
    final PeerPools peerPools = new PeerPools();
    return LibP2PNetworkBuilder.create()
        .asyncRunner(DelayedExecutorAsyncRunner.create())
        .config(config)
        .networkingSpecConfig(TestSpecFactory.createMinimalPhase0().getNetworkingConfig())
        .privateKeyProvider(() -> KeyKt.generateKeyPair(KeyType.SECP256K1).component1())
        .reputationManager(
            new DefaultReputationManager(
                metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY, peerPools))
        .metricsSystem(metricsSystem)
        .rpcMethods(List.of())
        .peerHandlers(List.of())
        .preparedGossipMessageFactory(
            (topic, payload, networkingSpecConfig, arrivalTimestamp) -> {
              throw new UnsupportedOperationException();
            })
        .gossipTopicFilter(topic -> true)
        .timeProvider(timeProvider)
        .build();
  }

  private static int findAvailablePort() throws IOException {
    try (final ServerSocket serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    }
  }

  private static boolean canBindIpv6Wildcard() {
    try (final ServerSocket serverSocket = new ServerSocket()) {
      serverSocket.bind(new InetSocketAddress(InetAddress.getByName("::"), 0));
      return true;
    } catch (final IOException ex) {
      return false;
    }
  }
}

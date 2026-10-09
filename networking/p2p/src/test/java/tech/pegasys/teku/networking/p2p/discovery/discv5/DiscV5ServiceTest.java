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

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.multiformats.Multiaddr;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver.IPVersion;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

class DiscV5ServiceTest {

  @Test
  void dualStackSameUdpPortUsesSingleIpv6WildcardListenAddress() {
    final CapturingDiscoverySystemBuilder discoverySystemBuilder =
        new CapturingDiscoverySystemBuilder();
    final DiscoveryConfig discoveryConfig =
        DiscoveryConfig.builder().listenUdpPort(9000).listenUdpPortIpv6(9000).build();
    final NetworkConfig networkConfig =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .build();

    createService(discoveryConfig, networkConfig, discoverySystemBuilder);

    assertThat(discoverySystemBuilder.listenAddresses).hasSize(1);
    assertThat(discoverySystemBuilder.listenAddresses[0].getPort()).isEqualTo(9000);
    assertThat(discoverySystemBuilder.listenAddresses[0].getAddress().isAnyLocalAddress()).isTrue();
    assertThat(IPVersionResolver.resolve(discoverySystemBuilder.listenAddresses[0]))
        .isEqualTo(IPVersion.IP_V6);
  }

  @Test
  void dualStackSpecificIpv4SameUdpPortUsesSingleIpv6WildcardListenAddress() {
    final CapturingDiscoverySystemBuilder discoverySystemBuilder =
        new CapturingDiscoverySystemBuilder();
    final DiscoveryConfig discoveryConfig =
        DiscoveryConfig.builder().listenUdpPort(9000).listenUdpPortIpv6(9000).build();
    final NetworkConfig networkConfig =
        NetworkConfig.builder()
            .networkInterfaces(List.of("127.0.0.1", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .build();

    createService(discoveryConfig, networkConfig, discoverySystemBuilder);

    assertThat(discoverySystemBuilder.listenAddresses).hasSize(1);
    assertThat(discoverySystemBuilder.listenAddresses[0].getPort()).isEqualTo(9000);
    assertThat(discoverySystemBuilder.listenAddresses[0].getAddress().isAnyLocalAddress()).isTrue();
    assertThat(IPVersionResolver.resolve(discoverySystemBuilder.listenAddresses[0]))
        .isEqualTo(IPVersion.IP_V6);
  }

  @Test
  void shouldSkipMalformedBootnodeWithoutFailingStartup() {
    final DiscoveryConfig discoveryConfig =
        DiscoveryConfig.builder()
            .listenUdpPort(9000)
            .listenUdpPortIpv6(9000)
            .bootnodes(List.of("enr:-not-valid-base64!!!"))
            .build();
    final NetworkConfig networkConfig =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .build();

    assertThatCode(
            () ->
                createService(
                    discoveryConfig, networkConfig, new CapturingDiscoverySystemBuilder()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldReportUdpDiscoveryAddressForEachAdvertisedIp() {
    final PrivKey privKey = KeyKt.generateKeyPair(KeyType.SECP256K1).component1();
    final String peerId = PeerId.fromPubKey(privKey.publicKey()).toBase58();
    final DiscoveryConfig discoveryConfig =
        DiscoveryConfig.builder().listenUdpPort(9000).listenUdpPortIpv6(9001).build();
    final NetworkConfig networkConfig =
        NetworkConfig.builder()
            .networkInterfaces(List.of("0.0.0.0", "::"))
            .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")))
            .build();

    final DiscV5Service service =
        createService(
            discoveryConfig,
            networkConfig,
            new CapturingDiscoverySystemBuilder(),
            Bytes.wrap(privKey.raw()));

    assertThat(service.getDiscoveryAddresses().orElseThrow())
        .map(Multiaddr::fromString)
        .containsExactly(
            Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/p2p/" + peerId),
            Multiaddr.fromString("/ip6/::1/udp/9001/p2p/" + peerId));
  }

  private static void createService(
      final DiscoveryConfig discoveryConfig,
      final NetworkConfig networkConfig,
      final DiscoverySystemBuilder discoverySystemBuilder) {
    createService(
        discoveryConfig,
        networkConfig,
        discoverySystemBuilder,
        Bytes.wrap(KeyKt.generateKeyPair(KeyType.SECP256K1).component1().raw()));
  }

  private static DiscV5Service createService(
      final DiscoveryConfig discoveryConfig,
      final NetworkConfig networkConfig,
      final DiscoverySystemBuilder discoverySystemBuilder,
      final Bytes privateKey) {
    final Spec spec = TestSpecFactory.createMinimalPhase0();

    return new DiscV5Service(
        new NoOpMetricsSystem(),
        DelayedExecutorAsyncRunner.create(),
        discoveryConfig,
        networkConfig,
        new MemKeyValueStore<>(),
        privateKey,
        spec::getGenesisSchemaDefinitions,
        Optional.empty(),
        discoverySystemBuilder,
        DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER);
  }

  private static class CapturingDiscoverySystemBuilder extends DiscoverySystemBuilder {
    private InetSocketAddress[] listenAddresses = new InetSocketAddress[0];

    @Override
    public DiscoverySystemBuilder listen(final InetSocketAddress... listenAddresses) {
      this.listenAddresses = listenAddresses;
      return super.listen(listenAddresses);
    }
  }
}

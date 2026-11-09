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

package tech.pegasys.teku.networking.p2p.network.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.libp2p.core.multiformats.Multiaddr;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class NetworkConfigTest {

  private Optional<String> advertisedIp = Optional.empty();
  private String listenIp = "0.0.0.0";

  @Test
  void getAdvertisedIps_shouldUseAdvertisedAddressWhenSet() {
    final String expected = "1.2.3.4";
    advertisedIp = Optional.of(expected);
    assertThat(createConfig().getAdvertisedIps()).containsExactly(expected);
  }

  @Test
  void getAdvertisedIps_shouldResolveAnyLocalAdvertisedAddress() {
    advertisedIp = Optional.of("0.0.0.0");
    assertThat(createConfig().getAdvertisedIps()).hasSize(1).doesNotContain("0.0.0.0");
  }

  @Test
  void getAdvertisedIps_shouldReturnInterfaceIpWhenNotSet() {
    listenIp = "127.0.0.1";
    assertThat(createConfig().getAdvertisedIps()).containsExactly(listenIp);
  }

  @Test
  void getAdvertisedIps_shouldResolveLocalhostIpWhenInterfaceIpIsAnyLocal() {
    listenIp = "0.0.0.0";
    assertThat(createConfig().getAdvertisedIps()).hasSize(1).doesNotContain("0.0.0.0");
  }

  @Test
  void getAdvertisedIps_shouldResolveLocalhostIpWhenInterfaceIpIsAnyLocalIpv6() {
    listenIp = "::0";
    final List<String> result = createConfig().getAdvertisedIps();
    assertThat(result)
        .hasSize(1)
        .first()
        .asString()
        .isNotBlank()
        .satisfies(
            ip -> {
              // check the advertised IP is IPv6
              assertThat(InetAddress.getByName(ip)).isInstanceOf(Inet6Address.class);
            });
  }

  @Test
  void checkPrivateKeySourceCreatedCorrectly() {
    final NetworkConfig config =
        NetworkConfig.builder()
            .advertisedIp(advertisedIp)
            .networkInterface(listenIp)
            .privateKeyFile("file.txt")
            .build();
    final Optional<PrivateKeySource> source = config.getPrivateKeySource();
    final PrivateKeySource expected = new GeneratingFilePrivateKeySource("file.txt");

    assertThat(source).isPresent();
    assertThat(source).contains(expected);
  }

  private NetworkConfig createConfig() {
    return NetworkConfig.builder().advertisedIp(advertisedIp).networkInterface(listenIp).build();
  }

  @Test
  void checkDirectPeersConfigCreatedCorrectly() {
    final String peerAddress1 =
        "/ip4/198.51.100.0/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N";
    final String peerAddress2 =
        "/ip4/198.51.100.0/tcp/4242/p2p/QmTESTo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjTEST";
    final LibP2PNodeId peerId1 = new LibP2PNodeId(new Multiaddr(peerAddress1).getPeerId());
    final LibP2PNodeId peerId2 = new LibP2PNodeId(new Multiaddr(peerAddress2).getPeerId());

    final List<NodeId> directPeers = List.of(peerId1);

    final NetworkConfig config =
        NetworkConfig.builder()
            .advertisedIp(advertisedIp)
            .networkInterface(listenIp)
            .directPeers(directPeers)
            .build();

    final Optional<DirectPeerManager> optionalDirectPeerManager =
        config.getGossipConfig().getScoringConfig().getPeerScoringConfig().getDirectPeerManager();
    assertThat(optionalDirectPeerManager).isPresent();

    final DirectPeerManager manager = optionalDirectPeerManager.get();

    assertThat(manager.isDirectPeer(peerId1)).isTrue();
    assertThat(manager.isDirectPeer(peerId2)).isFalse();
  }

  @Test
  void checkSetBothIPv4andIPv6() {
    final NetworkConfig config =
        NetworkConfig.builder()
            .networkInterfaces(List.of("192.0.2.146", "2a01:4b00:875c:9500:d55c:71df:3af7:9f1f"))
            .advertisedIps(
                Optional.of(List.of("1.2.3.4", "2001:db8:3333:4444:5555:6666:7777:8888")))
            .build();

    assertThat(config.getNetworkInterfaces())
        .containsExactly("192.0.2.146", "2a01:4b00:875c:9500:d55c:71df:3af7:9f1f");
    assertThat(config.getAdvertisedIps())
        .containsExactly("1.2.3.4", "2001:db8:3333:4444:5555:6666:7777:8888");
  }

  @Test
  void failsIfInvalidNumberOfNetworkInterfacesAreSet() {
    assertThatThrownBy(
            () ->
                NetworkConfig.builder()
                    .networkInterfaces(List.of("0.0.0.0", "::", "1.2.3.4"))
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid number of --p2p-interface. It should be either 1 or 2, but it was 3");
  }

  @Test
  void failsIfTwoIPv4NetworkInterfacesAreSet() {
    assertThatThrownBy(
            () -> NetworkConfig.builder().networkInterfaces(List.of("0.0.0.0", "1.2.3.4")).build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage(
            "Expected an IPv4 and an IPv6 address for --p2p-interface but only [IP_V4] was set");
  }

  @Test
  void failsIfTwoIPv6AdvertisedIpsAreSet() {
    assertThatThrownBy(
            () ->
                NetworkConfig.builder()
                    .advertisedIps(
                        Optional.of(List.of("::", "2001:db8:3333:4444:5555:6666:7777:8888")))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage(
            "Expected an IPv4 and an IPv6 address for --p2p-advertised-ip but only [IP_V6] was set");
  }
}

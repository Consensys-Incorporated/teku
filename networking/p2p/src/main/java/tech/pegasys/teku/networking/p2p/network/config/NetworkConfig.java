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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.InetAddresses.isInetAddress;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver.IPVersion;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class NetworkConfig {

  private static final Logger LOG = LogManager.getLogger();

  public static final List<String> DEFAULT_P2P_INTERFACE = List.of("0.0.0.0");
  public static final int DEFAULT_P2P_PORT = 9000;
  public static final int DEFAULT_P2P_QUIC_PORT = 9001;
  public static final int DEFAULT_P2P_PORT_IPV6 = 9090;
  public static final int DEFAULT_P2P_QUIC_PORT_IPV6 = 9091;
  public static final boolean DEFAULT_YAMUX_ENABLED = false;
  public static final boolean DEFAULT_STRICT_CONFIG_LOADING_ENABLED = false;
  public static final boolean DEFAULT_QUIC_ENABLED = true;
  public static final boolean DEFAULT_TCP_ENABLED = true;

  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  private final boolean isEnabled;
  private final Optional<PrivateKeySource> privateKeySource;
  private final List<String> networkInterfaces;
  private final Optional<List<String>> advertisedIps;
  private final int listenPort;
  private final int listenPortIpv6;
  private final int listenQuicPort;
  private final int listenQuicPortIpv6;
  private final OptionalInt advertisedPort;
  private final OptionalInt advertisedPortIpv6;
  private final OptionalInt advertisedQuicPort;
  private final OptionalInt advertisedQuicPortIpv6;
  private final boolean yamuxEnabled;
  private final boolean quicEnabled;
  private final boolean tcpEnabled;

  private NetworkConfig(
      final boolean isEnabled,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig,
      final Optional<PrivateKeySource> privateKeySource,
      final List<String> networkInterfaces,
      final Optional<List<String>> advertisedIps,
      final int listenPort,
      final int listenPortIpv6,
      final int listenQuicPort,
      final int listenQuicPortIpv6,
      final OptionalInt advertisedPort,
      final OptionalInt advertisedPortIpv6,
      final OptionalInt advertisedQuicPort,
      final OptionalInt advertisedQuicPortIpv6,
      final boolean yamuxEnabled,
      final boolean quicEnabled,
      final boolean tcpEnabled) {
    this.privateKeySource = privateKeySource;
    this.networkInterfaces = networkInterfaces;
    this.advertisedIps = advertisedIps;
    this.isEnabled = isEnabled;
    this.listenPort = listenPort;
    this.listenPortIpv6 = listenPortIpv6;
    this.listenQuicPort = listenQuicPort;
    this.listenQuicPortIpv6 = listenQuicPortIpv6;
    this.advertisedPort = advertisedPort;
    this.advertisedPortIpv6 = advertisedPortIpv6;
    this.advertisedQuicPort = advertisedQuicPort;
    this.advertisedQuicPortIpv6 = advertisedQuicPortIpv6;
    this.yamuxEnabled = yamuxEnabled;
    this.gossipConfig = gossipConfig;
    this.wireLogsConfig = wireLogsConfig;
    this.quicEnabled = quicEnabled;
    this.tcpEnabled = tcpEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public Optional<PrivateKeySource> getPrivateKeySource() {
    return privateKeySource;
  }

  public List<String> getNetworkInterfaces() {
    return networkInterfaces;
  }

  public List<String> getAdvertisedIps() {
    return advertisedIps.orElse(networkInterfaces).stream()
        .map(this::resolveAnyLocalAddress)
        .toList();
  }

  public boolean hasUserExplicitlySetAdvertisedIps() {
    return advertisedIps.isPresent();
  }

  public int getListenPort() {
    return listenPort;
  }

  public int getListenPortIpv6() {
    return listenPortIpv6;
  }

  public int getListenQuicPort() {
    return listenQuicPort;
  }

  public int getListenQuicPortIpv6() {
    return listenQuicPortIpv6;
  }

  public int getAdvertisedPort() {
    return advertisedPort.orElse(listenPort);
  }

  public OptionalInt getOptionalAdvertisedPort() {
    return advertisedPort;
  }

  public int getAdvertisedPortIpv6() {
    return advertisedPortIpv6.orElse(listenPortIpv6);
  }

  public OptionalInt getOptionalAdvertisedPortIpv6() {
    return advertisedPortIpv6;
  }

  public int getAdvertisedQuicPort() {
    return advertisedQuicPort.orElse(listenQuicPort);
  }

  public int getAdvertisedQuicPortIpv6() {
    return advertisedQuicPortIpv6.orElse(listenQuicPortIpv6);
  }

  public boolean isYamuxEnabled() {
    return yamuxEnabled;
  }

  public boolean isQuicEnabled() {
    return quicEnabled;
  }

  public boolean isTcpEnabled() {
    return tcpEnabled;
  }

  public GossipConfig getGossipConfig() {
    return gossipConfig;
  }

  public WireLogsConfig getWireLogsConfig() {
    return wireLogsConfig;
  }

  private String resolveAnyLocalAddress(final String ipAddress) {
    try {
      final InetAddress advertisedAddress = InetAddress.getByName(ipAddress);
      if (advertisedAddress.isAnyLocalAddress()) {
        final IPVersion ipVersion = IPVersionResolver.resolve(advertisedAddress);
        return getLocalAddress(ipVersion);
      } else {
        return ipAddress;
      }
    } catch (final UnknownHostException ex) {
      LOG.error("Failed resolving local address: {}. Trying to use {}", ex.getMessage(), ipAddress);
      return ipAddress;
    }
  }

  private String getLocalAddress(final IPVersion ipVersion) throws UnknownHostException {
    try {
      final InetAddress localHostAddress = InetAddress.getLocalHost();
      if (localHostAddress.isAnyLocalAddress()
          && IPVersionResolver.resolve(localHostAddress) == ipVersion) {
        return localHostAddress.getHostAddress();
      }
      final Enumeration<NetworkInterface> networkInterfaces =
          NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        final NetworkInterface networkInterface = networkInterfaces.nextElement();
        final Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          final InetAddress inetAddress = inetAddresses.nextElement();
          if (IPVersionResolver.resolve(inetAddress) != ipVersion) {
            // incompatible IP version
            continue;
          }
          switch (ipVersion) {
            case IP_V4 -> {
              // IPv4 (include only site local addresses)
              if (inetAddress.isSiteLocalAddress()) {
                return inetAddress.getHostAddress();
              }
            }
            case IP_V6 -> {
              // IPv6: accept non-loopback, non-link-local addresses.
              // Unlike IPv4 where only private (site-local) addresses are selected,
              // IPv6 commonly uses global unicast addresses (no NAT), so we accept
              // global unicast (2000::/3), unique local (fc00::/7), and site local
              // (fec0::/10) addresses.
              if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                return inetAddress.getHostAddress();
              }
            }
          }
        }
      }
    } catch (final SocketException ex) {
      LOG.error("Failed to find local address", ex);
      throw new UnknownHostException(ex.getMessage());
    }
    throw new UnknownHostException(
        String.format("Unable to determine local %s Address", ipVersion.getName()));
  }

  public static class Builder {

    private final GossipConfig.Builder gossipConfigBuilder = GossipConfig.builder();
    private final WireLogsConfig.Builder wireLogsConfig = WireLogsConfig.builder();

    private Boolean isEnabled = true;
    private Optional<String> privateKeyFile = Optional.empty();
    private Optional<String> privateKeyFileSecp256k1 = Optional.empty();
    private Optional<String> privateKeyFileEcdsa = Optional.empty();
    private List<String> networkInterfaces = DEFAULT_P2P_INTERFACE;
    private Optional<List<String>> advertisedIps = Optional.empty();
    private int listenPort = DEFAULT_P2P_PORT;
    private int listenPortIpv6 = DEFAULT_P2P_PORT_IPV6;
    private int listenQuicPort = DEFAULT_P2P_QUIC_PORT;
    private int listenQuicPortIpv6 = DEFAULT_P2P_QUIC_PORT_IPV6;
    private OptionalInt advertisedPort = OptionalInt.empty();
    private OptionalInt advertisedPortIpv6 = OptionalInt.empty();
    private OptionalInt advertisedQuicPort = OptionalInt.empty();
    private OptionalInt advertisedQuicPortIpv6 = OptionalInt.empty();
    private boolean yamuxEnabled = DEFAULT_YAMUX_ENABLED;
    private boolean quicEnabled = DEFAULT_QUIC_ENABLED;
    private boolean tcpEnabled = DEFAULT_TCP_ENABLED;

    private Builder() {}

    public NetworkConfig build() {
      if (Stream.of(privateKeyFile, privateKeyFileSecp256k1, privateKeyFileEcdsa)
              .filter(Optional::isPresent)
              .count()
          > 1) {
        throw new InvalidConfigurationException(
            "Only a single private key option should be specified.");
      }

      return new NetworkConfig(
          isEnabled,
          gossipConfigBuilder.build(),
          wireLogsConfig.build(),
          createFileKeySource()
              .or(this::createSecp256k1FileKeySource)
              .or(this::createEcdsaFileKeySource),
          networkInterfaces,
          advertisedIps,
          listenPort,
          listenPortIpv6,
          listenQuicPort,
          listenQuicPortIpv6,
          advertisedPort,
          advertisedPortIpv6,
          advertisedQuicPort,
          advertisedQuicPortIpv6,
          yamuxEnabled,
          quicEnabled,
          tcpEnabled);
    }

    private Optional<PrivateKeySource> createFileKeySource() {
      return privateKeyFile.map(GeneratingFilePrivateKeySource::new);
    }

    private Optional<PrivateKeySource> createSecp256k1FileKeySource() {
      return privateKeyFileSecp256k1.map(
          path -> new TypedFilePrivateKeySource(path, PrivateKeySource.Type.SECP256K1));
    }

    private Optional<PrivateKeySource> createEcdsaFileKeySource() {
      return privateKeyFileEcdsa.map(
          path -> new TypedFilePrivateKeySource(path, PrivateKeySource.Type.ECDSA));
    }

    public Builder isEnabled(final Boolean enabled) {
      checkNotNull(enabled);
      isEnabled = enabled;
      return this;
    }

    public Builder gossipConfig(final Consumer<GossipConfig.Builder> consumer) {
      consumer.accept(gossipConfigBuilder);
      return this;
    }

    public Builder wireLogs(final Consumer<WireLogsConfig.Builder> consumer) {
      consumer.accept(wireLogsConfig);
      return this;
    }

    public Builder privateKeyFile(final String privateKeyFile) {
      checkNotNull(privateKeyFile);
      this.privateKeyFile = Optional.of(privateKeyFile).filter(f -> !f.isBlank());
      return this;
    }

    public Builder privateKeyFileSecp256k1(final String privateKeyFileSecp256k1) {
      checkNotNull(privateKeyFileSecp256k1);
      this.privateKeyFileSecp256k1 = Optional.of(privateKeyFileSecp256k1).filter(f -> !f.isBlank());
      return this;
    }

    public Builder privateKeyFileEcdsa(final String privateKeyFileEcdsa) {
      checkNotNull(privateKeyFileEcdsa);
      this.privateKeyFileEcdsa = Optional.of(privateKeyFileEcdsa).filter(f -> !f.isBlank());
      return this;
    }

    public Builder networkInterface(final String networkInterface) {
      return networkInterfaces(Collections.singletonList(networkInterface));
    }

    public Builder networkInterfaces(final List<String> networkInterfaces) {
      checkNotNull(networkInterfaces);
      validateAddresses(networkInterfaces, "--p2p-interface");
      this.networkInterfaces = networkInterfaces;
      return this;
    }

    public Builder advertisedIp(final Optional<String> advertisedIp) {
      return advertisedIps(advertisedIp.map(Collections::singletonList));
    }

    public Builder advertisedIps(final Optional<List<String>> advertisedIps) {
      checkNotNull(advertisedIps);
      advertisedIps.ifPresent(
          ips -> {
            ips.forEach(
                ip -> {
                  if (ip == null || ip.isBlank()) {
                    throw new InvalidConfigurationException("Advertised ip is blank");
                  }
                  if (!isInetAddress(ip)) {
                    throw new InvalidConfigurationException(
                        String.format("Advertised ip (%s) is set incorrectly", ip));
                  }
                });
            validateAddresses(ips, "--p2p-advertised-ip");
          });
      this.advertisedIps = advertisedIps;
      return this;
    }

    public Builder listenPort(final int listenPort) {
      validatePort(listenPort, "--p2p-port");
      this.listenPort = listenPort;
      return this;
    }

    public Builder listenPortIpv6(final int listenPortIpv6) {
      validatePort(listenPortIpv6, "--p2p-port-ipv6");
      this.listenPortIpv6 = listenPortIpv6;
      return this;
    }

    public Builder listenQuicPort(final int listenQuicPort) {
      validatePort(listenQuicPort, "--p2p-quic-port");
      this.listenQuicPort = listenQuicPort;
      return this;
    }

    public Builder listenQuicPortIpv6(final int listenQuicPortIpv6) {
      validatePort(listenQuicPortIpv6, "--p2p-quic-port-ipv6");
      this.listenQuicPortIpv6 = listenQuicPortIpv6;
      return this;
    }

    public Builder advertisedPort(final OptionalInt advertisedPort) {
      checkNotNull(advertisedPort);
      advertisedPort.ifPresent(port -> validatePort(port, "--p2p-advertised-port"));
      this.advertisedPort = advertisedPort;
      return this;
    }

    public Builder advertisedPortIpv6(final OptionalInt advertisedPortIpv6) {
      checkNotNull(advertisedPortIpv6);
      advertisedPortIpv6.ifPresent(port -> validatePort(port, "--p2p-advertised-port-ipv6"));
      this.advertisedPortIpv6 = advertisedPortIpv6;
      return this;
    }

    public Builder advertisedQuicPort(final OptionalInt advertisedQuicPort) {
      checkNotNull(advertisedQuicPort);
      advertisedQuicPort.ifPresent(port -> validatePort(port, "--p2p-advertised-quic-port"));
      this.advertisedQuicPort = advertisedQuicPort;
      return this;
    }

    public Builder advertisedQuicPortIpv6(final OptionalInt advertisedQuicPortIpv6) {
      checkNotNull(advertisedQuicPortIpv6);
      advertisedQuicPortIpv6.ifPresent(
          port -> validatePort(port, "--p2p-advertised-quic-port-ipv6"));
      this.advertisedQuicPortIpv6 = advertisedQuicPortIpv6;
      return this;
    }

    public Builder yamuxEnabled(final boolean yamuxEnabled) {
      this.yamuxEnabled = yamuxEnabled;
      return this;
    }

    public Builder quicEnabled(final Boolean quicEnabled) {
      this.quicEnabled = quicEnabled;
      return this;
    }

    public Builder tcpEnabled(final boolean tcpEnabled) {
      this.tcpEnabled = tcpEnabled;
      return this;
    }

    public Builder directPeers(final List<NodeId> directPeers) {
      checkNotNull(directPeers);
      final DirectPeerManager directPeerManager = directPeers::contains;
      this.gossipConfigBuilder.directPeerManager(directPeerManager);
      return this;
    }

    private void validateAddresses(final List<String> addresses, final String cliOption) {
      checkState(
          addresses.size() == 1 || addresses.size() == 2,
          "Invalid number of %s. It should be either 1 or 2, but it was %s",
          cliOption,
          addresses.size());
      if (addresses.size() == 2) {
        final Set<IPVersion> ipVersions =
            addresses.stream().map(IPVersionResolver::resolve).collect(Collectors.toSet());
        if (ipVersions.size() != 2) {
          throw new InvalidConfigurationException(
              String.format(
                  "Expected an IPv4 and an IPv6 address for %s but only %s was set",
                  cliOption, ipVersions));
        }
      }
    }

    private void validatePort(final int port, final String cliOption) {
      if (!PortAvailability.isPortValid(port)) {
        throw new InvalidConfigurationException(String.format("Invalid %s: %d", cliOption, port));
      }
    }
  }
}

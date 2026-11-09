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

package tech.pegasys.teku.networking.p2p.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.networking.p2p.network.config.NetworkConfig.DEFAULT_P2P_PORT;
import static tech.pegasys.teku.networking.p2p.network.config.NetworkConfig.DEFAULT_P2P_PORT_IPV6;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;

public class DiscoveryConfig {
  private static final Logger LOG = LogManager.getLogger();
  public static final boolean DEFAULT_P2P_DISCOVERY_ENABLED = true;
  public static final int DEFAULT_P2P_PEERS_LOWER_BOUND = 64;
  public static final int DEFAULT_P2P_PEERS_UPPER_BOUND = 100;
  public static final int DEFAULT_P2P_PEERS_LOWER_BOUND_ALL_SUBNETS = 60;
  public static final int DEFAULT_P2P_PEERS_UPPER_BOUND_ALL_SUBNETS = 80;
  public static final int DEFAULT_RANDOMLY_SELECTED_PEER_COUNT_PERCENTAGE = 30;
  public static final boolean DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED = false;

  private final boolean isDiscoveryEnabled;
  private final int listenUdpPort;
  private final int listenUpdPortIpv6;
  private final OptionalInt advertisedUdpPort;
  private final OptionalInt advertisedUdpPortIpv6;
  private final List<String> staticPeers;
  private final List<String> bootnodes;
  private final int minPeers;
  private final int maxPeers;
  private final int minRandomlySelectedPeers;
  private final boolean siteLocalAddressesEnabled;

  private DiscoveryConfig(
      final boolean isDiscoveryEnabled,
      final int listenUdpPort,
      final int listenUpdPortIpv6,
      final OptionalInt advertisedUdpPort,
      final OptionalInt advertisedUdpPortIpv6,
      final List<String> staticPeers,
      final List<String> bootnodes,
      final int minPeers,
      final int maxPeers,
      final int minRandomlySelectedPeers,
      final boolean siteLocalAddressesEnabled) {
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.listenUdpPort = listenUdpPort;
    this.listenUpdPortIpv6 = listenUpdPortIpv6;
    this.advertisedUdpPort = advertisedUdpPort;
    this.advertisedUdpPortIpv6 = advertisedUdpPortIpv6;
    this.staticPeers = staticPeers;
    this.bootnodes = bootnodes;
    this.minPeers = minPeers;
    this.maxPeers = maxPeers;
    this.minRandomlySelectedPeers = minRandomlySelectedPeers;
    this.siteLocalAddressesEnabled = siteLocalAddressesEnabled;

    LOG.debug("Peer limits - Minimum {}, Maximum {}", minPeers, maxPeers);
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDiscoveryEnabled() {
    return isDiscoveryEnabled;
  }

  public int getListenUdpPort() {
    return listenUdpPort;
  }

  public int getListenUpdPortIpv6() {
    return listenUpdPortIpv6;
  }

  public int getAdvertisedUdpPort() {
    return advertisedUdpPort.orElse(listenUdpPort);
  }

  public int getAdvertisedUdpPortIpv6() {
    return advertisedUdpPortIpv6.orElse(listenUpdPortIpv6);
  }

  public List<String> getStaticPeers() {
    return staticPeers;
  }

  public List<String> getBootnodes() {
    return bootnodes;
  }

  public int getMinPeers() {
    return minPeers;
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public int getMinRandomlySelectedPeers() {
    return minRandomlySelectedPeers;
  }

  public boolean areSiteLocalAddressesEnabled() {
    return siteLocalAddressesEnabled;
  }

  public static class Builder {
    private Boolean isDiscoveryEnabled = DEFAULT_P2P_DISCOVERY_ENABLED;
    private List<String> staticPeers = Collections.emptyList();
    private List<String> bootnodes;
    private OptionalInt minPeers = OptionalInt.empty();
    private OptionalInt maxPeers = OptionalInt.empty();
    private OptionalInt minRandomlySelectedPeers = OptionalInt.empty();
    private OptionalInt listenUdpPort = OptionalInt.empty();
    private OptionalInt listenUdpPortIpv6 = OptionalInt.empty();
    private OptionalInt advertisedUdpPort = OptionalInt.empty();
    private OptionalInt advertisedUdpPortIpv6 = OptionalInt.empty();
    private boolean siteLocalAddressesEnabled = DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED;

    private Builder() {}

    public DiscoveryConfig build() {
      initMissingDefaults();
      validateRandomlySelectedPeerCount();

      return new DiscoveryConfig(
          isDiscoveryEnabled,
          listenUdpPort.orElseThrow(),
          listenUdpPortIpv6.orElseThrow(),
          advertisedUdpPort,
          advertisedUdpPortIpv6,
          staticPeers,
          bootnodes == null ? Collections.emptyList() : bootnodes,
          minPeers.orElse(DEFAULT_P2P_PEERS_LOWER_BOUND),
          maxPeers.orElse(DEFAULT_P2P_PEERS_UPPER_BOUND),
          minRandomlySelectedPeers.orElseThrow(),
          siteLocalAddressesEnabled);
    }

    private void initMissingDefaults() {
      if (minRandomlySelectedPeers.isEmpty()) {
        if (maxPeers.isPresent() && maxPeers.getAsInt() == 0) {
          minRandomlySelectedPeers = OptionalInt.of(0);
        } else {
          minRandomlySelectedPeers =
              OptionalInt.of(
                  Math.max(
                      1,
                      minPeers.orElse(DEFAULT_P2P_PEERS_LOWER_BOUND)
                          * DEFAULT_RANDOMLY_SELECTED_PEER_COUNT_PERCENTAGE
                          / 100));
        }
      }
      if (listenUdpPort.isEmpty()) {
        listenUdpPort = OptionalInt.of(DEFAULT_P2P_PORT);
      }
      if (listenUdpPortIpv6.isEmpty()) {
        listenUdpPortIpv6 = OptionalInt.of(DEFAULT_P2P_PORT_IPV6);
      }
    }

    private void validateRandomlySelectedPeerCount() {
      final int effectiveMaxPeers = maxPeers.orElse(DEFAULT_P2P_PEERS_UPPER_BOUND);
      final int effectiveMinRandomlySelectedPeers = minRandomlySelectedPeers.orElseThrow();
      if (effectiveMinRandomlySelectedPeers > effectiveMaxPeers) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid minRandomlySelectedPeers: %d exceeds maxPeers: %d",
                effectiveMinRandomlySelectedPeers, effectiveMaxPeers));
      }
    }

    public Builder isDiscoveryEnabled(final Boolean discoveryEnabled) {
      checkNotNull(discoveryEnabled);
      isDiscoveryEnabled = discoveryEnabled;
      return this;
    }

    public Builder listenUdpPort(final int listenUdpPort) {
      validatePort(listenUdpPort, "--p2p-udp-port");
      this.listenUdpPort = OptionalInt.of(listenUdpPort);
      return this;
    }

    public Builder listenUdpPortDefault(final int listenUdpPort) {
      validatePort(listenUdpPort, "--p2p-udp-port");
      if (this.listenUdpPort.isEmpty()) {
        this.listenUdpPort = OptionalInt.of(listenUdpPort);
      }
      return this;
    }

    public Builder listenUdpPortIpv6(final int listenUdpPortIpv6) {
      validatePort(listenUdpPortIpv6, "--p2p-udp-port-ipv6");
      this.listenUdpPortIpv6 = OptionalInt.of(listenUdpPortIpv6);
      return this;
    }

    public Builder listenUdpPortIpv6Default(final int listenUdpPortIpv6) {
      validatePort(listenUdpPortIpv6, "--p2p-udp-port-ipv6");
      if (this.listenUdpPortIpv6.isEmpty()) {
        this.listenUdpPortIpv6 = OptionalInt.of(listenUdpPortIpv6);
      }
      return this;
    }

    public Builder advertisedUdpPort(final OptionalInt advertisedUdpPort) {
      checkNotNull(advertisedUdpPort);
      if (advertisedUdpPort.isPresent()) {
        validatePort(advertisedUdpPort.getAsInt(), "--p2p-advertised-udp-port");
      }
      this.advertisedUdpPort = advertisedUdpPort;
      return this;
    }

    public Builder advertisedUdpPortDefault(final OptionalInt advertisedUdpPort) {
      checkNotNull(advertisedUdpPort);
      if (advertisedUdpPort.isPresent()) {
        validatePort(advertisedUdpPort.getAsInt(), "--p2p-advertised-udp-port");
      }
      if (this.advertisedUdpPort.isEmpty()) {
        this.advertisedUdpPort = advertisedUdpPort;
      }
      return this;
    }

    public Builder advertisedUdpPortIpv6(final OptionalInt advertisedUdpPortIpv6) {
      checkNotNull(advertisedUdpPortIpv6);
      if (advertisedUdpPortIpv6.isPresent()) {
        validatePort(advertisedUdpPortIpv6.getAsInt(), "--p2p-advertised-udp-port-ipv6");
      }
      this.advertisedUdpPortIpv6 = advertisedUdpPortIpv6;
      return this;
    }

    public Builder advertisedUdpPortIpv6Default(final OptionalInt advertisedUdpPortIpv6) {
      checkNotNull(advertisedUdpPortIpv6);
      if (advertisedUdpPortIpv6.isPresent()) {
        validatePort(advertisedUdpPortIpv6.getAsInt(), "--p2p-advertised-udp-port-ipv6");
      }
      if (this.advertisedUdpPortIpv6.isEmpty()) {
        this.advertisedUdpPortIpv6 = advertisedUdpPortIpv6;
      }
      return this;
    }

    public Builder staticPeers(final List<String> staticPeers) {
      checkNotNull(staticPeers);
      this.staticPeers = staticPeers;
      return this;
    }

    public Builder bootnodes(final List<String> bootnodes) {
      checkNotNull(bootnodes);
      for (final String bootnode : bootnodes) {
        if (!bootnode.startsWith("enr:-")) {
          throw new IllegalArgumentException(
              String.format("The bootnode (%s) does not start with `enr:-`", bootnode));
        }
      }
      this.bootnodes = bootnodes;
      return this;
    }

    public Builder bootnodesDefault(final List<String> bootnodes) {
      checkNotNull(bootnodes);
      if (this.bootnodes == null) {
        this.bootnodes = bootnodes;
      }
      return this;
    }

    public Builder minPeersIfDefault(final Integer minPeers) {
      if (this.minPeers.isEmpty()) {
        return minPeers(minPeers);
      }
      return this;
    }

    public Builder minPeers(final Integer minPeers) {
      checkNotNull(minPeers);
      if (minPeers < 0) {
        throw new InvalidConfigurationException(String.format("Invalid minPeers: %d", minPeers));
      }
      this.minPeers = OptionalInt.of(minPeers);
      return this;
    }

    public Builder maxPeersIfDefault(final Integer maxPeers) {
      if (this.maxPeers.isEmpty()) {
        return maxPeers(maxPeers);
      }
      return this;
    }

    public Builder maxPeers(final Integer maxPeers) {
      checkNotNull(maxPeers);
      if (maxPeers < 0) {
        throw new InvalidConfigurationException(String.format("Invalid maxPeers: %d", maxPeers));
      }
      this.maxPeers = OptionalInt.of(maxPeers);
      return this;
    }

    public Builder minRandomlySelectedPeers(final Integer minRandomlySelectedPeers) {
      checkNotNull(minRandomlySelectedPeers);
      if (minRandomlySelectedPeers < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid minRandomlySelectedPeers: %d", minRandomlySelectedPeers));
      }
      this.minRandomlySelectedPeers = OptionalInt.of(minRandomlySelectedPeers);
      return this;
    }

    public Builder siteLocalAddressesEnabled(final boolean siteLocalAddressesEnabled) {
      this.siteLocalAddressesEnabled = siteLocalAddressesEnabled;
      return this;
    }

    private void validatePort(final int port, final String cliOption) {
      if (!PortAvailability.isPortValid(port)) {
        throw new InvalidConfigurationException(String.format("Invalid %s: %d", cliOption, port));
      }
    }
  }
}

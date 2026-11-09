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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver.IPVersion;

public final class DualStackPortBindings {

  private DualStackPortBindings() {}

  public static boolean shouldListenOnAddress(
      final List<String> networkInterfaces,
      final String networkInterface,
      final int listenPort,
      final int listenPortIpv6) {
    // A wildcard IPv6 socket is used as the single dual-stack listener for the same port. This
    // may broaden a specific IPv4 bind to all IPv4 interfaces, but avoids a double-bind failure
    // when the IPv6 wildcard socket already owns the IPv4 port.
    return networkInterfaces.size() == 1
        || IPVersionResolver.resolve(networkInterface) == IPVersion.IP_V6
        || !hasIpv6AnyLocalAddressOnSamePort(networkInterfaces, listenPort, listenPortIpv6);
  }

  private static boolean hasIpv6AnyLocalAddressOnSamePort(
      final List<String> networkInterfaces, final int listenPort, final int listenPortIpv6) {
    return listenPort == listenPortIpv6
        && networkInterfaces.stream()
            .anyMatch(
                networkInterface ->
                    IPVersionResolver.resolve(networkInterface) == IPVersion.IP_V6
                        && isAnyLocalAddress(networkInterface));
  }

  private static boolean isAnyLocalAddress(final String address) {
    try {
      return InetAddress.getByName(address).isAnyLocalAddress();
    } catch (final UnknownHostException ex) {
      throw new InvalidConfigurationException("Invalid network interface: " + address, ex);
    }
  }
}

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

package tech.pegasys.teku.networking.nat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class NatService extends Service {

  private final Optional<NatManager> maybeNatManager;
  private final boolean isDiscoveryEnabled;
  private final int p2pPort;
  private final Optional<Integer> p2pPortIpv6;

  NatService(
      final int p2pPort,
      final Optional<Integer> p2pPortIpv6,
      final boolean isDiscoveryEnabled,
      final Optional<NatManager> maybeNatManager) {
    this.p2pPort = p2pPort;
    this.p2pPortIpv6 = p2pPortIpv6;
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.maybeNatManager = maybeNatManager;
  }

  public NatService(
      final NatConfiguration natConfiguration,
      final int p2pPort,
      final Optional<Integer> p2pPortIpv6,
      final boolean isDiscoveryEnabled) {
    this(
        p2pPort,
        p2pPortIpv6,
        isDiscoveryEnabled,
        natConfiguration.getNatMethod().equals(NatMethod.UPNP)
            ? Optional.of(new NatManager())
            : Optional.empty());
  }

  @Override
  protected SafeFuture<Void> doStart() {
    if (maybeNatManager.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final NatManager natManager = maybeNatManager.get();
    return natManager
        .start()
        .thenRun(
            () -> {
              final Set<Integer> p2pPorts = new HashSet<>();
              p2pPorts.add(p2pPort);
              p2pPortIpv6.ifPresent(p2pPorts::add);
              p2pPorts.forEach(port -> requestPortForward(natManager, port));
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return maybeNatManager.map(NatManager::stop).orElse(SafeFuture.completedFuture(null));
  }

  private void requestPortForward(final NatManager natManager, final int port) {
    natManager.requestPortForward(port, NetworkProtocol.TCP, NatServiceType.TEKU_P2P);
    if (isDiscoveryEnabled) {
      natManager.requestPortForward(port, NetworkProtocol.UDP, NatServiceType.TEKU_DISCOVERY);
    }
  }
}

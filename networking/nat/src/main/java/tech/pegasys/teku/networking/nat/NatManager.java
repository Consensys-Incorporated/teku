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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.model.meta.RemoteService;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class NatManager extends Service {
  private static final Logger LOG = LogManager.getLogger();

  static final String SERVICE_TYPE_WAN_IP_CONNECTION = "WANIPConnection";

  private final UpnpClient upnpClient;
  private final Queue<NatPortMapping> forwardedPorts = new ConcurrentLinkedQueue<>();

  public NatManager() {
    this(new UpnpClient());
  }

  @VisibleForTesting
  NatManager(final UpnpClient upnpClient) {
    this.upnpClient = upnpClient;
  }

  @Override
  public SafeFuture<Void> doStart() {
    LOG.info("Starting UPnP Service");

    upnpClient.startup().finish(error -> LOG.warn("Failed to startup UPnP service", error));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<Void> doStop() {
    return releaseAllPortForwards()
        .orTimeout(3, TimeUnit.SECONDS)
        .exceptionally(
            error -> {
              LOG.debug("Failed to release port forwards", error);
              return null;
            })
        .alwaysRun(() -> upnpClient.shutdown());
  }

  @VisibleForTesting
  CompletableFuture<RemoteService> getWANIPConnectionService() {
    checkState(isRunning(), "Cannot call getWANIPConnectionService() when in stopped state");
    return upnpClient.getWanIpFuture();
  }

  private SafeFuture<Void> releaseAllPortForwards() {
    // if we haven't observed the WANIPConnection service yet, we should have no port forwards to
    // release
    if (!upnpClient.getWanIpFuture().isDone()) {
      LOG.debug("Ports had not completed setting up, will not be able to disconnect.");
      return SafeFuture.COMPLETE;
    }

    if (forwardedPorts.isEmpty()) {
      LOG.debug("Port list is empty.");
      return SafeFuture.COMPLETE;
    } else {
      LOG.debug("Have {} ports to close", forwardedPorts.size());
    }
    final List<SafeFuture<Void>> futures = new ArrayList<>();

    while (!forwardedPorts.isEmpty()) {
      futures.add(upnpClient.releasePortForward(forwardedPorts.remove()));
    }

    // return a future that completes successfully only when each of our port delete requests
    // complete
    return SafeFuture.allOf(futures.toArray(new SafeFuture<?>[0]));
  }

  public void requestPortForward(
      final int port, final NetworkProtocol protocol, final NatServiceType serviceType) {
    checkState(isRunning(), "Cannot call requestPortForward() when in stopped state");
    checkArgument(port != 0, "Cannot map to internal port zero.");
    upnpClient
        .requestPortForward(port, protocol, serviceType)
        .thenCompose(
            natPortMapping -> {
              forwardedPorts.add(natPortMapping);
              return SafeFuture.COMPLETE;
            })
        .finish(error -> LOG.debug("Failed to forward port ", error));
  }
}

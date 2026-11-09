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

import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.jupnp.support.model.PortMapping;
import tech.pegasys.teku.infrastructure.async.FutureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class UpnpClient {
  private static final Logger LOG = LogManager.getLogger();

  static final String SERVICE_TYPE_WAN_IP_CONNECTION = "WANIPConnection";
  private final SafeFuture<String> externalIpQueryFuture = new SafeFuture<>();
  private final SafeFuture<RemoteService> wanIpFuture = new SafeFuture<>();
  private Optional<String> localIpAddress = Optional.empty();
  private final UpnpService upnpService;
  private final RegistryListener registryListener;

  public UpnpClient() {
    this(new UpnpServiceImpl(new TekuNatServiceConfiguration()));
  }

  public UpnpClient(final UpnpService upnpService) {
    this.upnpService = upnpService;
    // registry listener to observe new devices and look for specific services
    registryListener =
        new TekuRegistryListener() {
          @Override
          public void remoteDeviceAdded(final Registry registry, final RemoteDevice device) {
            LOG.debug("UPnP Device discovered: " + device.getDetails().getFriendlyName());
            inspectDeviceRecursive(device);
          }
        };
  }

  public SafeFuture<?> startup() {
    upnpService.startup();
    upnpService.getRegistry().addListener(registryListener);
    initiateExternalIpQuery();
    return wanIpFuture;
  }

  public void shutdown() {
    wanIpFuture.cancel(true);
    upnpService.getRegistry().removeListener(registryListener);
    upnpService.shutdown();
  }

  @SuppressWarnings("unchecked")
  public SafeFuture<Void> releasePortForward(final NatPortMapping portMapping) {
    LOG.debug(
        "Releasing port forward for {} {} -> {}",
        portMapping.getProtocol(),
        portMapping.getInternalPort(),
        portMapping.getExternalPort());

    RemoteService service = getWanIpFuture().join();
    TekuPortMappingDelete callback =
        new TekuPortMappingDelete(service, toJupnpPortMapping(portMapping));

    FutureUtil.ignoreFuture(upnpService.getControlPoint().execute(callback));

    return callback.getFuture();
  }

  @SuppressWarnings("unchecked")
  public SafeFuture<NatPortMapping> requestPortForward(
      final int port, final NetworkProtocol protocol, final NatServiceType serviceType) {
    return requestPortForward(
        new PortMapping(
            true,
            new UnsignedIntegerFourBytes(0),
            null,
            new UnsignedIntegerTwoBytes(port),
            new UnsignedIntegerTwoBytes(port),
            null,
            toJupnpProtocol(protocol),
            serviceType.getValue()));
  }

  @VisibleForTesting
  SafeFuture<RemoteService> getWanIpFuture() {
    return wanIpFuture;
  }

  private SafeFuture<String> getExternalIpFuture() {
    return externalIpQueryFuture;
  }

  private Optional<String> getLocalIpAddress() {
    return localIpAddress;
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<NatPortMapping> requestPortForward(final PortMapping portMapping) {
    return getExternalIpFuture()
        .thenCompose(
            address -> {
              // note that this future is a dependency of externalIpQueryFuture, so it must be
              // completed by now
              RemoteService service = getWanIpFuture().join();

              // at this point, we should have the local address we discovered the IGD on,
              // so we can prime the NewInternalClient field if it was omitted
              if (null == portMapping.getInternalClient()) {
                portMapping.setInternalClient(getLocalIpAddress().orElse(""));
              }

              // our query, which will be handled asynchronously by the jupnp library
              TekuPortMappingAdd callback = new TekuPortMappingAdd(service, portMapping);

              LOG.debug(
                  "Requesting port forward for {} {} -> {}",
                  portMapping.getProtocol(),
                  portMapping.getInternalPort(),
                  portMapping.getExternalPort());

              ignoreFuture(upnpService.getControlPoint().execute(callback));
              return callback.getFuture();
            });
  }

  @SuppressWarnings("unchecked")
  private void initiateExternalIpQuery() {
    wanIpFuture
        .thenAccept(
            service -> {
              TekuGetExternalIP callback = new TekuGetExternalIP(service);
              FutureUtil.ignoreFuture(upnpService.getControlPoint().execute(callback));
              callback
                  .getFuture()
                  .thenAccept(
                      externalIpAddress -> {
                        LOG.debug("Finished getting IP Address");
                        localIpAddress = callback.getDiscoveredOnLocalAddress();
                        externalIpQueryFuture.complete(externalIpAddress);
                      })
                  .finish(
                      error -> {
                        LOG.debug("Failed to get external ip address", error);
                        externalIpQueryFuture.completeExceptionally(error);
                      });
            })
        .finish(error -> LOG.debug("Failed to retrieve external ip address", error));
  }

  private void inspectDeviceRecursive(final RemoteDevice device) {
    for (RemoteService service : device.getServices()) {
      String serviceType = service.getServiceType().getType();
      if (serviceType.equalsIgnoreCase(SERVICE_TYPE_WAN_IP_CONNECTION)) {
        wanIpFuture.complete(service);
      }
    }
    for (RemoteDevice subDevice : device.getEmbeddedDevices()) {
      inspectDeviceRecursive(subDevice);
    }
  }

  private PortMapping.Protocol toJupnpProtocol(final NetworkProtocol protocol) {
    return switch (protocol) {
      case UDP -> PortMapping.Protocol.UDP;
      case TCP -> PortMapping.Protocol.TCP;
    };
  }

  private PortMapping toJupnpPortMapping(final NatPortMapping natPortMapping) {
    return new PortMapping(
        true,
        new UnsignedIntegerFourBytes(0),
        null,
        new UnsignedIntegerTwoBytes(natPortMapping.getExternalPort()),
        new UnsignedIntegerTwoBytes(natPortMapping.getInternalPort()),
        null,
        toJupnpProtocol(natPortMapping.getProtocol()),
        natPortMapping.getNatServiceType().getValue());
  }
}

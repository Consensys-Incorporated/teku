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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.UpnpService;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class UpnpClientTest {
  private final UpnpService natService = mock(UpnpService.class);
  private final Registry registry = mock(Registry.class);
  private UpnpClient upnpClient;

  @BeforeEach
  void setup() {
    when(natService.getRegistry()).thenReturn(registry);
    this.upnpClient = new UpnpClient(natService);
  }

  @Test
  public void registryListenerShouldDetectService() throws Exception {
    final SafeFuture<?> startupFuture = upnpClient.startup();

    ArgumentCaptor<RegistryListener> captor = ArgumentCaptor.forClass(RegistryListener.class);
    verify(registry).addListener(captor.capture());
    RegistryListener listener = captor.getValue();

    assertThat(listener).isNotNull();

    // create a remote device that matches the WANIPConnection service that NatManager
    // is looking for and directly call the registry listener
    RemoteService wanIpConnectionService =
        new RemoteService(
            new UDAServiceType("WANIPConnection"),
            new UDAServiceId("WANIPConnectionService"),
            URI.create("/x_wanipconnection.xml"),
            URI.create("/control?WANIPConnection"),
            URI.create("/event?WANIPConnection"),
            null,
            null);

    RemoteDevice device =
        new RemoteDevice(
            new RemoteDeviceIdentity(
                UDN.valueOf(NatManager.SERVICE_TYPE_WAN_IP_CONNECTION),
                3600,
                URI.create("http://127.63.31.15/").toURL(),
                null,
                InetAddress.getByName("127.63.31.15")),
            new UDADeviceType("WANConnectionDevice"),
            new DeviceDetails("WAN Connection Device"),
            wanIpConnectionService);

    listener.remoteDeviceAdded(registry, device);

    assertThat(startupFuture).isCompleted();
    assertThat(upnpClient.getWanIpFuture().join()).isEqualTo(wanIpConnectionService);
  }
}

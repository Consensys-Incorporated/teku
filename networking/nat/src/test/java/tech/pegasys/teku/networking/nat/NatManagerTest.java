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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.model.meta.RemoteService;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class NatManagerTest {
  private UpnpClient upnpClient;
  private NatManager natManager;

  @BeforeEach
  public void setup() {
    upnpClient = mock(UpnpClient.class);
    natManager = new NatManager(upnpClient);
    when(upnpClient.startup()).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  public void startShouldInvokeUPnPClientStartup() {
    assertThat(natManager.start()).isCompleted();
    verify(upnpClient).startup();

    verifyNoMoreInteractions(upnpClient);
  }

  @Test
  public void stopShouldInvokeUPnPClientShutdown() {
    final RemoteService remoteService = mock(RemoteService.class);
    assertThat(natManager.start()).isCompleted();
    verify(upnpClient).startup();

    when(upnpClient.getWanIpFuture()).thenReturn(SafeFuture.completedFuture(remoteService));
    assertThat(natManager.stop()).isCompleted();

    verify(upnpClient).getWanIpFuture();
    verify(upnpClient).shutdown();

    verifyNoMoreInteractions(upnpClient);
  }

  @Test
  public void stopDoesNothingWhenAlreadyStopped() {
    assertThat(natManager.stop()).isCompleted();
    verifyNoMoreInteractions(upnpClient);
  }

  @Test
  public void startDoesNothingWhenAlreadyStarted() {
    assertThat(natManager.start()).isCompleted();

    verify(upnpClient).startup();

    assertThat(natManager.start()).isCompletedExceptionally().isNotCancelled();

    verifyNoMoreInteractions(upnpClient);
  }

  @Test
  public void requestPortForwardThrowsWhenCalledBeforeStart() {
    assertThatThrownBy(
            () -> {
              natManager.requestPortForward(80, NetworkProtocol.TCP, NatServiceType.TEKU_DISCOVERY);
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void requestPortForwardThrowsWhenPortIsZero() {
    assertThat(natManager.start()).isCompleted();

    assertThatThrownBy(
            () ->
                natManager.requestPortForward(
                    0, NetworkProtocol.TCP, NatServiceType.TEKU_DISCOVERY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReleasePortsOnShutdown() {
    final NatPortMapping tcpMapping = mock(NatPortMapping.class);
    final SafeFuture<NatPortMapping> futureTcpMapping = SafeFuture.completedFuture(tcpMapping);

    assertThat(natManager.start()).isCompleted();
    verify(upnpClient).startup();

    // after the manager starts, the parent service will map any required ports
    when(upnpClient.requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.TEKU_P2P))
        .thenReturn(futureTcpMapping);
    natManager.requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.TEKU_P2P);
    verify(upnpClient).requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.TEKU_P2P);
    verifyNoMoreInteractions(upnpClient);

    // when stop is called, the port that got mapped needs to be released
    when(upnpClient.getWanIpFuture()).thenReturn(SafeFuture.completedFuture(null));
    when(upnpClient.releasePortForward(tcpMapping)).thenReturn(SafeFuture.COMPLETE);
    assertThat(natManager.stop()).isCompleted();
    verify(upnpClient).getWanIpFuture();
    verify(upnpClient).releasePortForward(tcpMapping);
    verify(upnpClient).shutdown();
    verifyNoMoreInteractions(upnpClient);
  }
}

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

package tech.pegasys.teku.beaconrestapi.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Optional;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;

public class RestApiInterfacesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private static final Logger LOG = LogManager.getLogger();

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldOnlyListenOnLocalhostByDefault() throws SocketException {
    final Optional<InetAddress> maybeHostAddress = getHostAddress();

    if (maybeHostAddress.isEmpty()) {
      LOG.info("Skipping test, no ipv4 external addresses found");
      return;
    }
    final String addressString = maybeHostAddress.get().getHostAddress();

    final String url =
        "http://" + addressString + ":" + beaconRestApi.getListenPort() + "/eth/v1/beacon/genesis";
    assertThatThrownBy(() -> getResponseFromUrl(url)).isInstanceOf(ConnectException.class);
  }

  @Test
  void shouldListenOnLocalhost() throws IOException {
    final String url =
        "http://127.0.0.1:" + beaconRestApi.getListenPort() + "/eth/v1/beacon/genesis";
    final Response response = getResponseFromUrl(url);
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  private Optional<InetAddress> getHostAddress() throws SocketException {
    return NetworkInterface.networkInterfaces()
        .filter(
            iface -> {
              try {
                return (!iface.isLoopback() && iface.isUp());
              } catch (Exception ex) {
                LOG.error(ex);
              }
              return false;
            })
        .flatMap(NetworkInterface::inetAddresses)
        .filter(addr -> !(addr instanceof Inet6Address))
        .findFirst();
  }
}

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

package tech.pegasys.teku.infrastructure.io.resource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class URLResourceLoaderIntegrationTest {

  @Test
  void shouldThrowConnectExceptionWhenConnectionTimesOut() throws Exception {
    // Create a socket on any available port that never responds
    final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    try (final ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
      final ResourceLoader loader = new URLResourceLoader(Optional.empty(), __ -> true, 1);
      assertThatThrownBy(
              () ->
                  loader.loadSource(
                      "http://"
                          + loopbackAddress.getHostAddress()
                          + ":"
                          + serverSocket.getLocalPort()))
          .isInstanceOf(SocketTimeoutException.class);
    }
  }
}

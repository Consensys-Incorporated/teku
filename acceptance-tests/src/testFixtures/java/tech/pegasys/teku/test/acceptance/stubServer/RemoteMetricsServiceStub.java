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

package tech.pegasys.teku.test.acceptance.stubServer;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class RemoteMetricsServiceStub {
  private final HttpServer server;

  public RemoteMetricsServiceStub(final InetSocketAddress inetSocketAddress) throws IOException {
    server = HttpServer.create(inetSocketAddress, 0);
  }

  public void startServer() {
    server.setExecutor(null);
    server.start();
  }

  public void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  public void registerHandler(final String uriToHandle, final HttpHandler httpHandler) {
    server.createContext(uriToHandle, httpHandler);
  }
}

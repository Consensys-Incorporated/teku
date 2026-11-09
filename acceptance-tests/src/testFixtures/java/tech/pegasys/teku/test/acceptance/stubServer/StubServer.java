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

import java.net.InetSocketAddress;

public class StubServer {

  public static void main(final String[] args) throws Throwable {
    RemoteMetricsServiceStub remoteMetricsServiceStub =
        new RemoteMetricsServiceStub(new InetSocketAddress(8001));
    SuccessHandler handler = new SuccessHandler();
    remoteMetricsServiceStub.registerHandler("/input", handler);

    ReplyHandler reply = new ReplyHandler(handler);
    remoteMetricsServiceStub.registerHandler("/output", reply);

    remoteMetricsServiceStub.startServer();
  }
}

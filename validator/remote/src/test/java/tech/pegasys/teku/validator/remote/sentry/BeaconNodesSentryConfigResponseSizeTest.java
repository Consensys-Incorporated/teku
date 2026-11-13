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

package tech.pegasys.teku.validator.remote.sentry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class BeaconNodesSentryConfigResponseSizeTest {

  @Test
  void configuredLimitAppliesToSentryNodes() throws IOException {
    try (final MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setBody("small").setHeader("Content-Length", 1_000_001));
      final BeaconNodesSentryConfig config =
          BeaconNodesSentryConfig.Builder.builder()
              .dutiesProviderNodeConfig(
                  BeaconNodeRoleConfig.Builder.builder()
                      .endpoints(List.of(server.url("/").toString()))
                      .build())
              .build();
      final OkHttpClient client = config.createOkHttpClientForSentryNodes(1);

      assertThatThrownBy(
              () -> client.newCall(new Request.Builder().url(server.url("/")).build()).execute())
          .isInstanceOf(IOException.class)
          .hasMessageContaining("1 MB");
    }
  }
}

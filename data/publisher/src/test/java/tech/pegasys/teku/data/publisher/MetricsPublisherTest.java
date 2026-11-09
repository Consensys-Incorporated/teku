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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsPublisherTest {

  private final OkHttpClient okHttpClient = new OkHttpClient();
  private final MockWebServer mockWebServer = new MockWebServer();
  private MetricsPublisher metricsPublisher;

  @BeforeEach
  public void beforeEach() throws Exception {
    mockWebServer.start();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void shouldPublishMetrics() throws IOException, InterruptedException {
    String json = "{ \"version\": 1}";
    metricsPublisher = new MetricsPublisher(okHttpClient, mockWebServer.url("/"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    metricsPublisher.publishMetrics(json);

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(json);
  }
}

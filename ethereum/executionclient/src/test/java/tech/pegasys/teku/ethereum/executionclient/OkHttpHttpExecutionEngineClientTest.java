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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class OkHttpHttpExecutionEngineClientTest {

  private static final String VALID_JSONRPC_RESPONSE =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}";

  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);
  private final EventLogger eventLog = mock(EventLogger.class);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  private OkHttpClient spyOkHttpClient;
  private OkHttpHttpExecutionEngineClient engineClient;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void usesExistingClient_whenTimeoutMatchesDefault() throws Exception {
    // getPayloadV1 uses GET_PAYLOAD_TIMEOUT = 2s
    createClientWithCallTimeout(Duration.ofSeconds(2));

    mockWebServer.enqueue(new MockResponse().setBody(VALID_JSONRPC_RESPONSE));

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);

    assertThat(response).isNotNull();
    verify(spyOkHttpClient, never()).newBuilder();
  }

  @Test
  void createsNewClientCallWithCustomTimeout_whenTimeoutDiffers() throws Exception {
    // Set client default to 12s, but getPayloadV1 uses GET_PAYLOAD_TIMEOUT = 2s
    createClientWithCallTimeout(Duration.ofSeconds(12));

    mockWebServer.enqueue(new MockResponse().setBody(VALID_JSONRPC_RESPONSE));

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);

    assertThat(response).isNotNull();
    verify(spyOkHttpClient).newBuilder();
  }

  @Test
  void requestIsSuccessfullyEnqueued_regardlessOfTimeoutBranch() throws Exception {
    createClientWithCallTimeout(Duration.ofSeconds(12));

    mockWebServer.enqueue(new MockResponse().setBody(VALID_JSONRPC_RESPONSE));

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    final RecordedRequest recorded = mockWebServer.takeRequest();
    assertThat(recorded.getBody().readUtf8()).contains("engine_getPayloadV1");
  }

  @Test
  void multipleRequestsWithDifferentTimeouts_usesCorrectBranch() throws Exception {
    // exchangeCapabilities uses EXCHANGE_CAPABILITIES_TIMEOUT = 1s
    // getPayloadV1 uses GET_PAYLOAD_TIMEOUT = 2s
    // Set default to 1s to match exchangeCapabilities
    createClientWithCallTimeout(Duration.ofSeconds(1));

    // First request: exchangeCapabilities with 1s timeout (matches default)
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[\"engine_newPayloadV1\"]}"));

    final SafeFuture<Response<List<String>>> capabilitiesFuture =
        engineClient.exchangeCapabilities(List.of("engine_newPayloadV1"));
    capabilitiesFuture.get(5, TimeUnit.SECONDS);

    // exchangeCapabilities timeout (1s) matches default → uses existing client
    verify(spyOkHttpClient).newCall(any());
    verify(spyOkHttpClient, never()).newBuilder();

    reset(spyOkHttpClient);

    // Second request: getPayloadV1 with 2s timeout (different from default 1s)
    mockWebServer.enqueue(new MockResponse().setBody(VALID_JSONRPC_RESPONSE));

    final SafeFuture<Response<ExecutionPayloadV1>> payloadFuture =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    payloadFuture.get(5, TimeUnit.SECONDS);

    // getPayloadV1 timeout (2s) differs from default (1s) → creates new builder
    verify(spyOkHttpClient).newBuilder();
  }

  private void createClientWithCallTimeout(final Duration callTimeout) {
    final OkHttpClient httpClient = new OkHttpClient.Builder().callTimeout(callTimeout).build();
    spyOkHttpClient = spy(httpClient);
    engineClient =
        new OkHttpHttpExecutionEngineClient(
            spyOkHttpClient,
            mockWebServer.url("/").toString(),
            eventLog,
            timeProvider,
            executionClientEventsPublisher);
  }
}

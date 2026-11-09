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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class OkHttpWebSocketExecutionEngineClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);
  private final EventLogger eventLog = mock(EventLogger.class);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);
  private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

  private OkHttpWebSocketExecutionEngineClient engineClient;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer.start();
    final String wsUrl =
        "ws://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort() + "/";

    engineClient =
        new OkHttpWebSocketExecutionEngineClient(
            httpClient, wsUrl, eventLog, timeProvider, executionClientEventsPublisher);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void successfulRequest_returnsNullResult() throws Exception {
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response).isNotNull();
    assertThat(response.payload()).isNull();
  }

  @Test
  void successfulRequest_reportsExecutionClientOnline() throws Exception {
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientIsOnline();
    verify(executionClientEventsPublisher).onAvailabilityUpdated(true);
  }

  @Test
  void jsonRpcError_returnsErrorResponse() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).contains("Method not found").contains("-32601");
  }

  @Test
  void jsonRpcError_forCriticalMethod_reportsFailure() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientRequestFailed(any(Exception.class), eq(false));
  }

  @Test
  void nonCriticalMethod_doesNotReportErrorOnJsonRpcFailure() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<List<String>>>
        future = engineClient.exchangeCapabilities(List.of("engine_newPayloadV1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog, never()).executionClientRequestFailed(any(), any(Boolean.class));
  }

  @Test
  void multipleRequests_matchResponsesById() throws Exception {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMainnetFulu());
    final ExecutionPayloadV1 executionPayloadV1resp1 =
        ExecutionPayloadV1.fromInternalExecutionPayload(dataStructureUtil.randomExecutionPayload());
    final ExecutionPayloadV1 executionPayloadV1resp2 =
        ExecutionPayloadV1.fromInternalExecutionPayload(dataStructureUtil.randomExecutionPayload());

    final String jsonResponse1 =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"
            + objectMapper.writeValueAsString(executionPayloadV1resp1)
            + "}";
    final String jsonResponse2 =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":"
            + objectMapper.writeValueAsString(executionPayloadV1resp2)
            + "}";

    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(final WebSocket webSocket, final String text) {
                    if (text.contains("\"id\":2")) {
                      // Respond to id 2 before id 1
                      webSocket.send(jsonResponse2);
                      webSocket.send(jsonResponse1);
                    }
                  }
                }));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future1 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future2 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));

    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response1 =
        future1.get(30, TimeUnit.SECONDS);
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response2 =
        future2.get(30, TimeUnit.SECONDS);

    assertThat(response1.payload()).isEqualTo(executionPayloadV1resp1);
    assertThat(response2.payload()).isEqualTo(executionPayloadV1resp2);
  }

  @Test
  void webSocketDisconnect_failsPendingRequests() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(
                      @NotNull final WebSocket webSocket, @NotNull final String text) {
                    // Don't respond - close the connection instead
                    webSocket.close(1000, "Server closing");
                  }
                }));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).isNotEmpty();
  }

  @Test
  void reconnectsAfterDisconnect() throws Exception {
    // First connection: will be closed by server
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onOpen(
                      @NotNull final WebSocket webSocket, @NotNull final Response response) {
                    webSocket.close(1000, "Server closing");
                  }
                }));

    // Second connection: responds normally
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}"));

    // First request triggers connection, which gets closed
    // Give time for the close to propagate
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future1 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response1 =
        future1.get(30, TimeUnit.SECONDS);
    assertThat(response1.errorMessage()).isNotEmpty();

    // Wait until the client observes the disconnect so the next request reconnects
    // deterministically
    waitUntilDisconnected();

    // Second request should reconnect
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future2 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response2 =
        future2.get(30, TimeUnit.SECONDS);
    assertThat(response2).isNotNull();
    assertThat(response2.errorMessage()).isNull();
  }

  private void waitUntilDisconnected() throws Exception {
    final Field connectedField =
        OkHttpWebSocketExecutionEngineClient.class.getDeclaredField("connected");
    connectedField.setAccessible(true);
    final AtomicBoolean connected = (AtomicBoolean) connectedField.get(engineClient);
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (connected.get()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Client did not observe disconnect within 10 seconds");
      }
      Thread.sleep(10);
    }
  }

  @Test
  void requestBodyContainsCorrectMethod() throws Exception {
    final AtomicReference<String> receivedMessage = new AtomicReference<>();
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(
                      @NotNull final WebSocket webSocket, @NotNull final String text) {
                    receivedMessage.set(text);
                    webSocket.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
                  }
                }));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    assertThat(receivedMessage.get()).contains("engine_getPayloadV1");
  }

  @Test
  void unexpectedExceptionOnSend_returnsFailedFuture() throws Exception {
    // First establish a connection
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));
    engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1")).get(5, TimeUnit.SECONDS);

    // Replace the internal webSocket with a mock that throws on send()
    final WebSocket throwingWebSocket = mock(WebSocket.class);
    final RuntimeException expectedException = new RuntimeException("Unexpected send failure");
    when(throwingWebSocket.send(anyString())).thenThrow(expectedException);

    final Field wsField = OkHttpWebSocketExecutionEngineClient.class.getDeclaredField("webSocket");
    wsField.setAccessible(true);
    wsField.set(engineClient, throwingWebSocket);

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(() -> future.get())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasRootCauseMessage("Unexpected send failure");

    verifyRequestWasRemovedFromPendingRequests();
  }

  private void verifyRequestWasRemovedFromPendingRequests()
      throws NoSuchFieldException, IllegalAccessException {
    final Field pendingField =
        OkHttpWebSocketExecutionEngineClient.class.getDeclaredField("pendingRequests");
    pendingField.setAccessible(true);
    @SuppressWarnings("unchecked")
    final ConcurrentHashMap<Long, ?> pendingRequests =
        (ConcurrentHashMap<Long, ?>) pendingField.get(engineClient);
    assertThat(pendingRequests).isEmpty();
  }

  private static MockResponse webSocketResponse(final String s) {
    return new MockResponse()
        .withWebSocketUpgrade(
            new WebSocketListener() {
              @Override
              public void onMessage(
                  @NotNull final WebSocket webSocket, @NotNull final String text) {
                webSocket.send(s);
              }

              @Override
              public void onMessage(
                  @NonNull final WebSocket webSocket, @NonNull final ByteString bytes) {
                webSocket.send(ByteString.encodeUtf8(s));
              }
            });
  }
}

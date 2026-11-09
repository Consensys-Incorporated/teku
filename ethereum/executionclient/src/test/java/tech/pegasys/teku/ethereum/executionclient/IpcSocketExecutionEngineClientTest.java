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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class IpcSocketExecutionEngineClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);
  private final EventLogger eventLog = mock(EventLogger.class);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  @TempDir Path tempDir;

  private Path socketPath;
  private ServerSocketChannel serverChannel;
  private IpcSocketExecutionEngineClient engineClient;

  @BeforeEach
  void setUp() throws Exception {
    socketPath = tempDir.resolve("test.ipc");
    serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    serverChannel.bind(UnixDomainSocketAddress.of(socketPath));

    engineClient =
        new IpcSocketExecutionEngineClient(
            DelayedExecutorAsyncRunner.create(),
            socketPath,
            eventLog,
            timeProvider,
            executionClientEventsPublisher);
  }

  @AfterEach
  void tearDown() throws Exception {
    serverChannel.close();
    Files.deleteIfExists(socketPath);
  }

  @Test
  void successfulRequest_returnsNullResult() throws Exception {
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(out, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
        });

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);

    assertThat(response).isNotNull();
    assertThat(response.payload()).isNull();
    assertThat(response.errorMessage()).isNull();
  }

  @Test
  void successfulRequest_reportsExecutionClientOnline() throws Exception {
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(out, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
        });

    engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1")).get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientIsOnline();
    verify(executionClientEventsPublisher).onAvailabilityUpdated(true);
  }

  @Test
  void jsonRpcError_returnsErrorResponse() throws Exception {
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(
              out,
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
        });

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).contains("Method not found").contains("-32601");
  }

  @Test
  void jsonRpcError_forCriticalMethod_reportsFailure() throws Exception {
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(
              out,
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
        });

    engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1")).get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientRequestFailed(any(Exception.class), eq(false));
  }

  @Test
  void nonCriticalMethod_doesNotReportErrorOnJsonRpcFailure() throws Exception {
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(
              out,
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
        });

    engineClient.exchangeCapabilities(List.of("engine_newPayloadV1")).get(5, TimeUnit.SECONDS);

    verify(eventLog, never()).executionClientRequestFailed(any(), any(Boolean.class));
  }

  @Test
  void multipleRequests_matchResponsesById() throws Exception {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMainnetFulu());
    final ExecutionPayloadV1 payload1 =
        ExecutionPayloadV1.fromInternalExecutionPayload(dataStructureUtil.randomExecutionPayload());
    final ExecutionPayloadV1 payload2 =
        ExecutionPayloadV1.fromInternalExecutionPayload(dataStructureUtil.randomExecutionPayload());

    final String jsonResponse1 =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"
            + objectMapper.writeValueAsString(payload1)
            + "}";
    final String jsonResponse2 =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":"
            + objectMapper.writeValueAsString(payload2)
            + "}";

    startServer(
        (reader, out) -> {
          readLine(reader);
          final String secondRequest = readLine(reader);
          if (secondRequest != null) {
            // Respond to id 2 before id 1
            writeLine(out, jsonResponse2);
            writeLine(out, jsonResponse1);
          }
        });

    final SafeFuture<Response<ExecutionPayloadV1>> future1 =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final SafeFuture<Response<ExecutionPayloadV1>> future2 =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));

    final Response<ExecutionPayloadV1> response1 = future1.get(5, TimeUnit.SECONDS);
    final Response<ExecutionPayloadV1> response2 = future2.get(5, TimeUnit.SECONDS);

    assertThat(response1.payload()).isEqualTo(payload1);
    assertThat(response2.payload()).isEqualTo(payload2);
  }

  @Test
  void disconnect_failsPendingRequests() throws Exception {
    final CountDownLatch requestReceived = new CountDownLatch(1);
    startServer(
        (reader, out) -> {
          readLine(reader);
          requestReceived.countDown();
          // Close without responding
        });

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    requestReceived.await(5, TimeUnit.SECONDS);

    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);
    assertThat(response.errorMessage()).isNotEmpty();
  }

  @Test
  void reconnectsAfterDisconnect() throws Exception {
    // First connection: accept then close immediately
    startServer(
        (reader, out) -> {
          // Close the connection without responding
        });

    // Trigger first connection attempt
    final SafeFuture<Response<ExecutionPayloadV1>> future1 =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response1 = future1.get(5, TimeUnit.SECONDS);
    assertThat(response1.errorMessage()).isNotEmpty();

    // Start a new server handler for the second connection
    startServer(
        (reader, out) -> {
          readLine(reader);
          writeLine(out, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}");
        });

    // Second request should reconnect
    final SafeFuture<Response<ExecutionPayloadV1>> future2 =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));
    final Response<ExecutionPayloadV1> response2 = future2.get(5, TimeUnit.SECONDS);
    assertThat(response2).isNotNull();
    assertThat(response2.errorMessage()).isNull();
  }

  @Test
  void requestBodyContainsCorrectMethod() throws Exception {
    final AtomicReference<String> receivedMessage = new AtomicReference<>();
    startServer(
        (reader, out) -> {
          receivedMessage.set(readLine(reader));
          writeLine(out, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
        });

    engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1")).get(5, TimeUnit.SECONDS);

    assertThat(receivedMessage.get()).contains("engine_getPayloadV1");
  }

  @Test
  void requestBody_isRawJsonRpc_withoutHttpFraming() throws Exception {
    final AtomicReference<String> receivedMessage = new AtomicReference<>();
    startServer(
        (reader, out) -> {
          receivedMessage.set(readLine(reader));
          writeLine(out, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
        });

    engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1")).get(5, TimeUnit.SECONDS);

    final JsonNode request = objectMapper.readTree(receivedMessage.get());
    assertThat(request.get("jsonrpc").asText()).isEqualTo("2.0");
    assertThat(request.get("method").asText()).isEqualTo("engine_getPayloadV1");
    assertThat(request.get("id").asLong()).isEqualTo(1L);
    assertThat(request.has("params")).isTrue();
  }

  @Test
  void connectionFailure_returnsErrorResponse() throws Exception {
    // Close the server so connection fails
    serverChannel.close();
    Files.deleteIfExists(socketPath);

    final SafeFuture<Response<ExecutionPayloadV1>> future =
        engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final Response<ExecutionPayloadV1> response = future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).isNotEmpty();
  }

  private void startServer(final BiConsumer<BufferedReader, OutputStream> handler) {
    final Thread serverThread =
        new Thread(
            () -> {
              try (SocketChannel clientChannel = serverChannel.accept();
                  BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(
                              Channels.newInputStream(clientChannel), StandardCharsets.UTF_8))) {
                final OutputStream out = Channels.newOutputStream(clientChannel);
                handler.accept(reader, out);
              } catch (final Exception e) {
                // Server closed
              }
            },
            "ipc-test-server");
    serverThread.setDaemon(true);
    serverThread.start();
  }

  private static String readLine(final BufferedReader reader) {
    try {
      return reader.readLine();
    } catch (final Exception e) {
      return null;
    }
  }

  private static void writeLine(final OutputStream out, final String line) {
    try {
      out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}

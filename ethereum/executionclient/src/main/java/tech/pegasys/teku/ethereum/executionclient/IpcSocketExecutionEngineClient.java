/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.JsonRpcRequest;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class IpcSocketExecutionEngineClient extends AbstractExecutionEngineClient {

  public static final String IPC_READER_ASYNC_RUNNER_NAME = "ipcreader";
  public static final int IPC_READER_MAX_THREADS = 1;

  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final UnixDomainSocketFactory socketFactory;
  private final ConcurrentHashMap<Long, SafeFuture<JsonNode>> pendingRequests =
      new ConcurrentHashMap<>();
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private volatile Socket socket;
  private volatile OutputStream outputStream;

  IpcSocketExecutionEngineClient(
      final AsyncRunner asyncRunner,
      final Path ipcPath,
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    super(eventLog, timeProvider, executionClientEventsPublisher);
    this.asyncRunner = asyncRunner;
    this.socketFactory = new UnixDomainSocketFactory(ipcPath);
  }

  private void ensureConnected() throws IOException {
    if (connected.get()) {
      return;
    }
    synchronized (this) {
      if (connected.get()) {
        return;
      }
      final Socket newSocket = socketFactory.createSocket();
      try {
        newSocket.connect(null);
      } catch (final IOException e) {
        newSocket.close();
        throw e;
      }
      socket = newSocket;
      outputStream = newSocket.getOutputStream();
      connected.set(true);
      startReaderThread();
    }
  }

  private void startReaderThread() {
    final Socket currentSocket = this.socket;
    asyncRunner
        .runAsync(
            () -> {
              try (final BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(
                          currentSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  processResponse(line);
                }
              } catch (final Exception e) {
                if (connected.get()) {
                  LOG.warn("IPC reader error", e);
                }
              } finally {
                handleDisconnect(currentSocket, new Exception("IPC connection closed"));
              }
            })
        .finishError(LOG);
  }

  private void processResponse(final String line) {
    try {
      final JsonNode jsonResponse = objectMapper.readTree(line);
      final JsonNode idNode = jsonResponse.get("id");
      if (idNode == null || idNode.isNull()) {
        LOG.warn("Received IPC message without id");
        return;
      }
      final long id = idNode.asLong();
      final SafeFuture<JsonNode> future = pendingRequests.remove(id);
      if (future != null) {
        future.complete(jsonResponse);
      } else {
        LOG.warn("Received IPC response for unknown request id: {}", id);
      }
    } catch (final Exception e) {
      LOG.error("Error processing IPC message", e);
    }
  }

  @SuppressWarnings({"ReferenceComparison", "ReferenceEquality"})
  private void handleDisconnect(final Socket disconnectedSocket, final Throwable cause) {
    synchronized (this) {
      if (socket != disconnectedSocket) {
        // Connection has already been replaced, close stale socket
        closeSocket(disconnectedSocket);
        return;
      }
      connected.set(false);
      pendingRequests.forEach((id, future) -> future.completeExceptionally(cause));
      pendingRequests.clear();
      closeSocket(disconnectedSocket);
    }
  }

  private void closeSocket(final Socket socketToClose) {
    try {
      if (socketToClose != null) {
        socketToClose.close();
      }
    } catch (final IOException e) {
      LOG.debug("Error closing IPC socket", e);
    }
  }

  @Override
  protected <T> SafeFuture<Response<T>> doRequest(
      final String method,
      final List<Object> params,
      final JavaType resultType,
      final Duration timeout) {
    final boolean isCritical = isCriticalMethod(method);

    final JsonRpcRequest request = buildRequest(method, params);
    final long requestId = request.id();

    final byte[] requestBytes;
    try {
      requestBytes = writeRequestAsByteArray(request);
    } catch (final Exception e) {
      handleError(isCritical, e, false);
      return SafeFuture.completedFuture(Response.fromErrorMessage(getMessageOrSimpleName(e)));
    }

    try {
      ensureConnected();
    } catch (final Exception e) {
      handleError(isCritical, e, false);
      return SafeFuture.completedFuture(Response.fromErrorMessage(getMessageOrSimpleName(e)));
    }

    final SafeFuture<JsonNode> jsonFuture = new SafeFuture<>();
    pendingRequests.put(requestId, jsonFuture);

    try {
      synchronized (this) {
        outputStream.write(requestBytes);
        outputStream.write('\n');
        outputStream.flush();
      }
    } catch (final IOException e) {
      pendingRequests.remove(requestId);
      handleDisconnect(socket, e);
      handleError(isCritical, e, false);
      return SafeFuture.completedFuture(Response.fromErrorMessage(getMessageOrSimpleName(e)));
    }

    return jsonFuture
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .<Response<T>>thenApply(
            jsonResponse -> {
              try {
                final JsonNode errorNode = jsonResponse.get("error");
                if (errorNode != null && !errorNode.isNull()) {
                  final String formattedError = getFormattedError(errorNode);
                  if (isCritical) {
                    eventLog.executionClientRequestFailed(new Exception(formattedError), false);
                  }
                  return Response.fromErrorMessage(formattedError);
                }

                handleSuccess(isCritical);
                final JsonNode resultNode = jsonResponse.get("result");
                final T result =
                    resultNode == null || resultNode.isNull()
                        ? null
                        : objectMapper.treeToValue(resultNode, resultType);
                return Response.fromPayloadReceivedAsJson(result);
              } catch (final Exception e) {
                handleError(isCritical, e, false);
                return Response.fromErrorMessage(getMessageOrSimpleName(e));
              }
            })
        .exceptionally(
            throwable -> {
              pendingRequests.remove(requestId);
              handleError(isCritical, throwable, false);
              return Response.fromErrorMessage(getMessageOrSimpleName(throwable));
            });
  }

  private static String getFormattedError(final JsonNode errorNode) {
    final int code = errorNode.path("code").asInt();
    final String msg = errorNode.path("message").asText();
    return String.format("JSON-RPC error: %s (%d): %s", describeJsonRpcErrorCode(code), code, msg);
  }
}

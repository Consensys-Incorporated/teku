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

import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.JsonRpcRequest;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class OkHttpWebSocketExecutionEngineClient extends AbstractExecutionEngineClient {

  private static final Logger LOG = LogManager.getLogger();

  private final OkHttpClient httpClient;
  private final Request wsRequest;
  private final ConcurrentHashMap<Long, SafeFuture<JsonNode>> pendingRequests =
      new ConcurrentHashMap<>();
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private volatile WebSocket webSocket;

  OkHttpWebSocketExecutionEngineClient(
      final OkHttpClient httpClient,
      final String endpoint,
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    super(eventLog, timeProvider, executionClientEventsPublisher);
    this.httpClient = httpClient;
    this.wsRequest = new Request.Builder().url(endpoint).build();
  }

  private void ensureConnected() {
    if (connected.get()) {
      return;
    }
    synchronized (this) {
      if (connected.get()) {
        return;
      }
      webSocket = httpClient.newWebSocket(wsRequest, new JsonRpcWebSocketListener());
      connected.set(true);
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

    final String requestBody;
    try {
      requestBody = writeRequestAsString(request);
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
      final boolean sent = webSocket.send(requestBody);
      // This branch is for expected errors like disconnected, not able to write to socket, etc (all
      // handled by library)
      if (!sent) {
        pendingRequests.remove(requestId);
        final String errorMsg = "Failed to send WebSocket message";
        handleError(isCritical, new Exception(errorMsg), false);
        return SafeFuture.completedFuture(Response.fromErrorMessage(errorMsg));
      }
    } catch (final Exception e) {
      // This branch is unexpected so we are raising the error
      pendingRequests.remove(requestId);
      return SafeFuture.failedFuture(e);
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
              final boolean couldBeAuthError =
                  ExceptionUtil.hasCause(throwable, ProtocolException.class);
              handleError(isCritical, throwable, couldBeAuthError);
              return Response.fromErrorMessage(getMessageOrSimpleName(throwable));
            });
  }

  private static String getFormattedError(final JsonNode errorNode) {
    final int code = errorNode.path("code").asInt();
    final String msg = errorNode.path("message").asText();
    return String.format("JSON-RPC error: %s (%d): %s", describeJsonRpcErrorCode(code), code, msg);
  }

  private class JsonRpcWebSocketListener extends WebSocketListener {

    @Override
    public void onMessage(final WebSocket ws, final String text) {
      onMessageInternal(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onMessage(final WebSocket ws, final ByteString bytes) {
      onMessageInternal(bytes.toByteArray());
    }

    void onMessageInternal(final byte[] bytes) {
      try {
        final JsonNode jsonResponse = objectMapper.readTree(bytes);
        final JsonNode idNode = jsonResponse.get("id");
        if (idNode == null || idNode.isNull()) {
          LOG.warn("Received WebSocket message without id");
          return;
        }
        final long id = idNode.asLong();
        final SafeFuture<JsonNode> future = pendingRequests.remove(id);
        if (future != null) {
          future.complete(jsonResponse);
        } else {
          LOG.warn("Received WebSocket response for unknown request id: {}", id);
        }
      } catch (final Exception e) {
        LOG.error("Error processing WebSocket message", e);
      }
    }

    @Override
    public void onFailure(final WebSocket ws, final Throwable t, final okhttp3.Response response) {
      LOG.warn("WebSocket connection failure", t);
      handleDisconnect(ws, t);
    }

    @Override
    public void onClosing(final WebSocket ws, final int code, final String reason) {
      ws.close(code, reason);
    }

    @Override
    public void onClosed(final WebSocket ws, final int code, final String reason) {
      LOG.debug("WebSocket connection closed: {} {}", code, reason);
      handleDisconnect(ws, new Exception("WebSocket closed: " + code + " " + reason));
    }

    private void handleDisconnect(final WebSocket ws, final Throwable cause) {
      synchronized (OkHttpWebSocketExecutionEngineClient.this) {
        if (!ws.equals(webSocket)) {
          LOG.debug("Ignoring disconnect from stale WebSocket");
          return;
        }
        connected.set(false);
        pendingRequests.forEach((id, future) -> future.completeExceptionally(cause));
        pendingRequests.clear();
      }
    }
  }
}

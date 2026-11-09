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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class OkHttpHttpExecutionEngineClient extends AbstractExecutionEngineClient {

  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

  private final OkHttpClient httpClient;
  private final HttpUrl endpointUrl;

  OkHttpHttpExecutionEngineClient(
      final OkHttpClient httpClient,
      final String endpoint,
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    super(eventLog, timeProvider, executionClientEventsPublisher);
    this.httpClient = httpClient;
    this.endpointUrl = HttpUrl.get(endpoint);
  }

  @Override
  protected <T> SafeFuture<Response<T>> doRequest(
      final String method,
      final List<Object> params,
      final JavaType resultType,
      final Duration timeout) {
    final boolean isCritical = isCriticalMethod(method);

    final byte[] requestBodyBytes;
    try {
      requestBodyBytes = writeRequestAsByteArray(buildRequest(method, params));
    } catch (final Exception e) {
      handleError(isCritical, e, false);
      return SafeFuture.completedFuture(Response.fromErrorMessage(getMessageOrSimpleName(e)));
    }

    final Request httpRequest =
        new Request.Builder()
            .url(endpointUrl)
            .post(RequestBody.create(requestBodyBytes, JSON_MEDIA_TYPE))
            .build();

    final SafeFuture<Response<T>> future = new SafeFuture<>();
    final Call call;
    if (timeout.toMillis() != httpClient.callTimeoutMillis()) {
      call = callWithCustomTimeout(timeout, httpRequest);
    } else {
      call = httpClient.newCall(httpRequest);
    }

    call.enqueue(
        new Callback() {
          @Override
          public void onFailure(final Call call, final IOException e) {
            handleError(isCritical, e, false);
            future.complete(Response.fromErrorMessage(getMessageOrSimpleName(e)));
          }

          @Override
          public void onResponse(final Call call, final okhttp3.Response httpResponse) {
            try (httpResponse) {
              final ResponseBody body = httpResponse.body();
              if (!httpResponse.isSuccessful() || body == null) {
                final boolean couldBeAuthError =
                    httpResponse.code() == 401 || httpResponse.code() == 403;
                final String errorMsg =
                    body != null
                        ? body.string()
                        : (httpResponse.code() + ": " + httpResponse.message());
                handleError(isCritical, new Exception(errorMsg), couldBeAuthError);
                future.complete(Response.fromErrorMessage(errorMsg));
                return;
              }

              final JsonNode jsonResponse = objectMapper.readTree(body.byteStream());
              final JsonNode errorNode = jsonResponse.get("error");
              if (errorNode != null && !errorNode.isNull()) {
                final int code = errorNode.path("code").asInt();
                final String msg = errorNode.path("message").asText();
                final String formattedError =
                    String.format(
                        "JSON-RPC error: %s (%d): %s", describeJsonRpcErrorCode(code), code, msg);
                if (isCritical) {
                  eventLog.executionClientRequestFailed(new Exception(formattedError), false);
                }
                future.complete(Response.fromErrorMessage(formattedError));
                return;
              }

              handleSuccess(isCritical);
              final JsonNode resultNode = jsonResponse.get("result");
              final T result =
                  resultNode == null || resultNode.isNull()
                      ? null
                      : objectMapper.treeToValue(resultNode, resultType);
              future.complete(Response.fromPayloadReceivedAsJson(result));
            } catch (final Exception e) {
              handleError(isCritical, e, false);
              future.complete(Response.fromErrorMessage(getMessageOrSimpleName(e)));
            }
          }
        });

    return future;
  }

  private Call callWithCustomTimeout(final Duration timeout, final Request httpRequest) {
    return httpClient
        .newBuilder()
        .callTimeout(timeout)
        .readTimeout(timeout)
        .build()
        .newCall(httpRequest);
  }
}

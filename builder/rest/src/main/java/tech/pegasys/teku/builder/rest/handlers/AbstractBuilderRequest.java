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

package tech.pegasys.teku.builder.rest.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.builder.rest.BuilderApiMethod;
import tech.pegasys.teku.builder.rest.ResponseHandler;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public abstract class AbstractBuilderRequest {

  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
  private static final RequestBody EMPTY_REQUEST_BODY = RequestBody.create(new byte[0], null);

  private static final Logger LOG = LogManager.getLogger();

  private final HttpUrl baseEndpoint;
  private final OkHttpClient httpClient;

  protected AbstractBuilderRequest(final HttpUrl baseEndpoint, final OkHttpClient httpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = httpClient;
  }

  protected HttpUrl buildUrl(
      final BuilderApiMethod apiMethod, final Map<String, String> urlParams) {
    return baseEndpoint.resolve(apiMethod.getPath(urlParams));
  }

  protected <T, TObject> Optional<T> postJson(
      final BuilderApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> headers,
      final TObject requestBodyObj,
      final SerializableTypeDefinition<TObject> objectTypeDefinition,
      final ResponseHandler<T> responseHandler,
      final Optional<Duration> maybeTimeout) {
    final String requestBody;
    try {
      requestBody = JsonUtil.serialize(requestBodyObj, objectTypeDefinition);
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException("Failed to serialize request body to JSON", ex);
    }
    final Request.Builder builder =
        new Request.Builder()
            .url(buildUrl(apiMethod, urlParams))
            .post(RequestBody.create(requestBody, APPLICATION_JSON));
    headers.forEach(builder::addHeader);
    return executeCall(builder.build(), responseHandler, maybeTimeout);
  }

  protected <T> Optional<T> postEmpty(
      final BuilderApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> headers,
      final ResponseHandler<T> responseHandler,
      final Optional<Duration> maybeTimeout) {
    final Request.Builder builder =
        new Request.Builder().url(buildUrl(apiMethod, urlParams)).post(EMPTY_REQUEST_BODY);
    headers.forEach(builder::addHeader);
    return executeCall(builder.build(), responseHandler, maybeTimeout);
  }

  protected <T> Optional<T> postOctetStream(
      final BuilderApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> headers,
      final byte[] body,
      final ResponseHandler<T> responseHandler) {
    final Request.Builder builder =
        new Request.Builder()
            .url(buildUrl(apiMethod, urlParams))
            .post(RequestBody.create(body, OCTET_STREAM));
    headers.forEach(builder::addHeader);
    return executeCall(builder.build(), responseHandler, Optional.empty());
  }

  private <T> Optional<T> executeCall(
      final Request request,
      final ResponseHandler<T> responseHandler,
      final Optional<Duration> maybeTimeout) {
    final Call call = httpClient.newCall(request);
    maybeTimeout.ifPresent(
        timeout -> call.timeout().timeout(timeout.toMillis(), TimeUnit.MILLISECONDS));
    try (final Response response = call.execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());
      return responseHandler.handleResponse(request, response);
    } catch (final IOException ex) {
      throw new UncheckedIOException("Error communicating with builder: " + ex.getMessage(), ex);
    }
  }
}

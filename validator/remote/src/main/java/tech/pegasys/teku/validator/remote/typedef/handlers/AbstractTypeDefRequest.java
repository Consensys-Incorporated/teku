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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public abstract class AbstractTypeDefRequest {
  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
  private static final Logger LOG = LogManager.getLogger();
  private final HttpUrl baseEndpoint;
  private final OkHttpClient httpClient;

  public AbstractTypeDefRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = okHttpClient;
  }

  protected HttpUrl.Builder urlBuilder(
      final ValidatorApiMethod apiMethod, final Map<String, String> urlParams) {
    checkNotNull(apiMethod, "apiMethod not defined");
    final HttpUrl httpUrl = baseEndpoint.resolve(apiMethod.getPath(urlParams));
    checkNotNull(httpUrl, "Could not create endpoint builder from baseEndpoint: %s", apiMethod);
    return httpUrl.newBuilder();
  }

  protected Request.Builder requestBuilder() {
    final Request.Builder builder = new Request.Builder();

    if (!baseEndpoint.username().isEmpty()) {
      builder.header(
          "Authorization",
          Credentials.basic(baseEndpoint.encodedUsername(), baseEndpoint.encodedPassword()));
    }
    return builder;
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod, final ResponseHandler<T> responseHandler) {
    return get(apiMethod, emptyMap(), emptyMap(), responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, emptyMap(), queryParams, responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, urlParams, queryParams, emptyMap(), emptyMap(), responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Map<String, String> encodedQueryParams,
      final Map<String, String> headers,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }

    // The encodedQueryParams are considered to be encoded already
    // and should not be encoded again. This is useful to prevent
    // the comma in an array of values (e.g. id=1,2,3) from being
    // encoded.
    if (encodedQueryParams != null && !encodedQueryParams.isEmpty()) {
      encodedQueryParams.forEach(httpUrlBuilder::addEncodedQueryParameter);
    }

    final Request.Builder builder = requestBuilder().url(httpUrlBuilder.build());
    if (headers != null && !headers.isEmpty()) {
      headers.forEach(builder::addHeader);
    }
    return executeCall(builder.build(), responseHandler);
  }

  protected <T, TObject> Optional<T> postJson(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final TObject requestBodyObj,
      final SerializableTypeDefinition<TObject> objectTypeDefinition,
      final ResponseHandler<T> responseHandler) {
    return postJson(
        apiMethod,
        urlParams,
        emptyMap(),
        emptyMap(),
        requestBodyObj,
        objectTypeDefinition,
        responseHandler);
  }

  protected <T, TObject> Optional<T> postJson(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Map<String, String> headers,
      final TObject requestBodyObj,
      final SerializableTypeDefinition<TObject> objectTypeDefinition,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }
    final String requestBody;
    final Request request;
    try {
      final Request.Builder builder = requestBuilder();
      headers.forEach(builder::addHeader);
      requestBody = JsonUtil.serialize(requestBodyObj, objectTypeDefinition);
      request =
          builder
              .url(httpUrlBuilder.build())
              .post(RequestBody.create(requestBody, APPLICATION_JSON))
              .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return executeCall(request, responseHandler);
  }

  protected <T> Optional<T> postOctetStream(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> headers,
      final byte[] objectBytes,
      final ResponseHandler<T> responseHandler) {
    return postOctetStream(apiMethod, urlParams, emptyMap(), headers, objectBytes, responseHandler);
  }

  protected <T> Optional<T> postOctetStream(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Map<String, String> headers,
      final byte[] objectBytes,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }
    final Request.Builder builder = requestBuilder();
    headers.forEach(builder::addHeader);
    final Request request =
        builder
            .url(httpUrlBuilder.build())
            .post(RequestBody.create(objectBytes, OCTET_STREAM))
            .build();

    return executeCall(request, responseHandler);
  }

  private <T> Optional<T> executeCall(
      final Request request, final ResponseHandler<T> responseHandler) {
    try (final Response response = httpClient.newCall(request).execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());
      return responseHandler.handleResponse(request, response);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Error communicating with Beacon Node API: " + e.getMessage(), e);
    }
  }
}

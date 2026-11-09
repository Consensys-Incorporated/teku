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

package tech.pegasys.teku.infrastructure.restapi;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class StubRestApiRequest implements RestApiRequest {
  private static final Logger LOG = LogManager.getLogger();
  private static final int CODE_NOT_SET = -1;
  private Object requestBody = null;
  private int responseCode = CODE_NOT_SET;
  private Object responseObject = null;
  private Optional<Throwable> responseError = Optional.empty();
  private CacheLength cacheLength = null;
  private final Map<String, String> pathParameters = new HashMap<>();
  private final Map<String, String> queryParameters = new HashMap<>();
  private final Map<String, String> optionalQueryParameters = new HashMap<>();
  private final Map<String, List<String>> listQueryParameters = new HashMap<>();
  private final Map<Integer, String> contentTypeMap = new HashMap<>();
  private final Map<String, String> responseHeaders = new HashMap<>();
  private final Map<String, String> requestHeaders = new HashMap<>();

  private final EndpointMetadata metadata;

  public StubRestApiRequest(final EndpointMetadata metadata) {
    this.metadata = metadata;
  }

  public boolean responseCodeSet() {
    return responseCode != CODE_NOT_SET;
  }

  public int getResponseCode() {
    return responseCode;
  }

  public CacheLength getCacheLength() {
    return cacheLength;
  }

  public <T> void setRequestBody(final T requestBody) {
    assertThat(this.requestBody).isNull();
    this.requestBody = requestBody;
  }

  @Override
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() {
    return (T) requestBody;
  }

  @Override
  public <T> Optional<T> getOptionalRequestBody() throws JsonProcessingException {
    return Optional.ofNullable(getRequestBody());
  }

  @Override
  public void respondOk(final Object response) throws JsonProcessingException {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    assertThat(this.responseObject).isNull();
    this.responseCode = SC_OK;
    this.responseObject = response;
    verifyResponseMatchesMetadata();
  }

  private void verifyResponseMatchesMetadata() {
    if (responseObject == null) {
      assertThat(metadata.isNoContentResponse(responseCode))
          .withFailMessage("No content provided for " + responseCode + " when content is required.")
          .isTrue();
    } else {
      metadata.createResponseMetadata(responseCode, Optional.empty(), responseObject);
    }
  }

  @Override
  public void respondAsync(final SafeFuture<AsyncApiResponse> futureResponse) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    final AsyncApiResponse response;
    try {
      response = safeJoin(futureResponse);
    } catch (final CompletionException e) {
      responseError = Optional.of(e.getCause());
      return;
    } catch (final Throwable e) {
      responseError = Optional.of(e);
      return;
    }
    responseCode = response.getResponseCode();
    if (response.getResponseBody().isEmpty()) {
      LOG.warn("Response body was empty on async response");
    }
    responseObject = response.getResponseBody().orElse(null);
    verifyResponseMatchesMetadata();
  }

  @Override
  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    assertThat(this.cacheLength).isNull();
    this.cacheLength = cacheLength;
    respondOk(response);
  }

  @Override
  public void respondError(final int statusCode, final String message) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    assertThat(this.responseObject).isNull();
    responseObject = new HttpErrorResponse(statusCode, message);
    this.responseCode = statusCode;
    verifyResponseMatchesMetadata();
  }

  @Override
  public void respondWithCode(final int statusCode) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    this.responseCode = statusCode;
    verifyResponseMatchesMetadata();
  }

  @Override
  public void respondWithUndocumentedCode(final int statusCode) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    this.responseCode = statusCode;
  }

  @Override
  public void respondWithCode(final int statusCode, final CacheLength cacheLength) {
    assertThat(this.cacheLength).isNull();
    this.cacheLength = cacheLength;
    respondWithCode(statusCode);
  }

  public void setPathParameter(final String parameter, final String value) {
    assertThat(this.pathParameters.containsKey(parameter)).isFalse();
    this.pathParameters.put(parameter, value);
  }

  public void setQueryParameter(final String parameter, final String value) {
    assertThat(this.queryParameters.containsKey(parameter)).isFalse();
    this.queryParameters.put(parameter, value);
  }

  public void setOptionalQueryParameter(final String parameter, final String value) {
    assertThat(this.optionalQueryParameters.containsKey(parameter)).isFalse();
    this.optionalQueryParameters.put(parameter, value);
  }

  public void setListQueryParameters(final String parameter, final List<String> value) {
    assertThat(this.optionalQueryParameters.containsKey(parameter)).isFalse();
    this.listQueryParameters.put(parameter, value);
  }

  @Override
  public <T> T getPathParameter(final ParameterMetadata<T> parameterMetadata) {
    assertThat(this.pathParameters.containsKey(parameterMetadata.getName())).isTrue();
    return parameterMetadata
        .getType()
        .deserializeFromString(pathParameters.get(parameterMetadata.getName()));
  }

  @Override
  public String getResponseContentType(final int statusCode) {
    return contentTypeMap.get(statusCode);
  }

  public void setResponseContentType(final int statusCode, final String contentType) {
    contentTypeMap.put(statusCode, contentType);
  }

  @Override
  public <T> Optional<T> getOptionalQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    if (!optionalQueryParameters.containsKey(parameterMetadata.getName())) {
      return Optional.empty();
    }
    final Optional<String> param =
        Optional.ofNullable(optionalQueryParameters.get(parameterMetadata.getName()));
    return param.map(p -> parameterMetadata.getType().deserializeFromString(p));
  }

  @Override
  public <T> T getQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    assertThat(this.queryParameters.containsKey(parameterMetadata.getName())).isTrue();
    final String param = queryParameters.get(parameterMetadata.getName());
    return parameterMetadata.getType().deserializeFromString(param);
  }

  @Override
  public <T> List<T> getQueryParameterList(final ParameterMetadata<T> parameterMetadata) {
    if (!this.listQueryParameters.containsKey(parameterMetadata.getName())) {
      return List.of();
    }

    final List<String> params =
        ListQueryParameterUtils.getParameterAsStringList(
            listQueryParameters, parameterMetadata.getName());
    return params.stream()
        .map(p -> parameterMetadata.getType().deserializeFromString(p))
        .collect(Collectors.toList());
  }

  public void setRequestHeader(final String parameter, final String value) {
    assertThat(this.requestHeaders.containsKey(parameter)).isFalse();
    this.requestHeaders.put(parameter, value);
  }

  @Override
  public <T> Optional<T> getOptionalRequestHeader(final ParameterMetadata<T> parameterMetadata) {
    final Optional<String> param =
        Optional.ofNullable(requestHeaders.get(parameterMetadata.getName()));
    return param.map(p -> parameterMetadata.getType().deserializeFromString(p));
  }

  @Override
  public <T> T getRequestHeader(final ParameterMetadata<T> parameterMetadata) {
    assertThat(this.requestHeaders.containsKey(parameterMetadata.getName())).isTrue();
    final String param = requestHeaders.get(parameterMetadata.getName());
    return parameterMetadata.getType().deserializeFromString(param);
  }

  @Override
  public void header(final String name, final String value) {
    responseHeaders.put(name, value);
  }

  public String getResponseHeaders(final String name) {
    return responseHeaders.get(name);
  }

  @Override
  public void startEventStream(final Consumer<SseClient> clientConsumer) {
    throw new UnsupportedOperationException();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Object getResponseBody() {
    return responseObject;
  }

  @SuppressWarnings("unchecked")
  public String getResponseBodyAsJson(final RestApiEndpoint handler) throws IOException {
    final ResponseContentTypeDefinition<Object> responseType =
        (ResponseContentTypeDefinition<Object>)
            handler.getMetadata().getResponseType(responseCode, ContentTypes.JSON);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    responseType.serialize(responseObject, out);
    return out.toString(UTF_8);
  }

  public Optional<Throwable> getResponseError() {
    return responseError;
  }

  public static class Builder {
    private final Map<String, String> pathParameters = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();
    private final Map<String, String> optionalQueryParameters = new HashMap<>();
    private final Map<String, List<String>> listQueryParameters = new HashMap<>();
    private EndpointMetadata metadata;

    Builder() {}

    public Builder metadata(final EndpointMetadata metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder pathParameter(final String param, final String value) {
      assertThat(pathParameters.containsKey(param)).isFalse();
      this.pathParameters.put(param, value);
      return this;
    }

    public Builder queryParameter(final String param, final String value) {
      assertThat(queryParameters.containsKey(param)).isFalse();
      this.queryParameters.put(param, value);
      return this;
    }

    public Builder optionalQueryParameter(final String param, final String value) {
      assertThat(optionalQueryParameters.containsKey(param)).isFalse();
      this.optionalQueryParameters.put(param, value);
      return this;
    }

    public Builder listQueryParameter(final String param, final List<String> value) {
      assertThat(listQueryParameters.containsKey(param)).isFalse();
      this.listQueryParameters.put(param, value);
      return this;
    }

    public StubRestApiRequest build() {
      checkNotNull(metadata, "Must specify metadata");
      final StubRestApiRequest request = new StubRestApiRequest(metadata);
      for (String k : pathParameters.keySet()) {
        request.setPathParameter(k, pathParameters.get(k));
      }
      for (String k : queryParameters.keySet()) {
        request.setQueryParameter(k, queryParameters.get(k));
      }
      for (String k : optionalQueryParameters.keySet()) {
        request.setOptionalQueryParameter(k, optionalQueryParameters.get(k));
      }
      for (String k : listQueryParameters.keySet()) {
        request.setListQueryParameters(k, listQueryParameters.get(k));
      }

      return request;
    }
  }
}

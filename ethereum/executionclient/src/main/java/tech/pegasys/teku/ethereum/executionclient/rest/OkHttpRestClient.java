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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;

public class OkHttpRestClient implements RestClient {

  static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
  static final MediaType OCTET_STREAM_MEDIA_TYPE = MediaType.parse("application/octet-stream");

  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpRestClient(final OkHttpClient httpClient, final String baseEndpoint) {
    this.httpClient = httpClient;
    this.baseEndpoint = HttpUrl.get(baseEndpoint);
  }

  @Override
  public SafeFuture<Response<Void>> getAsync(
      final String apiPath, final Map<String, String> headers, final Duration timeout) {
    final Request request = createGetRequest(apiPath, headers);
    return makeAsyncVoidRequest(request, timeout);
  }

  @Override
  public <TResp extends SszData> SafeFuture<Response<BuilderApiResponse<TResp>>> getAsync(
      final String apiPath,
      final Map<String, String> headers,
      final ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
      final Duration timeout) {
    final Request request = createGetRequest(apiPath, headers);
    return makeAsyncRequest(request, Optional.of(responseSchema), timeout);
  }

  @Override
  public <TReq extends SszData> SafeFuture<Response<Void>> postAsync(
      final String apiPath,
      final TReq requestBodyObject,
      final boolean postAsSsz,
      final Duration timeout) {
    final RequestBody requestBody =
        postAsSsz
            ? createOctetStreamRequestBody(requestBodyObject)
            : createJsonRequestBody(requestBodyObject);
    final Request request = createPostRequest(apiPath, requestBody, NO_HEADERS);
    return makeAsyncVoidRequest(request, timeout);
  }

  @Override
  public <TReq extends SszData> SafeFuture<Response<Void>> postAsync(
      final String apiPath,
      final Map<String, String> headers,
      final TReq requestBodyObject,
      final boolean postAsSsz,
      final Duration timeout) {
    final RequestBody requestBody =
        postAsSsz
            ? createOctetStreamRequestBody(requestBodyObject)
            : createJsonRequestBody(requestBodyObject);
    final Request request = createPostRequest(apiPath, requestBody, headers);
    return makeAsyncVoidRequest(request, timeout);
  }

  @Override
  public <TResp extends SszData, TReq extends SszData>
      SafeFuture<Response<BuilderApiResponse<TResp>>> postAsync(
          final String apiPath,
          final Map<String, String> headers,
          final TReq requestBodyObject,
          final boolean postAsSsz,
          final ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
          final Duration timeout) {
    final RequestBody requestBody =
        postAsSsz
            ? createOctetStreamRequestBody(requestBodyObject)
            : createJsonRequestBody(requestBodyObject);
    final Request request = createPostRequest(apiPath, requestBody, headers);
    return makeAsyncRequest(request, Optional.of(responseSchema), timeout);
  }

  private Request createGetRequest(final String apiPath, final Map<String, String> headers) {
    final URL httpUrl = createHttpUrl(apiPath);
    final Request.Builder requestBuilder = new Request.Builder().url(httpUrl);
    headers.forEach(requestBuilder::header);
    return requestBuilder.build();
  }

  private <TReq extends SszData> RequestBody createJsonRequestBody(final TReq requestBodyObject) {
    return new RequestBody() {

      @Override
      @SuppressWarnings("unchecked")
      public void writeTo(final BufferedSink bufferedSink) throws IOException {
        JsonUtil.serializeToBytesChecked(
            requestBodyObject,
            (SerializableTypeDefinition<TReq>)
                requestBodyObject.getSchema().getJsonTypeDefinition(),
            bufferedSink.outputStream());
      }

      @Override
      public MediaType contentType() {
        return JSON_MEDIA_TYPE;
      }
    };
  }

  private <S extends SszData> RequestBody createOctetStreamRequestBody(final S requestBodyObject) {
    return new RequestBody() {

      @Override
      public void writeTo(final BufferedSink bufferedSink) throws IOException {
        try {
          requestBodyObject.sszSerialize(bufferedSink.outputStream());
        } catch (final UncheckedIOException e) {
          throw e.getCause();
        }
      }

      @Override
      public MediaType contentType() {
        return OCTET_STREAM_MEDIA_TYPE;
      }
    };
  }

  private Request createPostRequest(
      final String apiPath, final RequestBody requestBody, final Map<String, String> headers) {
    final URL httpUrl = createHttpUrl(apiPath);
    final Request.Builder requestBuilder = new Request.Builder().url(httpUrl).post(requestBody);
    headers.forEach(requestBuilder::header);
    return requestBuilder.build();
  }

  private URL createHttpUrl(final String apiPath) {
    final HttpUrl.Builder urlBuilder = baseEndpoint.newBuilder(apiPath);
    return requireNonNull(urlBuilder).build().url();
  }

  private <TResp extends SszData> SafeFuture<Response<BuilderApiResponse<TResp>>> makeAsyncRequest(
      final Request request,
      final Optional<ResponseSchemaAndDeserializableTypeDefinition<TResp>> responseSchemaMaybe,
      final Duration timeout) {
    final Call call = httpClient.newCall(request);
    call.timeout().timeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    final ResponseHandler<TResp> responseHandler = new ResponseHandler<>(responseSchemaMaybe);
    final Callback responseCallback = createResponseCallback(request, responseHandler);
    call.enqueue(responseCallback);
    return responseHandler.getFutureResponse();
  }

  private SafeFuture<Response<Void>> makeAsyncVoidRequest(
      final Request request, final Duration timeout) {
    final Call call = httpClient.newCall(request);
    call.timeout().timeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    final ResponseHandlerVoid responseHandler = new ResponseHandlerVoid();
    final Callback responseCallback = createResponseCallback(request, responseHandler);
    call.enqueue(responseCallback);
    return responseHandler.getFutureResponse();
  }

  private Callback createResponseCallback(
      final Request request, final AbstractResponseHandler responseHandler) {
    return new Callback() {
      @Override
      public void onFailure(final Call call, final IOException ex) {
        responseHandler.handleFailure(ex);
      }

      @Override
      public void onResponse(final Call call, final okhttp3.Response response) {
        responseHandler.handleResponse(request, response);
      }
    };
  }
}

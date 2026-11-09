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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface RestApiRequest {
  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  <T> T getRequestBody() throws JsonProcessingException;

  <T> Optional<T> getOptionalRequestBody() throws JsonProcessingException;

  void respondOk(Object response) throws JsonProcessingException;

  void respondAsync(SafeFuture<AsyncApiResponse> futureResponse);

  void respondOk(Object response, CacheLength cacheLength) throws JsonProcessingException;

  void respondError(int statusCode, String message) throws JsonProcessingException;

  void respondWithCode(int statusCode);

  /**
   * Respond with a status code that may not be declared in the endpoint metadata. This is very
   * rarely the right option and only applies in cases where the user supplies the response code.
   */
  void respondWithUndocumentedCode(int statusCode);

  void respondWithCode(int statusCode, CacheLength cacheLength);

  <T> T getPathParameter(ParameterMetadata<T> parameterMetadata);

  String getResponseContentType(final int statusCode);

  <T> Optional<T> getOptionalQueryParameter(ParameterMetadata<T> parameterMetadata);

  <T> T getQueryParameter(ParameterMetadata<T> parameterMetadata);

  <T> List<T> getQueryParameterList(final ParameterMetadata<T> parameterMetadata);

  <T> Optional<T> getOptionalRequestHeader(final ParameterMetadata<T> parameterMetadata);

  <T> T getRequestHeader(final ParameterMetadata<T> parameterMetadata);

  void header(final String name, final String value);

  void startEventStream(final Consumer<SseClient> clientConsumer);
}

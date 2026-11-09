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

package tech.pegasys.teku.validator.remote.typedef;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_GATEWAY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_GATEWAY_TIMEOUT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_TOO_MANY_REQUESTS;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.validator.remote.apiclient.BeaconNodeApiErrorUtils;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;

public class ResponseHandler<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<ResponseHandler.Handler<TObject>> handlers =
      new Int2ObjectOpenHashMap<>();
  private final Optional<SerializableTypeDefinition<TObject>> maybeTypeDefinition;

  public ResponseHandler(final Optional<SerializableTypeDefinition<TObject>> maybeTypeDefinition) {
    this.maybeTypeDefinition = maybeTypeDefinition;
    withHandler(SC_OK, this::defaultOkHandler);
    withHandler(SC_ACCEPTED, this::noValueHandler);
    withHandler(SC_NO_CONTENT, this::noValueHandler);
    withHandler(SC_BAD_REQUEST, this::defaultBadRequestHandler);
    withHandler(SC_NOT_FOUND, this::notFoundHandler);
    withHandler(SC_TOO_MANY_REQUESTS, this::defaultTooManyRequestsHandler);
    withHandler(SC_INTERNAL_SERVER_ERROR, this::serviceErrorHandler);
    withHandler(SC_BAD_GATEWAY, this::serviceErrorHandler);
    withHandler(SC_SERVICE_UNAVAILABLE, this::serviceErrorHandler);
    withHandler(SC_GATEWAY_TIMEOUT, this::serviceErrorHandler);
  }

  private Optional<TObject> notFoundHandler(final Request request, final Response response) {
    LOG.debug("Request to {} responded with status code {}", request.url(), response.code());
    return Optional.empty();
  }

  public ResponseHandler(final DeserializableTypeDefinition<TObject> typeDefinition) {
    this(Optional.of(typeDefinition));
  }

  public ResponseHandler(final DeserializableOneOfTypeDefinition<TObject> oneOfTypeDefinition) {
    this(Optional.of(oneOfTypeDefinition));
  }

  public ResponseHandler() {
    this(Optional.empty());
  }

  public ResponseHandler<TObject> withHandler(
      final int responseCode, final Handler<TObject> handler) {
    handlers.put(responseCode, handler);
    return this;
  }

  public ResponseHandler<TObject> withHandler(
      final Handler<TObject> handler, final int... responseCodes) {
    for (final int responseCode : responseCodes) {
      handlers.put(responseCode, handler);
    }
    return this;
  }

  private Optional<TObject> defaultOkHandler(final Request request, final Response response)
      throws IOException {
    final ResponseBody responseBody = response.body();
    if (responseBody != null && maybeTypeDefinition.isPresent()) {
      try {
        final SerializableTypeDefinition<TObject> typeDefinition = maybeTypeDefinition.get();
        if (typeDefinition instanceof DeserializableOneOfTypeDefinition) {
          return Optional.of(
              JsonUtil.parse(
                  responseBody.string(),
                  (DeserializableOneOfTypeDefinition<? extends TObject>) typeDefinition));
        } else {
          return Optional.of(
              JsonUtil.parse(
                  responseBody.string(),
                  (DeserializableTypeDefinition<? extends TObject>) typeDefinition));
        }

      } catch (JsonProcessingException ex) {
        LOG.debug("Failed to decode response body", ex);
      }
    }
    return Optional.empty();
  }

  public Optional<TObject> handleResponse(final Request request, final Response response)
      throws IOException {
    return handlers
        .getOrDefault(response.code(), this::unknownResponseCodeHandler)
        .handleResponse(request, response);
  }

  private Optional<TObject> unknownResponseCodeHandler(
      final Request request, final Response response) {
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    final String exceptionMessage =
        String.format(
            "Unexpected response from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage);
    throw new RuntimeException(exceptionMessage);
  }

  public interface Handler<TObject> {
    Optional<TObject> handleResponse(Request request, Response response) throws IOException;
  }

  private Optional<TObject> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }

  private Optional<TObject> serviceErrorHandler(final Request request, final Response response) {
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    throw new RemoteServiceNotAvailableException(
        String.format(
            "Server error from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage));
  }

  private Optional<TObject> defaultBadRequestHandler(
      final Request request, final Response response) {
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    final String exceptionMessage =
        String.format(
            "Invalid params response from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage);
    throw new IllegalArgumentException(exceptionMessage);
  }

  private Optional<TObject> defaultTooManyRequestsHandler(
      final Request request, final Response response) {
    throw new RateLimitedException(request.url().toString());
  }
}

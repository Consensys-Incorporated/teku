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

package tech.pegasys.teku.builder.rest;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class ResponseHandler<T> {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<DeserializableTypeDefinition<T>> maybeTypeDefinition;
  private final Int2ObjectMap<Handler<T>> handlers = new Int2ObjectOpenHashMap<>();

  public static ResponseHandler<Void> voidHandler() {
    return new ResponseHandler<>(Optional.empty());
  }

  public ResponseHandler(final DeserializableTypeDefinition<T> typeDefinition) {
    this(Optional.of(typeDefinition));
  }

  private ResponseHandler(final Optional<DeserializableTypeDefinition<T>> maybeTypeDefinition) {
    this.maybeTypeDefinition = maybeTypeDefinition;
    withHandler(SC_OK, this::defaultOkHandler);
    withHandler(SC_ACCEPTED, this::noValueHandler);
    withHandler(SC_NO_CONTENT, this::noValueHandler);
    withHandler(SC_BAD_REQUEST, this::badRequestHandler);
    withHandler(SC_UNAUTHORIZED, this::unauthorizedHandler);
    withHandler(SC_INTERNAL_SERVER_ERROR, this::serverErrorHandler);
    withHandler(SC_SERVICE_UNAVAILABLE, this::serverErrorHandler);
  }

  public ResponseHandler<T> withHandler(final int responseCode, final Handler<T> handler) {
    handlers.put(responseCode, handler);
    return this;
  }

  public Optional<T> handleResponse(final Request request, final Response response)
      throws IOException {
    return handlers
        .getOrDefault(response.code(), this::unknownResponseCodeHandler)
        .handleResponse(request, response);
  }

  private Optional<T> defaultOkHandler(final Request request, final Response response)
      throws IOException {
    final ResponseBody responseBody = response.body();
    if (responseBody != null && maybeTypeDefinition.isPresent()) {
      try {
        return Optional.of(JsonUtil.parse(responseBody.string(), maybeTypeDefinition.get()));
      } catch (JsonProcessingException ex) {
        throw new UncheckedIOException("Failed to decode builder response body", ex);
      }
    }
    return Optional.empty();
  }

  private Optional<T> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }

  private Optional<T> badRequestHandler(final Request request, final Response response) {
    throw new BuilderClientException("Bad request: " + getErrorMessage(response), SC_BAD_REQUEST);
  }

  private Optional<T> unauthorizedHandler(final Request request, final Response response) {
    throw new BuilderClientException("Unauthorized: " + getErrorMessage(response), SC_UNAUTHORIZED);
  }

  private Optional<T> serverErrorHandler(final Request request, final Response response) {
    final String message =
        String.format(
            "Builder server error (url=%s, status=%d, message=%s)",
            request.url(), response.code(), getErrorMessage(response));
    LOG.warn(message);
    throw new BuilderClientException(message, response.code());
  }

  private Optional<T> unknownResponseCodeHandler(final Request request, final Response response) {
    throw new BuilderClientException(
        String.format(
            "Unexpected response from builder (url=%s, status=%d, message=%s)",
            request.url(), response.code(), getErrorMessage(response)),
        response.code());
  }

  private static String getErrorMessage(final Response response) {
    try (final ResponseBody body = response.body()) {
      if (body == null) {
        return response.message();
      }
      final BufferedSource source = body.source();
      source.request(1024); // 1 KB limit
      final long bytesToRead = Math.min(1024, source.getBuffer().size());
      final String content = source.readUtf8(bytesToRead);
      return content.isEmpty() ? response.message() : content;
    } catch (IOException ex) {
      return response.message();
    }
  }

  public interface Handler<T> {
    Optional<T> handleResponse(Request request, Response response) throws IOException;
  }
}

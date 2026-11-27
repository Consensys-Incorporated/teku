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

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class JavalinRestApiRequest implements RestApiRequest {
  private static final Logger LOG = LogManager.getLogger();
  private static final HttpErrorResponse TIMEOUT_ERROR =
      new HttpErrorResponse(SC_INTERNAL_SERVER_ERROR, "Request timeout");
  private final Context context;
  private final EndpointMetadata metadata;
  private final Map<String, String> pathParamMap;
  private final Map<String, List<String>> queryParamMap;
  private final Map<String, String> headerMap;

  @Override
  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() throws JsonProcessingException {
    if (!metadata.isRequiredRequestBody()) {
      throw new RuntimeException(
          "Optional request body configured, use getOptionalRequestBody() instead");
    }
    return metadata.getRequestBody(context.bodyInputStream(), headerMap);
  }

  @Override
  public <T> Optional<T> getOptionalRequestBody() throws JsonProcessingException {
    // We use a PushbackInputStream because the underlying input stream does not support reset()
    try (final PushbackInputStream pushbackInputStream =
        new PushbackInputStream(context.bodyInputStream())) {
      final int firstByte = pushbackInputStream.read();
      if (firstByte == -1) {
        return Optional.empty();
      } else {
        pushbackInputStream.unread(firstByte);
        return Optional.of(metadata.getRequestBody(pushbackInputStream, headerMap));
      }
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new RuntimeException("Error reading request body", e);
    }
  }

  public JavalinRestApiRequest(final Context context, final EndpointMetadata metadata) {
    this.context = context;
    this.metadata = metadata;
    // Work around a bug in Javalin where it decides whether to compress on each call to
    // write which could result in a mix of compressed and uncompressed content
    // and means it doesn't evaluate the length of the response correctly.
    this.context.minSizeForCompression(0);
    this.pathParamMap = context.pathParamMap();
    this.queryParamMap = context.queryParamMap();
    // https://datatracker.ietf.org/doc/html/rfc7230#section-3.2
    final Map<String, String> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headerMap.putAll(context.headerMap());
    this.headerMap = Collections.unmodifiableMap(headerMap);
  }

  @Override
  public void respondOk(final Object response) throws JsonProcessingException {
    respond(SC_OK, response, getResponseOutputStream());
  }

  @Override
  public void respondAsync(final SafeFuture<AsyncApiResponse> futureResponse) {
    context.future(
        () -> {
          final AsyncContext asyncContext = context.req().getAsyncContext();
          // The async timeout bounds the wait for the response, so it is applied to the future.
          // Left on the async context it would also end a response whose body is still being
          // written, and the client would get a truncated body that looks complete.
          final long timeoutMillis = asyncContext.getTimeout();
          asyncContext.setTimeout(0);
          final SafeFuture<Void> requestEnded = new SafeFuture<>();
          final SafeFuture<AsyncApiResponse> response =
              futureResponse.orInterrupt(
                  SafeFuture.createInterruptor(requestEnded, CancellationException::new));
          // Empty when the timeout fires first
          final SafeFuture<Optional<AsyncApiResponse>> responseOrTimeout =
              response.thenApply(Optional::of);
          final SafeFuture<Void> requestResponse =
              (timeoutMillis > 0
                      ? SafeFuture.of(
                          responseOrTimeout.completeOnTimeout(
                              Optional.empty(), timeoutMillis, MILLISECONDS))
                      : responseOrTimeout)
                  // Writing the body blocks for as long as the client takes to read it, so it is
                  // done on a server thread, as for any other response, and not on the thread
                  // that completed the response or on the one that fired the timeout.
                  .thenAcceptAsync(this::writeAsyncResponse, asyncContext::start);
          asyncContext.addListener(
              new AsyncResponseCancellationListener(requestResponse, requestEnded));
          return requestResponse;
        });
  }

  private void writeAsyncResponse(final Optional<AsyncApiResponse> maybeResponse) {
    try {
      if (maybeResponse.isEmpty()) {
        respondWithTimeout();
      } else {
        respond(
            maybeResponse.get().getResponseCode(),
            maybeResponse.get().getResponseBody(),
            getResponseOutputStream());
      }
    } catch (final JsonProcessingException e) {
      LOG.trace("Failed to generate API response", e);
      context.status(SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    respond(SC_OK, response, getResponseOutputStream());
  }

  @Override
  public void respondError(final int statusCode, final String message)
      throws JsonProcessingException {
    respond(statusCode, new HttpErrorResponse(statusCode, message), getResponseOutputStream());
  }

  /**
   * The timeout error is written as JSON without going through the endpoint's response types:
   * content negotiation falls back to the endpoint's default type, which its error responses may
   * not support.
   */
  private void respondWithTimeout() throws JsonProcessingException {
    context.status(SC_INTERNAL_SERVER_ERROR);
    context.json(JsonUtil.serialize(TIMEOUT_ERROR, HTTP_ERROR_RESPONSE_TYPE));
  }

  private OutputStream getResponseOutputStream() {
    return context.outputStream();
  }

  private void respond(
      final int statusCode, final Optional<Object> response, final OutputStream out)
      throws JsonProcessingException {
    context.status(statusCode);
    if (response.isPresent()) {
      respondImpl(statusCode, response.get(), out);
    }
  }

  private void respond(final int statusCode, final Object response, final OutputStream out)
      throws JsonProcessingException {
    context.status(statusCode);
    respondImpl(statusCode, response, out);
  }

  private void respondImpl(final int statusCode, final Object response, final OutputStream out)
      throws JsonProcessingException {
    final ResponseMetadata responseMetadata =
        metadata.createResponseMetadata(
            statusCode, Optional.ofNullable(context.header(HEADER_ACCEPT)), response);
    context.contentType(responseMetadata.getContentType());
    responseMetadata.getAdditionalHeaders().forEach(context::header);
    try {
      // The response stream stays open whatever the serializer does. The JSON serializer closes
      // the stream it writes to, also when it fails, and closing a compressed stream ends the
      // response normally before it can be aborted.
      metadata.serialize(
          statusCode,
          responseMetadata.getContentType(),
          response,
          CloseShieldOutputStream.wrap(out));
    } catch (final Throwable e) {
      abortCommittedResponse(e);
      throw e;
    }
  }

  /**
   * The body is written straight to the client, so writing it can fail after the status line and
   * headers have been sent. The status can no longer report the failure and Javalin would end the
   * response normally, leaving the client with a truncated body that looks complete. Closing the
   * connection makes the transfer fail on the client side instead. A response that is not yet
   * committed is left alone and gets an error status as usual.
   */
  private void abortCommittedResponse(final Throwable cause) {
    try {
      if (!context.res().isCommitted()) {
        return;
      }
      // The connection is closed directly because Jetty's own abort, sendError(-1), only takes
      // effect once the request completes: by then Javalin has closed a compressed stream, which
      // ends the response normally.
      ServletContextRequest.getServletContextRequest(context.req())
          .getConnectionMetaData()
          .getConnection()
          .getEndPoint()
          .close(new IOException("Response body could not be completed", cause));
      if (cause instanceof UncheckedIOException) {
        // Writing to the client failed, which is what a client going away looks like
        LOG.debug(
            "Closed connection, response body to {} could not be written", context.path(), cause);
      } else {
        LOG.warn(
            "Closed connection, response body to {} could not be completed", context.path(), cause);
      }
    } catch (final Throwable e) {
      // The failure to report is the one that stopped the body
      cause.addSuppressed(e);
    }
  }

  private static class AsyncResponseCancellationListener implements AsyncListener {
    private final SafeFuture<?> requestResponse;
    private final SafeFuture<Void> requestEnded;

    private AsyncResponseCancellationListener(
        final SafeFuture<?> requestResponse, final SafeFuture<Void> requestEnded) {
      this.requestResponse = requestResponse;
      this.requestEnded = requestEnded;
    }

    @Override
    public void onComplete(final AsyncEvent event) {
      cancelResponse();
    }

    @Override
    public void onTimeout(final AsyncEvent event) {
      cancelResponse();
    }

    @Override
    public void onError(final AsyncEvent event) {
      cancelResponse();
    }

    @Override
    public void onStartAsync(final AsyncEvent event) {
      event.getAsyncContext().addListener(this);
    }

    private void cancelResponse() {
      requestResponse.cancel(false);
      requestEnded.complete(null);
    }
  }

  /** This is only used when intending to return status code without a response body */
  @Override
  public void respondWithCode(final int statusCode) {
    checkState(
        metadata.isNoContentResponse(statusCode),
        "Content required for status code %s but not provided",
        statusCode);
    respondWithUndocumentedCode(statusCode);
  }

  /** This is only used when intending to return status code without a response body */
  @Override
  public void respondWithUndocumentedCode(final int statusCode) {
    context.status(statusCode);
  }

  @Override
  public void respondWithCode(final int statusCode, final CacheLength cacheLength) {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    context.status(statusCode);
  }

  @Override
  public <T> T getPathParameter(final ParameterMetadata<T> parameterMetadata) {
    try {
      return parameterMetadata
          .getType()
          .deserializeFromString(pathParamMap.get(parameterMetadata.getName()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read path parameter %s: %s", parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public String getResponseContentType(final int statusCode) {
    return metadata.getContentType(statusCode, Optional.ofNullable(context.header(HEADER_ACCEPT)));
  }

  @Override
  public <T> Optional<T> getOptionalQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    if (!queryParamMap.containsKey(parameterMetadata.getName())) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          parameterMetadata
              .getType()
              .deserializeFromString(
                  SingleQueryParameterUtils.validateQueryParameter(
                      queryParamMap, parameterMetadata.getName())));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read optional query parameter %s: %s",
              parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public <T> T getQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    try {
      return parameterMetadata
          .getType()
          .deserializeFromString(
              SingleQueryParameterUtils.validateQueryParameter(
                  queryParamMap, parameterMetadata.getName()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read query parameter %s: %s",
              parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public <T> List<T> getQueryParameterList(final ParameterMetadata<T> parameterMetadata) {
    if (!queryParamMap.containsKey(parameterMetadata.getName())) {
      return List.of();
    }
    try {
      final List<String> paramList =
          ListQueryParameterUtils.getParameterAsStringList(
              queryParamMap, parameterMetadata.getName());
      return paramList.stream()
          .map(item -> parameterMetadata.getType().deserializeFromString(item))
          .toList();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read query list parameter %s: %s",
              parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public <T> Optional<T> getOptionalRequestHeader(final ParameterMetadata<T> parameterMetadata) {
    if (!headerMap.containsKey(parameterMetadata.getName())) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          parameterMetadata
              .getType()
              .deserializeFromString(headerMap.get(parameterMetadata.getName())));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read optional header %s: %s",
              parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public <T> T getRequestHeader(final ParameterMetadata<T> parameterMetadata) {
    try {
      return parameterMetadata
          .getType()
          .deserializeFromString(headerMap.get(parameterMetadata.getName()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Could not read header %s: %s", parameterMetadata.getName(), e.getMessage()));
    }
  }

  @Override
  public void header(final String name, final String value) {
    context.header(name, value);
  }

  @Override
  public void startEventStream(final Consumer<SseClient> clientConsumer) {
    SseHandler sseHandler =
        new SseHandler(
            client -> {
              client.keepAlive();
              clientConsumer.accept(client);
            });
    sseHandler.handle(context);
  }
}

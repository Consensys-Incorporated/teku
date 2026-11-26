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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.Javalin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.tuweni.bytes.Bytes;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.JsonResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;

/**
 * Checks what an HTTP client observes when a response body cannot be completed. Once the status
 * line and headers are on the wire the status can no longer signal the failure, so the transfer
 * itself has to fail; a response that ends normally is indistinguishable from a complete one. The
 * async timeout bounds the wait for a response, and must not end one that is being written.
 */
@Timeout(60)
class StreamedResponseFailureTest {
  // Several times Jetty's output buffer, so the response is committed before the body ends
  private static final int CHUNK_SIZE = 256 * 1024;
  private static final int JSON_ITEM_COUNT = 8_192;
  private static final int ASYNC_TIMEOUT_MILLIS = 1_000;
  private static final String FAILURE_MESSAGE = "Failed to serialize the response body";

  private static final OctetStreamResponseContentTypeDefinition<BodyWriter> BODY_TYPE =
      new OctetStreamResponseContentTypeDefinition<>(
          (writer, out) -> writer.writeTo(out), writer -> Map.of());
  private static final SerializableTypeDefinition<List<String>> JSON_BODY_TYPE =
      SerializableTypeDefinition.listOf(STRING_TYPE);

  // Random content, so gzip does not shrink it below the output buffer size
  private final byte[] firstChunk = Bytes.random(CHUNK_SIZE).toArrayUnsafe();
  private final byte[] secondChunk = Bytes.random(CHUNK_SIZE).toArrayUnsafe();
  private final List<String> jsonItems =
      Stream.generate(() -> Bytes.random(32).toHexString()).limit(JSON_ITEM_COUNT).toList();

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final HttpClient client =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private RestApi restApi;
  private ServerConnector connector;

  @BeforeEach
  void startRestApi() throws Exception {
    final RestApiBuilder builder =
        new RestApiBuilder()
            .port(0)
            .asyncTimeoutMillis(ASYNC_TIMEOUT_MILLIS)
            // As the beacon REST API does
            .exceptionHandler(
                IllegalArgumentException.class,
                throwable -> HttpErrorResponse.badRequest(throwable.getMessage()));
    for (final boolean async : new boolean[] {false, true}) {
      final String prefix = async ? "/async" : "/sync";
      builder
          .endpoint(new StreamingEndpoint(prefix + "/complete", async, this::writeAll))
          .endpoint(new StreamingEndpoint(prefix + "/fail-before-commit", async, this::fail))
          .endpoint(
              new StreamingEndpoint(prefix + "/fail-after-commit", async, this::writeThenFail));
    }
    builder
        .endpoint(new StreamingEndpoint("/async/slow-body", true, this::writeSlowly))
        .endpoint(new JsonEndpoint("/json/complete", jsonItems))
        .endpoint(new JsonEndpoint("/json/fail-after-commit", failingAtLastItem(jsonItems)))
        .endpoint(new NeverRespondingEndpoint("/async/never-responds", ContentTypes.JSON))
        .endpoint(
            new NeverRespondingEndpoint(
                "/async/never-responds-octet-stream-default", ContentTypes.OCTET_STREAM));
    restApi = builder.build();
    waitFor(restApi.start());
    connector = (ServerConnector) getJavalin(restApi).jettyServer().server().getConnectors()[0];
  }

  @AfterEach
  void stopRestApi() throws Exception {
    client.shutdownNow();
    executor.shutdownNow();
    waitFor(restApi.stop());
  }

  @ParameterizedTest(name = "{0} gzip={1}")
  @CsvSource({"sync,false", "sync,true", "async,false", "async,true"})
  void shouldDeliverCompleteBody(final String mode, final boolean gzip) throws Exception {
    final Optional<HttpResponse<byte[]>> response = send("/" + mode + "/complete", gzip);

    assertThat(response).isPresent();
    assertThat(response.get().statusCode()).isEqualTo(SC_OK);
    assertThat(bodyOf(response.get(), gzip))
        .isEqualTo(Bytes.concatenate(Bytes.wrap(firstChunk), Bytes.wrap(secondChunk)));
  }

  @ParameterizedTest(name = "{0} gzip={1}")
  @CsvSource({"sync,false", "sync,true", "async,false", "async,true"})
  void shouldFailTransferWhenBodyWriterThrowsAfterResponseCommitted(
      final String mode, final boolean gzip) throws Exception {
    final Optional<HttpResponse<byte[]>> response = send("/" + mode + "/fail-after-commit", gzip);

    assertThat(response.map(StreamedResponseFailureTest::describe))
        .describedAs("response to a request whose body could not be completed")
        .isEmpty();
    waitFor(() -> assertThat(connector.getConnectedEndPoints()).isEmpty(), 10, SECONDS);
  }

  @ParameterizedTest(name = "gzip={0}")
  @CsvSource({"false", "true"})
  void shouldDeliverCompleteJsonBody(final boolean gzip) throws Exception {
    final Optional<HttpResponse<byte[]>> response =
        send("/json/complete", Optional.of(ContentTypes.JSON), gzip);

    assertThat(response).isPresent();
    assertThat(response.get().statusCode()).isEqualTo(SC_OK);
    assertThat(new String(bodyOf(response.get(), gzip).toArrayUnsafe(), UTF_8))
        .isEqualTo(JsonUtil.serialize(jsonItems, JSON_BODY_TYPE));
  }

  // The JSON serializer closes the stream it writes to, also when it fails. With gzip that would
  // end the response normally, with the truncated JSON even made well-formed.
  @ParameterizedTest(name = "gzip={0}")
  @CsvSource({"false", "true"})
  void shouldFailTransferWhenJsonSerializationFailsAfterResponseCommitted(final boolean gzip)
      throws Exception {
    final Optional<HttpResponse<byte[]>> response =
        send("/json/fail-after-commit", Optional.of(ContentTypes.JSON), gzip);

    assertThat(response.map(StreamedResponseFailureTest::describe))
        .describedAs("response to a request whose body could not be completed")
        .isEmpty();
    waitFor(() -> assertThat(connector.getConnectedEndPoints()).isEmpty(), 10, SECONDS);
  }

  @ParameterizedTest(name = "gzip={0}")
  @CsvSource({"false", "true"})
  void shouldDeliverCompleteBodyWhenWritingItOutlastsAsyncTimeout(final boolean gzip)
      throws Exception {
    final Optional<HttpResponse<byte[]>> response = send("/async/slow-body", gzip);

    assertThat(response).isPresent();
    assertThat(response.get().statusCode()).isEqualTo(SC_OK);
    assertThat(bodyOf(response.get(), gzip))
        .isEqualTo(Bytes.concatenate(Bytes.wrap(firstChunk), Bytes.wrap(secondChunk)));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"sync", "async"})
  void shouldRespondWithErrorStatusWhenBodyWriterThrowsBeforeResponseCommitted(final String mode)
      throws Exception {
    final Optional<HttpResponse<byte[]>> response = send("/" + mode + "/fail-before-commit", false);

    assertThat(response).isPresent();
    assertThat(response.get().statusCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(new String(response.get().body(), UTF_8)).contains(FAILURE_MESSAGE);
  }

  // The timeout error is JSON also when the endpoint's default response type is not, which is what
  // a request accepting that type, or stating none, would otherwise be answered with
  @ParameterizedTest(name = "{0} accept={1}")
  @CsvSource({
    "/async/never-responds,application/octet-stream",
    "/async/never-responds-octet-stream-default,application/octet-stream",
    "/async/never-responds-octet-stream-default,"
  })
  void shouldRespondWithErrorStatusWhenAsyncTimeoutFiresBeforeResponseStarted(
      final String path, final String accept) throws Exception {
    final Optional<HttpResponse<byte[]>> response = send(path, Optional.ofNullable(accept), false);

    assertThat(response).isPresent();
    assertThat(response.get().statusCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(new String(response.get().body(), UTF_8))
        .isEqualTo("{\"code\":500,\"message\":\"Request timeout\"}");
  }

  /** Returns the response, or empty when the transfer failed on the client side. */
  private Optional<HttpResponse<byte[]>> send(final String path, final boolean gzip)
      throws InterruptedException {
    return send(path, Optional.of(ContentTypes.OCTET_STREAM), gzip);
  }

  private Optional<HttpResponse<byte[]>> send(
      final String path, final Optional<String> accept, final boolean gzip)
      throws InterruptedException {
    final HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + restApi.getListenPort() + path));
    accept.ifPresent(type -> request.header("Accept", type));
    if (gzip) {
      request.header("Accept-Encoding", "gzip");
    }
    try {
      return Optional.of(client.send(request.build(), BodyHandlers.ofByteArray()));
    } catch (final IOException e) {
      return Optional.empty();
    }
  }

  private static Bytes bodyOf(final HttpResponse<byte[]> response, final boolean gzip)
      throws IOException {
    if (!gzip) {
      return Bytes.wrap(response.body());
    }
    assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
      return Bytes.wrap(in.readAllBytes());
    }
  }

  private static String describe(final HttpResponse<byte[]> response) {
    return "status " + response.statusCode() + " with a body of " + response.body().length;
  }

  private void writeAll(final OutputStream out) throws IOException {
    out.write(firstChunk);
    out.write(secondChunk);
  }

  private void fail(final OutputStream out) {
    throw new IllegalArgumentException(FAILURE_MESSAGE);
  }

  private void writeThenFail(final OutputStream out) throws IOException {
    out.write(firstChunk);
    throw new IllegalArgumentException(FAILURE_MESSAGE);
  }

  private void writeSlowly(final OutputStream out) throws IOException {
    out.write(firstChunk);
    try {
      Thread.sleep(2 * ASYNC_TIMEOUT_MILLIS);
    } catch (final InterruptedException e) {
      throw new IOException(e);
    }
    out.write(secondChunk);
  }

  private static List<String> failingAtLastItem(final List<String> items) {
    return new AbstractList<>() {
      @Override
      public String get(final int index) {
        if (index == items.size() - 1) {
          throw new IllegalArgumentException(FAILURE_MESSAGE);
        }
        return items.get(index);
      }

      @Override
      public int size() {
        return items.size();
      }
    };
  }

  private static Javalin getJavalin(final RestApi restApi)
      throws NoSuchFieldException, IllegalAccessException {
    final Field appField = RestApi.class.getDeclaredField("app");
    appField.setAccessible(true);
    return (Javalin) appField.get(restApi);
  }

  private interface BodyWriter {
    void writeTo(OutputStream out) throws IOException;
  }

  private class StreamingEndpoint extends RestApiEndpoint {
    private final boolean async;
    private final BodyWriter bodyWriter;

    private StreamingEndpoint(final String path, final boolean async, final BodyWriter bodyWriter) {
      super(
          get(path)
              .operationId(path)
              .summary("Streamed response")
              .description("Streams a response body")
              .response(SC_OK, "Success", BODY_TYPE)
              .build());
      this.async = async;
      this.bodyWriter = bodyWriter;
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
      if (!async) {
        request.respondOk(bodyWriter);
        return;
      }
      final SafeFuture<AsyncApiResponse> response = new SafeFuture<>();
      request.respondAsync(response);
      // Completed from another thread once the request thread is back with the server, as an
      // endpoint loading its data from storage would
      CompletableFuture.delayedExecutor(100, MILLISECONDS, executor)
          .execute(() -> response.complete(AsyncApiResponse.respondOk(bodyWriter)));
    }
  }

  private static class JsonEndpoint extends RestApiEndpoint {
    private final List<String> items;

    private JsonEndpoint(final String path, final List<String> items) {
      super(
          get(path)
              .operationId(path)
              .summary("JSON response")
              .description("Responds with a JSON body")
              .response(SC_OK, "Success", JSON_BODY_TYPE)
              .build());
      this.items = items;
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
      request.respondOk(items);
    }
  }

  private static class NeverRespondingEndpoint extends RestApiEndpoint {
    private NeverRespondingEndpoint(final String path, final String defaultResponseType) {
      super(
          get(path)
              .operationId(path)
              .summary("No response")
              .description("Never completes its response")
              .defaultResponseType(defaultResponseType)
              .response(
                  SC_OK,
                  "Success",
                  List.of(new JsonResponseContentTypeDefinition<>(STRING_TYPE), BODY_TYPE))
              .build());
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
      request.respondAsync(new SafeFuture<>());
    }
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient.ResponseSchemaAndDeserializableTypeDefinition;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

class OkHttpRestClientTest {

  private static final Map<String, String> TEST_HEADERS = Map.of("foo", "bar");
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
  private static final String TEST_PATH = "v1/test";

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  private ResponseSchemaAndDeserializableTypeDefinition<ResponseContainer> responseTypeDefinition;
  private OkHttpRestClient underTest;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    final String endpoint = "http://localhost:" + mockWebServer.getPort();
    responseTypeDefinition =
        new ResponseSchemaAndDeserializableTypeDefinition<>(
            ResponseContainer.SSZ_SCHEMA,
            BuilderApiResponse.createTypeDefinition(
                ResponseContainer.SSZ_SCHEMA.getJsonTypeDefinition()));

    underTest = new OkHttpRestClient(okHttpClient, endpoint);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
    okHttpClient.dispatcher().executorService().shutdown();
    okHttpClient.connectionPool().evictAll();
  }

  @Test
  void getsResponseAsync() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"version\" : \"electra\", \"data\" : {\"output\" : \"1\"}}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> responseFuture =
        underTest.getAsync(TEST_PATH, TEST_HEADERS, responseTypeDefinition, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.payload())
                  .satisfies(
                      testObject -> assertThat(testObject.data().getOutput()).isEqualTo(UInt64.ONE))
                  .satisfies(
                      testObject ->
                          assertThat(testObject.version()).isEqualTo(SpecMilestone.ELECTRA));
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getHeader("foo")).isEqualTo("bar");
  }

  @Test
  void getsResponseAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SafeFuture<Response<Void>> responseFuture =
        underTest.getAsync(TEST_PATH, TEST_HEADERS, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.payload()).isNull();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getsResponseAsyncTimeouts() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setHeadersDelay(1, TimeUnit.SECONDS));

    final SafeFuture<Response<Void>> responseFuture =
        underTest.getAsync(TEST_PATH, TEST_HEADERS, Duration.ofMillis(100));

    assertThat(responseFuture)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(InterruptedIOException.class)
        .withMessageContaining("timeout");

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getAsyncHandlesFailures() throws InterruptedException {
    final String errorBody = "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(errorBody));

    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> responseFuture =
        underTest.getAsync(TEST_PATH, TEST_HEADERS, responseTypeDefinition, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isEqualTo(errorBody);
              assertThat(response.payload()).isNull();
              assertThat(response.isFailure()).isTrue();
              assertThat(response.isSuccess()).isFalse();
              assertThat(response.isUnsupportedMediaTypeError()).isFalse();
            });

    // error without a body
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> secondResponseFuture =
        underTest.getAsync(TEST_PATH, TEST_HEADERS, responseTypeDefinition, TEST_TIMEOUT);

    assertThat(secondResponseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isEqualTo("500: Server Error");
              assertThat(response.payload()).isNull();
              assertThat(response.isFailure()).isTrue();
              assertThat(response.isSuccess()).isFalse();
              assertThat(response.isUnsupportedMediaTypeError()).isFalse();
            });

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    final RecordedRequest request = mockWebServer.takeRequest();
    final RecordedRequest secondRequest = mockWebServer.takeRequest();

    Consumer<RecordedRequest> requestsAssertions =
        (req) -> {
          assertThat(req.getPath()).isEqualTo("/" + TEST_PATH);
          assertThat(req.getMethod()).isEqualTo("GET");
          assertThat(request.getHeader("foo")).isEqualTo("bar");
        };

    assertThat(List.of(request, secondRequest)).allSatisfy(requestsAssertions);
  }

  @Test
  void postsAsync() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"version\" : \"electra\", \"data\" : {\"output\" : \"1\"}}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final RequestContainer requestBodyObject = new RequestContainer(2);
    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> responseFuture =
        underTest.postAsync(
            TEST_PATH, Map.of(), requestBodyObject, false, responseTypeDefinition, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.payload())
                  .satisfies(
                      testObject -> assertThat(testObject.data().getOutput()).isEqualTo(UInt64.ONE))
                  .satisfies(
                      testObject ->
                          assertThat(testObject.version()).isEqualTo(SpecMilestone.ELECTRA));
              assertThat(response.receivedAsSsz()).isFalse();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"input\":\"2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void postsAsSszAsync() throws InterruptedException {
    final Buffer responseBodyBuffer = new Buffer();
    final ResponseContainer responseContainer = new ResponseContainer(1);
    responseBodyBuffer.write(responseContainer.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setBody(responseBodyBuffer)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Eth-Consensus-Version", "electra"));

    final RequestContainer requestBodyObject = new RequestContainer(2);
    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> responseFuture =
        underTest.postAsync(
            TEST_PATH, Map.of(), requestBodyObject, true, responseTypeDefinition, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.payload())
                  .satisfies(
                      testObject -> assertThat(testObject.data()).isEqualTo(responseContainer))
                  .satisfies(
                      testObject ->
                          assertThat(testObject.version()).isEqualTo(SpecMilestone.ELECTRA));
              assertThat(response.receivedAsSsz()).isTrue();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody())
        .satisfies(
            buffer ->
                assertThat(
                        RequestContainer.SSZ_SCHEMA.sszDeserialize(
                            Bytes.wrap(buffer.readByteArray())))
                    .isEqualTo(requestBodyObject));
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  @SuppressWarnings("unchecked")
  void postsAsyncDoesNotThrowExceptionsInOtherThreadsWhenRequestCreationFails() {
    final RequestContainer requestBodyObject = spy(new RequestContainer(2));
    final AbstractSszContainerSchema<RequestContainer> failingSchema =
        mock(AbstractSszContainerSchema.class);

    when(failingSchema.getJsonTypeDefinition()).thenReturn(new FailingSerializableTypeDefinition());
    doAnswer(invocation -> failingSchema).when(requestBodyObject).getSchema();

    final SafeFuture<Response<BuilderApiResponse<ResponseContainer>>> responseFuture =
        underTest.postAsync(
            TEST_PATH, Map.of(), requestBodyObject, false, responseTypeDefinition, TEST_TIMEOUT);

    // this will fail if there are uncaught exceptions in other threads
    Waiter.waitFor(() -> assertThat(responseFuture).isDone(), 30, TimeUnit.SECONDS, false);

    SafeFutureAssert.assertThatSafeFuture(responseFuture)
        .isCompletedExceptionallyWithMessage("error!!");
  }

  @Test
  void postsAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final RequestContainer requestBodyObject = new RequestContainer(2);
    final SafeFuture<Response<Void>> responseFuture =
        underTest.postAsync(TEST_PATH, requestBodyObject, false, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.payload()).isNull();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"input\":\"2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void postsAsSszAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final RequestContainer requestBodyObject = new RequestContainer(2);
    final SafeFuture<Response<Void>> responseFuture =
        underTest.postAsync(TEST_PATH, requestBodyObject, true, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isNull();
              assertThat(response.isFailure()).isFalse();
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload()).isNull();
              assertThat(response.receivedAsSsz()).isFalse();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody())
        .satisfies(
            buffer ->
                assertThat(
                        RequestContainer.SSZ_SCHEMA.sszDeserialize(
                            Bytes.wrap(buffer.readByteArray())))
                    .isEqualTo(requestBodyObject));
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void postsAsSszAsyncDetectsUnsupportedMediaTypeError() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(415));

    final RequestContainer requestBodyObject = new RequestContainer(2);
    final SafeFuture<Response<Void>> responseFuture =
        underTest.postAsync(TEST_PATH, requestBodyObject, true, TEST_TIMEOUT);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.errorMessage()).isEqualTo("Unsupported Media Type");
              assertThat(response.isFailure()).isTrue();
              assertThat(response.isSuccess()).isFalse();
              assertThat(response.payload()).isNull();
              assertThat(response.receivedAsSsz()).isFalse();
              assertThat(response.isUnsupportedMediaTypeError()).isTrue();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody())
        .satisfies(
            buffer ->
                assertThat(
                        RequestContainer.SSZ_SCHEMA.sszDeserialize(
                            Bytes.wrap(buffer.readByteArray())))
                    .isEqualTo(requestBodyObject));
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  private static class FailingSerializableTypeDefinition
      implements DeserializableTypeDefinition<RequestContainer> {

    @Override
    public void serializeOpenApiType(final JsonGenerator gen) {}

    @Override
    public void serialize(final RequestContainer value, final JsonGenerator gen)
        throws IOException {
      throw new JsonGenerationException("error!!", null, null);
    }

    @Override
    public RequestContainer deserialize(final JsonParser parser) {
      // this should never be called
      return null;
    }

    @Override
    public DeserializableTypeDefinition<RequestContainer> withDescription(
        final String description) {
      return this;
    }
  }

  private static class RequestContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<RequestContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            "RequestContainer",
            List.of(NamedSchema.of("input", SszPrimitiveSchemas.UINT64_SCHEMA)),
            RequestContainer::new);

    private RequestContainer(
        final SszContainerSchema<RequestContainer> type, final TreeNode backingNode) {
      super(type, backingNode);
    }

    public RequestContainer(final long input) {
      super(SSZ_SCHEMA, SszUInt64.of(UInt64.fromLongBits(input)));
    }
  }

  private static class ResponseContainer extends AbstractSszImmutableContainer {
    public static final SszContainerSchema<ResponseContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            "ResponseContainer",
            List.of(NamedSchema.of("output", SszPrimitiveSchemas.UINT64_SCHEMA)),
            ResponseContainer::new);

    public UInt64 getOutput() {
      return ((SszUInt64) getAny(0)).get();
    }

    private ResponseContainer(
        final SszContainerSchema<ResponseContainer> type, final TreeNode backingNode) {
      super(type, backingNode);
    }

    public ResponseContainer(final long output) {
      super(SSZ_SCHEMA, SszUInt64.of(UInt64.fromLongBits(output)));
    }
  }
}

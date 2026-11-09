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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT8_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.restapi.CustomResponseTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class RestApiRequestTest {
  private static final ParameterMetadata<String> STR_PARAM =
      new ParameterMetadata<>("str", STRING_TYPE);
  private static final ParameterMetadata<Integer> INT_PARAM =
      new ParameterMetadata<>("int", RAW_INTEGER_TYPE);
  private static final ParameterMetadata<Boolean> BOOL_PARAM =
      new ParameterMetadata<>("bool", BOOLEAN_TYPE);
  private static final ParameterMetadata<Byte> BYTE_PARAM =
      new ParameterMetadata<>("byte", BYTE_TYPE);
  private static final ParameterMetadata<Byte> UINT8_PARAM =
      new ParameterMetadata<>("uint8", UINT8_TYPE);
  private static final EndpointMetadata METADATA =
      EndpointMetadata.get("/foo/:bool/:int/:str/:byte")
          .operationId("foo")
          .summary("Foo Summary")
          .description("description")
          .response(SC_OK, "Good")
          .pathParam(BOOL_PARAM)
          .pathParam(BYTE_PARAM)
          .pathParam(INT_PARAM)
          .pathParam(STR_PARAM)
          .queryParam(BOOL_PARAM)
          .queryParam(BYTE_PARAM)
          .queryParam(INT_PARAM)
          .queryParam(STR_PARAM)
          .requestBodyType(STRING_TYPE)
          .build();
  private static final EndpointMetadata ASYNC_METADATA =
      EndpointMetadata.get("/async")
          .operationId("async")
          .summary("Async")
          .description("Async response")
          .response(SC_OK, "Success", STRING_TYPE)
          .build();

  private final Context context = mock(Context.class);
  private final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
  private final AsyncContext asyncContext = mock(AsyncContext.class);

  @Test
  void shouldGiveSensibleErrorMessageFromOptionalQueryParameter() {
    when(context.queryParamMap()).thenReturn(Map.of("uint8", List.of("-1")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThatThrownBy(() -> request.getOptionalQueryParameter(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uint8");
  }

  @Test
  void shouldGiveSensibleErrorMessageFromQueryParameter() {
    when(context.queryParamMap()).thenReturn(Map.of("uint8", List.of("-1")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThatThrownBy(() -> request.getQueryParameter(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uint8");
  }

  @Test
  void shouldGiveSensibleErrorMessageFromQueryList() {
    when(context.queryParamMap()).thenReturn(Map.of("uint8", List.of("-1", "2", "3")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThatThrownBy(() -> request.getQueryParameterList(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uint8");
  }

  @Test
  void shouldGiveSensibleErrorMessageFromPathParameter() {
    when(context.pathParamMap()).thenReturn(Map.of("uint8", "-1"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThatThrownBy(() -> request.getPathParameter(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UINT8_PARAM.getName());
  }

  @Test
  void shouldGiveSensibleErrorMessageFromRequestHeader() {
    when(context.headerMap()).thenReturn(Map.of("uint8", "-1"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThatThrownBy(() -> request.getRequestHeader(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UINT8_PARAM.getName());
    assertThatThrownBy(() -> request.getOptionalRequestHeader(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UINT8_PARAM.getName());
  }

  @Test
  void shouldGiveSensibleErrorMessageFromEmptyRequestHeader() {
    when(context.headerMap()).thenReturn(Map.of());
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getOptionalRequestHeader(UINT8_PARAM)).isEmpty();
    assertThatThrownBy(() -> request.getRequestHeader(UINT8_PARAM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UINT8_PARAM.getName());
  }

  @Test
  void shouldDeserializeStringFromParameters() {
    when(context.pathParamMap()).thenReturn(Map.of("str", "byeWorld"));
    when(context.queryParamMap()).thenReturn(Map.of("str", List.of("helloWorld")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(STR_PARAM)).isEqualTo("byeWorld");
    assertThat(request.getQueryParameter(STR_PARAM)).isEqualTo("helloWorld");
  }

  @Test
  void shouldDeserializeStringFromHeaders() {
    when(context.headerMap()).thenReturn(Map.of("str", "byeWorld"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(STR_PARAM)).isEqualTo("byeWorld");
    assertThat(request.getOptionalRequestHeader(STR_PARAM)).isEqualTo(Optional.of("byeWorld"));
  }

  @Test
  void shouldDeserializeIntegerFromParameters() {
    when(context.pathParamMap()).thenReturn(Map.of("int", "1234"));
    when(context.queryParamMap()).thenReturn(Map.of("int", List.of("4321")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(INT_PARAM)).isEqualTo(1234);
    assertThat(request.getQueryParameter(INT_PARAM)).isEqualTo(4321);
  }

  @Test
  void shouldDeserializeIntegerFromHeaders() {
    when(context.headerMap()).thenReturn(Map.of("int", "1234"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(INT_PARAM)).isEqualTo(1234);
    assertThat(request.getOptionalRequestHeader(INT_PARAM)).isEqualTo(Optional.of(1234));
  }

  @Test
  void shouldDeserializeBooleanFromParameters() {
    when(context.pathParamMap()).thenReturn(Map.of("bool", "true"));
    when(context.queryParamMap()).thenReturn(Map.of("bool", List.of("false")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(BOOL_PARAM)).isEqualTo(true);
    assertThat(request.getQueryParameter(BOOL_PARAM)).isEqualTo(false);
  }

  @Test
  void shouldDeserializeBooleanFromHeaders() {
    when(context.headerMap()).thenReturn(Map.of("bool", "true"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(BOOL_PARAM)).isEqualTo(true);
    assertThat(request.getOptionalRequestHeader(BOOL_PARAM)).isEqualTo(Optional.of(true));
  }

  @ParameterizedTest
  @MethodSource("getContentTypeAndExpectedResponseType")
  void shouldReturnExpectedResponseType(
      final String contentType, final String expectedResponseType) {
    final ResponseContentTypeDefinition<Bytes32> responseContentTypeDefinition =
        new CustomResponseTypeDefinition<>(ContentTypes.OCTET_STREAM, BYTES32_TYPE);

    when(context.header(eq(HEADER_ACCEPT))).thenReturn(contentType);
    EndpointMetadata metadata =
        EndpointMetadata.get("/foo")
            .operationId("foo")
            .description("foobar")
            .summary("Foo Summary")
            .response(SC_OK, "Good", BYTES32_TYPE, responseContentTypeDefinition)
            .build();
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, metadata);
    assertThat(request.getResponseContentType(SC_OK)).isEqualTo(expectedResponseType);
  }

  @Test
  void shouldSerializeAsyncResponse() throws Exception {
    final ServletOutputStream outputStream = mock(ServletOutputStream.class);
    when(context.outputStream()).thenReturn(outputStream);
    final SafeFuture<AsyncApiResponse> endpointResponse = new SafeFuture<>();
    final CapturedAsyncResponse capturedResponse = respondAsync(endpointResponse);

    endpointResponse.complete(AsyncApiResponse.respondOk("response"));

    assertThat(capturedResponse.requestResponse().isDone()).isTrue();
    assertThat(capturedResponse.requestResponse().isCompletedExceptionally()).isFalse();
    verify(context).status(SC_OK);
    verify(context).contentType(ContentTypes.JSON);
    verify(outputStream, atLeastOnce()).write(any(byte[].class), anyInt(), anyInt());
  }

  @Test
  void shouldCancelRequestResponseOnConnectionErrorWithoutCancellingEndpointResponse()
      throws Exception {
    final SafeFuture<AsyncApiResponse> endpointResponse = new SafeFuture<>();
    final CapturedAsyncResponse capturedResponse = respondAsync(endpointResponse);

    capturedResponse
        .listener()
        .onError(new AsyncEvent(asyncContext, new IOException("Client disconnected")));

    assertRequestResponseCancelled(capturedResponse, endpointResponse);
  }

  @Test
  void shouldCancelRequestResponseOnTimeoutWithoutCancellingEndpointResponse() throws Exception {
    final SafeFuture<AsyncApiResponse> endpointResponse = new SafeFuture<>();
    final CapturedAsyncResponse capturedResponse = respondAsync(endpointResponse);

    capturedResponse.listener().onTimeout(new AsyncEvent(asyncContext));

    assertRequestResponseCancelled(capturedResponse, endpointResponse);
  }

  @Test
  void shouldCancelRequestResponseOnAsyncCompletionWithoutCancellingEndpointResponse()
      throws Exception {
    final SafeFuture<AsyncApiResponse> endpointResponse = new SafeFuture<>();
    final CapturedAsyncResponse capturedResponse = respondAsync(endpointResponse);

    capturedResponse.listener().onComplete(new AsyncEvent(asyncContext));

    assertRequestResponseCancelled(capturedResponse, endpointResponse);
  }

  @Test
  void shouldRegisterCancellationListenerWithNewAsyncCycle() throws Exception {
    final SafeFuture<AsyncApiResponse> endpointResponse = new SafeFuture<>();
    final CapturedAsyncResponse capturedResponse = respondAsync(endpointResponse);
    final AsyncContext newAsyncContext = mock(AsyncContext.class);

    capturedResponse.listener().onStartAsync(new AsyncEvent(newAsyncContext));

    verify(newAsyncContext).addListener(capturedResponse.listener());
  }

  public static Stream<Arguments> getContentTypeAndExpectedResponseType() {
    return Stream.of(
        Arguments.of(
            "application/octet-stream;q=0.9, application/json;q=0.4", "application/octet-stream"),
        Arguments.of("application/octet-stream;q=0.3, application/json;q=0.4", "application/json"),
        Arguments.of("application/json", "application/json"),
        Arguments.of("application/octet-stream", "application/octet-stream"),
        Arguments.of(null, "application/json"));
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToHex")
  void shouldDeserializeByteFromParameters(final byte value, final String stringValue) {
    when(context.pathParamMap()).thenReturn(Map.of("byte", stringValue));
    when(context.queryParamMap()).thenReturn(Map.of("byte", List.of(stringValue)));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(BYTE_PARAM)).isEqualTo(value);
    assertThat(request.getQueryParameter(BYTE_PARAM)).isEqualTo(value);
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToHex")
  void shouldDeserializeByteFromHeaders(final byte value, final String stringValue) {
    when(context.headerMap()).thenReturn(Map.of("byte", stringValue));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(BYTE_PARAM)).isEqualTo(value);
    assertThat(request.getOptionalRequestHeader(BYTE_PARAM)).isEqualTo(Optional.of(value));
  }

  static Stream<Arguments> unsignedBytesToHex() {
    return Stream.of(
        Arguments.of(Byte.MIN_VALUE, "0x80"),
        Arguments.of((byte) -1, "0xff"),
        Arguments.of((byte) 0, "0x00"),
        Arguments.of((byte) 1, "0x01"),
        Arguments.of(Byte.MAX_VALUE, "0x7f"));
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToDecimal")
  void shouldDeserializeUInt8FromParameters(final byte value, final String stringValue) {
    when(context.pathParamMap()).thenReturn(Map.of("uint8", stringValue));
    when(context.queryParamMap()).thenReturn(Map.of("uint8", List.of(stringValue)));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(UINT8_PARAM)).isEqualTo(value);
    assertThat(request.getQueryParameter(UINT8_PARAM)).isEqualTo(value);
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToDecimal")
  void shouldDeserializeUInt8FromHeaders(final byte value, final String stringValue) {
    when(context.headerMap()).thenReturn(Map.of("uint8", stringValue));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(UINT8_PARAM)).isEqualTo(value);
    assertThat(request.getOptionalRequestHeader(UINT8_PARAM)).isEqualTo(Optional.of(value));
  }

  static Stream<Arguments> unsignedBytesToDecimal() {
    return Stream.of(
        Arguments.of(Byte.MIN_VALUE, "128"),
        Arguments.of((byte) -1, "255"),
        Arguments.of((byte) 0, "0"),
        Arguments.of((byte) 1, "1"),
        Arguments.of(Byte.MAX_VALUE, "127"));
  }

  @Test
  void shouldRetrieveCorrectHeader() {
    when(context.headerMap())
        .thenReturn(
            Map.of(
                INT_PARAM.getName(), "1234",
                BOOL_PARAM.getName(), "true",
                STR_PARAM.getName(), "helloWorld"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(INT_PARAM)).isEqualTo(1234);
    assertThat(request.getOptionalRequestHeader(BOOL_PARAM)).isEqualTo(Optional.of(true));
    assertThat(request.getRequestHeader(STR_PARAM)).isEqualTo("helloWorld");
  }

  @Test
  void shouldRetrieveCaseInsensitiveHeader() {
    final ParameterMetadata<String> abcParam = new ParameterMetadata<>("ABC", STRING_TYPE);
    when(context.headerMap()).thenReturn(Map.of("abc", "helloWorld"));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getRequestHeader(abcParam)).isEqualTo("helloWorld");
  }

  @Test
  void shouldGetQueryParameterListTypeInteger() {
    when(context.queryParamMap()).thenReturn(Map.of("int", List.of("1", "2", "3")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);

    assertThat(request.getQueryParameterList(INT_PARAM)).isEqualTo(List.of(1, 2, 3));
  }

  @Test
  void shouldGetQueryParameterListWhenEmpty() {
    when(context.queryParamMap()).thenReturn(Map.of());
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);

    assertThat(request.getQueryParameterList(INT_PARAM)).isEqualTo(List.of());
  }

  @Test
  public void whenRequestBodyIsAbsent_GetOptionalRequestBodyShouldReturnEmpty()
      throws JsonProcessingException {
    when(context.bodyInputStream()).thenReturn(IOUtils.toInputStream("", StandardCharsets.UTF_8));

    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    final Optional<Object> requestBody = request.getOptionalRequestBody();

    assertThat(requestBody).isEmpty();
  }

  @Test
  public void whenRequestBodyIsPresent_GetOptionalRequestBodyShouldReturnExpectedBody()
      throws JsonProcessingException {
    when(context.bodyInputStream())
        .thenReturn(IOUtils.toInputStream("\"hello\"", StandardCharsets.UTF_8));

    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    final Optional<String> requestBody = request.getOptionalRequestBody();

    assertThat(requestBody).hasValue("hello");
  }

  @Test
  public void shouldNeverResetInputStreamWhileCheckingIfItHasContent() throws Exception {
    final InputStream inputStream = spy(IOUtils.toInputStream("", StandardCharsets.UTF_8));
    when(context.bodyInputStream()).thenReturn(inputStream);

    new JavalinRestApiRequest(context, METADATA).getOptionalRequestBody();

    verify(inputStream, never()).reset();
  }

  @Test
  public void whenParsingOptionalBodyThrowsJsonProcessingException_ShouldRethrow() {
    final InputStream inputStream = IOUtils.toInputStream("[", StandardCharsets.UTF_8);
    when(context.bodyInputStream()).thenReturn(inputStream);

    assertThatThrownBy(() -> new JavalinRestApiRequest(context, METADATA).getOptionalRequestBody())
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  public void whenUnderlyingInputStreamThrowsIOException_ShouldThrowRuntimeWithCause()
      throws Exception {
    final InputStream inputStream = spy(IOUtils.toInputStream("", StandardCharsets.UTF_8));
    when(inputStream.read()).thenThrow(new IOException("Error"));
    when(context.bodyInputStream()).thenReturn(inputStream);

    assertThatThrownBy(() -> new JavalinRestApiRequest(context, METADATA).getOptionalRequestBody())
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IOException.class)
        .hasMessageContaining("Error reading request body");
  }

  private CapturedAsyncResponse respondAsync(final SafeFuture<AsyncApiResponse> endpointResponse) {
    when(context.req()).thenReturn(servletRequest);
    when(servletRequest.getAsyncContext()).thenReturn(asyncContext);
    final AtomicReference<CompletableFuture<?>> requestResponse = new AtomicReference<>();
    doAnswer(
            invocation -> {
              final Supplier<? extends CompletableFuture<?>> responseSupplier =
                  invocation.getArgument(0);
              requestResponse.set(responseSupplier.get());
              return null;
            })
        .when(context)
        .future(any());

    new JavalinRestApiRequest(context, ASYNC_METADATA).respondAsync(endpointResponse);

    final ArgumentCaptor<AsyncListener> listenerCaptor =
        ArgumentCaptor.forClass(AsyncListener.class);
    verify(asyncContext).addListener(listenerCaptor.capture());
    return new CapturedAsyncResponse(requestResponse.get(), listenerCaptor.getValue());
  }

  private void assertRequestResponseCancelled(
      final CapturedAsyncResponse capturedResponse,
      final SafeFuture<AsyncApiResponse> endpointResponse) {
    assertThat(capturedResponse.requestResponse().isCancelled()).isTrue();
    assertThat(endpointResponse.isDone()).isFalse();
    assertThat(endpointResponse.getNumberOfDependents()).isZero();

    endpointResponse.complete(AsyncApiResponse.respondOk("late response"));

    assertThat(endpointResponse.isCancelled()).isFalse();
    verify(context, never()).outputStream();
  }

  private record CapturedAsyncResponse(
      CompletableFuture<?> requestResponse, AsyncListener listener) {}
}

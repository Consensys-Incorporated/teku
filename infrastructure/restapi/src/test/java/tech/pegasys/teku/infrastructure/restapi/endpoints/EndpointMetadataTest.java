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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.SERIALIZABLE_ONE_OF_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_A;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_B;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.HandlerType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.http.ContentTypeNotSupportedException;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TestObjA;
import tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TestType;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.CustomResponseTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.EndpointMetaDataBuilder;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfJsonRequestContentTypeDefinition.BodyTypeSelectorContext;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.JsonResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

class EndpointMetadataTest {

  private static final ParameterMetadata<String> STRING_PARAM =
      new ParameterMetadata<>("t", STRING_TYPE);

  @Test
  void shouldGetAllReferencedTypeDefinitions() {
    final DeserializableTypeDefinition<String> describedStringType =
        CoreTypes.string("describedString");
    final SerializableTypeDefinition<String> objectType1 =
        SerializableTypeDefinition.object(String.class).name("Test1").build();
    final SerializableTypeDefinition<String> objectType2 =
        SerializableTypeDefinition.object(String.class).name("Test2").build();
    final SerializableTypeDefinition<String> objectType3 =
        SerializableTypeDefinition.object(String.class)
            .name("Test4")
            .withField("type3", objectType2, __ -> null)
            .build();
    final EndpointMetadata metadata =
        new EndpointMetaDataBuilder()
            .method(HandlerType.GET)
            .path("/foo")
            .summary("foo")
            .description("foo")
            .operationId("foo")
            .response(200, "foo", CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
            .response(404, "foo", describedStringType)
            .response(
                500,
                "foo",
                List.of(
                    json(objectType1),
                    new CustomResponseTypeDefinition<>("application/octet-stream", objectType3)))
            .build();

    assertThat(metadata.getReferencedTypeDefinitions())
        .containsExactlyInAnyOrder(
            describedStringType,
            objectType1,
            objectType2,
            objectType3,
            CoreTypes.HTTP_ERROR_RESPONSE_TYPE,
            STRING_TYPE,
            CoreTypes.RAW_INTEGER_TYPE);
  }

  @Test
  void selectResponseContentType_shouldUseDefaultContentTypeWhenRequestedType() {
    final String contentType = ContentTypes.OCTET_STREAM;
    final EndpointMetadata metadata =
        validBuilder()
            .response(SC_OK, "Success", List.of(octetStream(STRING_TYPE)))
            .defaultResponseType(contentType)
            .build();
    assertThat(metadata.createResponseMetadata(SC_OK, Optional.empty(), "foo"))
        .isEqualTo(new ResponseMetadata(contentType, emptyMap()));
  }

  @Test
  void selectResponseContentType_shouldThrowExceptionWhenStatusCodeNotDeclared() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Success", STRING_TYPE).build();
    assertThatThrownBy(() -> metadata.createResponseMetadata(SC_NOT_FOUND, Optional.empty(), "foo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void
      selectResponseContentType_shouldReturnDefaultContentTypeWhenRequestedContentTypeNotSupported() {
    final String contentType = "application/foo";
    final EndpointMetadata metadata =
        validBuilder()
            .response(
                SC_OK,
                "Success",
                List.of(new CustomResponseTypeDefinition<>(contentType, STRING_TYPE)))
            .defaultResponseType(contentType)
            .build();
    assertThat(metadata.createResponseMetadata(SC_OK, Optional.of("foo"), "bar"))
        .isEqualTo(new ResponseMetadata(contentType, emptyMap()));
  }

  @Test
  void selectResponseContentType_shouldReturnRequestedTypeWhenAvailable() {
    final EndpointMetadata metadata =
        validBuilder()
            .response(SC_OK, "Success", List.of(json(STRING_TYPE), octetStream(STRING_TYPE)))
            .build();
    assertThat(
            metadata.createResponseMetadata(SC_OK, Optional.of(ContentTypes.OCTET_STREAM), "foo"))
        .isEqualTo(new ResponseMetadata(ContentTypes.OCTET_STREAM, emptyMap()));
  }

  @Test
  void selectResponseContentType_shouldReturnFirstTypeWhenAcceptAnythingSpecified() {
    final String contentType = "zz/zz";
    final EndpointMetadata metadata =
        validBuilder()
            .response(
                SC_OK,
                "Success",
                List.of(
                    new CustomResponseTypeDefinition<>(contentType, STRING_TYPE),
                    json(STRING_TYPE),
                    octetStream(STRING_TYPE)))
            .build();
    assertThat(metadata.createResponseMetadata(SC_OK, Optional.of("*/*"), "foo"))
        .isEqualTo(new ResponseMetadata(contentType, emptyMap()));
  }

  @Test
  void selectResponseContentType_shouldThrowExceptionWhenDefaultContentTypeNotSupported() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Foo").defaultResponseType("baa").build();
    assertThatThrownBy(() -> metadata.createResponseMetadata(SC_OK, Optional.empty(), "foo"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_shouldSerializeOpenApiDoc() throws IOException {
    final EndpointMetadata metadata =
        validBuilder()
            .pathParam(new ParameterMetadata<>("test", STRING_TYPE.withDescription("test2")))
            .queryParam(new ParameterMetadata<>("qtest", STRING_TYPE))
            .queryParamRequired(
                new ParameterMetadata<>("rq", RAW_INTEGER_TYPE.withDescription("testing")))
            .response(SC_OK, "Success", STRING_TYPE)
            .build();
    final JsonGenerator generator = mock(JsonGenerator.class);
    metadata.writeOpenApi(generator);
    verify(generator).writeArrayFieldStart("parameters");
  }

  @Test
  void queryParam_cannotSpecifyTwice() {
    assertThatThrownBy(() -> validBuilder().queryParam(STRING_PARAM).queryParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParamRequired_cannotSpecifyTwice() {
    assertThatThrownBy(
            () -> validBuilder().queryParamRequired(STRING_PARAM).queryParamRequired(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParamRequired_cannotSpecifyWithQueryParam() {
    assertThatThrownBy(
            () -> validBuilder().queryParam(STRING_PARAM).queryParamRequired(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParam_cannotSpecifyWithQueryParamRequired() {
    assertThatThrownBy(
            () -> validBuilder().queryParamRequired(STRING_PARAM).queryParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_cannotSpecifyTwice() {
    assertThatThrownBy(() -> validBuilder().pathParam(STRING_PARAM).pathParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_canSpecifyWithSameNameAsQueryParam() {
    assertThat(
            validBuilder()
                .pathParam(STRING_PARAM)
                .queryParam(STRING_PARAM)
                .response(SC_OK, "Success", STRING_TYPE)
                .build())
        .isInstanceOf(EndpointMetadata.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Content-Type", "Accept", "Authorization"})
  void header_cannotUseCertainNames(final String name) {
    final ParameterMetadata<String> parameterMetadata = new ParameterMetadata<>(name, STRING_TYPE);
    assertThatThrownBy(() -> validBuilder().header(parameterMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Request headers cannot be named Content-Type, Accept, or Authorization");
  }

  @Test
  void getRequestBody_shouldAcceptTypes() throws Exception {
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(STRING_TYPE).response(SC_OK, "Success", STRING_TYPE).build();
    final String body = metadata.getRequestBody(toStream("\"abcdef\""), Optional.empty());
    assertThat(body).isEqualTo("abcdef");
  }

  @Test
  void shouldAddSecurityToEndpoint() {
    final EndpointMetadata metadata =
        validBuilder().security("authBearer").response(SC_OK, "Success", STRING_TYPE).build();
    assertThat(metadata.getSecurity()).contains("authBearer");
  }

  @Test
  void shouldAddTagsToEndpoint() {
    final EndpointMetadata metadata =
        validBuilder()
            .tags(RestApiConstants.TAG_EXPERIMENTAL)
            .response(SC_OK, "Success", STRING_TYPE)
            .build();
    assertThat(metadata.getTags()).containsExactly(RestApiConstants.TAG_EXPERIMENTAL);
  }

  @Test
  void shouldDeprecateEndpoint() throws Exception {
    final EndpointMetadata metadata =
        validBuilder().deprecated(true).response(SC_OK, "Success", STRING_TYPE).build();
    final JsonGenerator generator = mock(JsonGenerator.class);
    metadata.writeOpenApi(generator);
    verify(generator).writeBooleanField("deprecated", true);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void getRequestBody_shouldAcceptLists() throws Exception {
    final DeserializableListTypeDefinition<String> type =
        new DeserializableListTypeDefinition(STRING_TYPE, Optional.empty(), Optional.empty());
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(type).response(SC_OK, "Success", STRING_TYPE).build();
    final List<String> requestBody =
        metadata.getRequestBody(toStream("[\"a\", \"b\", \"c\"]"), Optional.empty());
    assertThat(requestBody).isEqualTo(List.of("a", "b", "c"));
  }

  @Test
  void getRequestBody_shouldDetermineOneOf() throws Exception {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(SERIALIZABLE_ONE_OF_TYPE_DEFINITION, this::selector)
            .response(SC_OK, "Success")
            .build();

    final TestType body =
        metadata.getRequestBody(toStream("{\"value1\":\"FOO\"}"), Optional.empty());
    assertThat(body).isEqualTo(new TestObjA("FOO"));
  }

  @Test
  void getRequestBody_shouldGetBodyAsObject() throws JsonProcessingException {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(SERIALIZABLE_ONE_OF_TYPE_DEFINITION, this::selector)
            .response(SC_OK, "Success")
            .build();
    final OneOfTypeTestTypeDefinition.TestObjA a =
        metadata.getRequestBody(toStream("{\"value1\":\"FOO\"}"), Optional.empty());
    assertThat(a).isEqualTo(new OneOfTypeTestTypeDefinition.TestObjA("FOO"));
  }

  @Test
  void getRequestBody_shouldMatchSameType() throws JsonProcessingException {
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(TYPE_A).response(SC_OK, "Success").build();

    final OneOfTypeTestTypeDefinition.TestObjA a =
        metadata.getRequestBody(toStream("{\"value1\":\"FOO\"}"), Optional.empty());
    assertThat(a).isEqualTo(new OneOfTypeTestTypeDefinition.TestObjA("FOO"));
  }

  @Test
  void getRequestBody_shouldErrorIfSelectorDeterminesUndocumentedResult() {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(
                SERIALIZABLE_ONE_OF_TYPE_DEFINITION,
                __ ->
                    DeserializableTypeDefinition.string(TestType.class)
                        .parser(TestObjA::new)
                        .formatter(TestType::toString)
                        .build())
            .response(SC_OK, "Success")
            .build();

    assertThatThrownBy(
            () -> metadata.getRequestBody(toStream("{\"value1\":\"FOO\"}"), Optional.empty()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getRequestBody_shouldSupportOctetStream() throws Exception {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(
                SERIALIZABLE_ONE_OF_TYPE_DEFINITION,
                this::selector,
                bytes -> new TestObjA(bytes.toString()))
            .response(SC_OK, "Success")
            .build();
    final Bytes data = Bytes.fromHexString("0xABCD1234");
    final TestType body =
        metadata.getRequestBody(
            new ByteArrayInputStream(data.toArrayUnsafe()), Optional.of(ContentTypes.OCTET_STREAM));
    assertThat(body).isEqualTo(new TestObjA(data.toString()));
  }

  @Test
  void getRequestBody_shouldSupportOctetStreamWithoutSelector() throws Exception {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(
                STRING_TYPE, bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8))
            .response(SC_OK, "Success")
            .build();
    final byte[] data = "test".getBytes(StandardCharsets.UTF_8);
    final String body =
        metadata.getRequestBody(
            new ByteArrayInputStream(data), Optional.of(ContentTypes.OCTET_STREAM));
    assertThat(body).isEqualTo("test");
  }

  @Test
  void getRequestBody_shouldSupportJsonWithoutSelector() throws Exception {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(
                STRING_TYPE, bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8))
            .response(SC_OK, "Success")
            .build();

    final String body =
        metadata.getRequestBody(toStream("\"abcdef\""), Optional.of(ContentTypes.JSON));
    assertThat(body).isEqualTo("abcdef");
  }

  @Test
  void getRequestBody_shouldSupportJsonWithCharset() throws Exception {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(
                SERIALIZABLE_ONE_OF_TYPE_DEFINITION,
                this::selector,
                bytes -> new TestObjA(bytes.toString()))
            .response(SC_OK, "Success")
            .build();
    final TestType body =
        metadata.getRequestBody(
            toStream("{\"value1\":\"FOO\"}"), Optional.of("application/json; charset=UTF-8"));
    assertThat(body).isEqualTo(new TestObjA("FOO"));
  }

  @Test
  void getRequestBody_shouldThrowExceptionWhenContentTypeNotSupported() {
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(STRING_TYPE).response(SC_OK, "Success").build();
    assertThatThrownBy(
            () -> metadata.getRequestBody(toStream("abc"), Optional.of(ContentTypes.OCTET_STREAM)))
        .isInstanceOf(ContentTypeNotSupportedException.class);
  }

  private InputStream toStream(final String json) {
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }

  private DeserializableTypeDefinition<? extends TestType> selector(
      final BodyTypeSelectorContext context) {
    final ObjectMapper mapper = new ObjectMapper();
    try {
      final JsonNode jsonNode = mapper.readTree(context.getBody());
      if (jsonNode.has("value1")) {
        return TYPE_A;
      }
      if (jsonNode.has("value2")) {
        return TYPE_B;
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not parse object to find one-of type information");
    }
    throw new IllegalStateException("Object type not found in one-of selector");
  }

  private EndpointMetaDataBuilder validBuilder() {
    return EndpointMetadata.get("/foo")
        .operationId("fooId")
        .summary("foo summary")
        .description("foo description");
  }

  static <T> ResponseContentTypeDefinition<T> json(final SerializableTypeDefinition<T> type) {
    return new JsonResponseContentTypeDefinition<>(type);
  }

  /** Actually still writing json for convenience but declaring it as application/octet-stream */
  static <T> ResponseContentTypeDefinition<T> octetStream(
      final SerializableTypeDefinition<T> type) {
    return new OctetStreamResponseContentTypeDefinition<>(
        (data, out) -> JsonUtil.serializeToBytes(data, type, out), t -> emptyMap());
  }
}

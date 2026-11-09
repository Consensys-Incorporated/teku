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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.json.JsonTestUtil.getObject;
import static tech.pegasys.teku.infrastructure.json.JsonTestUtil.parse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.CustomResponseTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.JsonResponseContentTypeDefinition;

class OpenApiDocBuilderTest {

  @Test
  void shouldBuildValidDocWithMinimalInfo() throws Exception {
    final String json = validBuilder().build();
    final Map<String, Object> result = parse(json);

    assertThat(result).containsEntry("openapi", OpenApiDocBuilder.OPENAPI_VERSION);
    assertThat(getObject(result, "info"))
        .containsExactly(entry("title", "My Title"), entry("version", "My Version"));
  }

  @Test
  void shouldFailIfTitleNotSupplied() {
    assertThatThrownBy(new OpenApiDocBuilder().version("version")::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("title must be supplied");
  }

  @Test
  void shouldFailIfVersionNotSupplied() {
    assertThatThrownBy(new OpenApiDocBuilder().title("title")::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("version must be supplied");
  }

  @Test
  void shouldIncludeLicense() throws Exception {
    final Map<String, Object> result = parse(validBuilder().license("foo", "bar").build());

    assertThat(getObject(result, "info", "license"))
        .containsExactly(entry("name", "foo"), entry("url", "bar"));
  }

  @Test
  void shouldIncludeSecurityComponentIfDefined() throws Exception {
    final Map<String, Object> openApiDoc = parse(validBuilder().bearerAuth(true).build());
    final Map<String, Object> components = getObject(openApiDoc, "components");
    final Map<String, Object> securitySchemes = getObject(components, "securitySchemes");
    assertThat(getObject(securitySchemes, "bearerAuth"))
        .containsExactly(entry("type", "http"), entry("scheme", "bearer"));
  }

  @Test
  void shouldIncludeDescription() throws Exception {
    final Map<String, Object> result =
        parse(validBuilder().description("Some description").build());
    assertThat(getObject(result, "info")).containsEntry("description", "Some description");
  }

  @Test
  void shouldIncludeSimpleEndpoint() throws Exception {
    final RestApiEndpoint endpoint =
        endpoint(
            EndpointMetadata.get("/test/endpoint")
                .operationId("myOperationId")
                .summary("The summary")
                .description("The description")
                .response(200, "A simple string", CoreTypes.STRING_TYPE)
                .build());
    final Map<String, Object> result = parse(validBuilder().endpoint(endpoint).build());
    final Map<String, Object> endpointDefinition = getObject(result, "paths", "/test/endpoint");
    assertThat(endpointDefinition).containsOnlyKeys("get");

    final Map<String, Object> getHandler = getObject(endpointDefinition, "get");
    assertThat(getHandler).containsOnlyKeys("operationId", "summary", "description", "responses");
    assertThat(getHandler)
        .contains(
            entry("operationId", "myOperationId"),
            entry("summary", "The summary"),
            entry("description", "The description"));

    final Map<String, Object> responses = getObject(getHandler, "responses");
    assertThat(responses).containsOnlyKeys("200", "400", "500");
    final Map<String, Object> okResponses = getObject(responses, "200");
    assertThat(okResponses).containsOnlyKeys("description", "content");
    assertThat(okResponses).containsEntry("description", "A simple string");

    final Map<String, Object> okResponseContent = getObject(okResponses, "content");
    assertThat(okResponseContent).containsOnlyKeys("application/json");
    final Map<String, Object> jsonContent = getObject(okResponseContent, "application/json");
    assertThat(jsonContent).containsOnly(entry("schema", Map.of("type", "string")));
  }

  @Test
  void shouldIncludeEndpointWithMultipleResponseTypes() throws Exception {
    final RestApiEndpoint endpoint =
        endpoint(
            EndpointMetadata.get("/test/endpoint")
                .operationId("myOperationId")
                .summary("The summary")
                .description("The description")
                .response(
                    200,
                    "It depends",
                    List.of(
                        new JsonResponseContentTypeDefinition<>(CoreTypes.STRING_TYPE),
                        new CustomResponseTypeDefinition<>("uint", CoreTypes.UINT64_TYPE)))
                .response(404, "Not 'ere gov", CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
                .build());
    final Map<String, Object> result = parse(validBuilder().endpoint(endpoint).build());
    final Map<String, Object> responses =
        getObject(result, "paths", "/test/endpoint", "get", "responses");
    assertThat(responses).containsOnlyKeys("200", "400", "404", "500");

    final Map<String, Object> okResponses = getObject(responses, "200");
    assertThat(okResponses).containsEntry("description", "It depends");
    final Map<String, Object> okContent = getObject(okResponses, "content");
    assertThat(okContent).containsOnlyKeys("application/json", "uint");
    assertThat(getObject(okContent, "application/json", "schema"))
        .containsOnly(entry("type", "string"));
    assertThat(getObject(okContent, "uint", "schema")).containsEntry("format", "uint64");

    final Map<String, Object> notFoundResponses = getObject(responses, "404");
    assertThat(notFoundResponses).containsEntry("description", "Not 'ere gov");
    final Map<String, Object> notFoundContent = getObject(notFoundResponses, "content");
    assertThat(notFoundContent).containsOnlyKeys("application/json");
    assertThat(getObject(notFoundContent, "application/json"))
        .containsOnly(
            entry(
                "schema",
                Map.of(
                    "$ref",
                    "#/components/schemas/"
                        + CoreTypes.HTTP_ERROR_RESPONSE_TYPE.getTypeName().orElseThrow())));

    // Should include referenced types as schemas
    final Map<String, Object> schemas = getObject(result, "components", "schemas");
    assertThat(schemas).containsOnlyKeys("HttpErrorResponse");
    // Full type should be serialized in schemas
    assertThat(getObject(schemas, "HttpErrorResponse"))
        .isEqualTo(
            JsonTestUtil.parse(
                JsonUtil.serialize(CoreTypes.HTTP_ERROR_RESPONSE_TYPE::serializeOpenApiType)));
  }

  private OpenApiDocBuilder validBuilder() {
    return new OpenApiDocBuilder().title("My Title").version("My Version");
  }

  private RestApiEndpoint endpoint(final EndpointMetadata metadata) {
    return new RestApiEndpoint(metadata) {
      @Override
      public void handleRequest(final RestApiRequest request) {}
    };
  }
}

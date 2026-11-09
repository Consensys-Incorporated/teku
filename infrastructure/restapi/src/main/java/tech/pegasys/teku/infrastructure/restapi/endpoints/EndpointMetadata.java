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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.function.IOFunction;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.MilestoneSpecificOctetStreamRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OctetStreamRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfArrayJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfJsonRequestContentTypeDefinition.BodyTypeSelector;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.RequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.SimpleJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.JsonResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class EndpointMetadata {
  private final HandlerType method;
  private final String path;
  private final String operationId;
  private final String summary;
  private final Optional<String> security;
  private final String description;
  private final boolean requiredRequestBody;
  private final boolean deprecated;
  private final String defaultResponseContentType;
  private final Map<String, OpenApiResponse> responses;
  private final String defaultRequestContentType;
  private final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes;
  private final List<String> tags;
  private final Map<String, StringValueTypeDefinition<?>> pathParams;
  private final Map<String, StringValueTypeDefinition<?>> requiredQueryParams;
  private final Map<String, StringValueTypeDefinition<?>> queryParams;
  private final Map<String, StringValueTypeDefinition<?>> queryParamsAllowEmpty;
  private final Map<String, StringValueTypeDefinition<?>> queryListParams;
  private final Map<String, StringValueTypeDefinition<?>> requiredHeaders;
  private final Map<String, StringValueTypeDefinition<?>> headers;

  private EndpointMetadata(
      final HandlerType method,
      final String path,
      final String operationId,
      final String summary,
      final Optional<String> security,
      final String description,
      final boolean deprecated,
      final String defaultResponseContentType,
      final Map<String, OpenApiResponse> responses,
      final String defaultRequestContentType,
      final boolean requiredRequestBody,
      final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes,
      final List<String> tags,
      final Map<String, StringValueTypeDefinition<?>> pathParams,
      final Map<String, StringValueTypeDefinition<?>> queryParams,
      final Map<String, StringValueTypeDefinition<?>> queryParamsAllowEmpty,
      final Map<String, StringValueTypeDefinition<?>> requiredQueryParams,
      final Map<String, StringValueTypeDefinition<?>> queryListParams,
      final Map<String, StringValueTypeDefinition<?>> requiredHeaders,
      final Map<String, StringValueTypeDefinition<?>> headers) {
    this.method = method;
    this.path = path;
    this.operationId = operationId;
    this.summary = summary;
    this.security = security;
    this.description = description;
    this.requiredRequestBody = requiredRequestBody;
    this.deprecated = deprecated;
    this.defaultResponseContentType = defaultResponseContentType;
    this.responses = responses;
    this.defaultRequestContentType = defaultRequestContentType;
    this.requestBodyTypes = requestBodyTypes;
    this.tags = tags;
    this.pathParams = pathParams;
    this.queryParams = queryParams;
    this.queryParamsAllowEmpty = queryParamsAllowEmpty;
    this.requiredQueryParams = requiredQueryParams;
    this.queryListParams = queryListParams;
    this.requiredHeaders = requiredHeaders;
    this.headers = headers;
  }

  public static EndpointMetaDataBuilder get(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.GET).path(path);
  }

  public static EndpointMetaDataBuilder post(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.POST).path(path);
  }

  public static EndpointMetaDataBuilder put(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.PUT).path(path);
  }

  public static EndpointMetaDataBuilder delete(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.DELETE).path(path);
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody(final InputStream body, final Optional<String> maybeContentType)
      throws JsonProcessingException {
    checkArgument(!requestBodyTypes.isEmpty(), "requestBodyType has not been defined");

    try {
      final String contentType = selectRequestContentType(maybeContentType);
      final RequestContentTypeDefinition<T> requestContentTypeDefinition =
          (RequestContentTypeDefinition<T>) requestBodyTypes.get(contentType);
      final T result = requestContentTypeDefinition.deserialize(body);
      if (result == null) {
        throw new MissingRequestBodyException();
      }
      return result;
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody(final InputStream body, final Map<String, String> headers)
      throws JsonProcessingException {
    checkArgument(!requestBodyTypes.isEmpty(), "requestBodyType has not been defined");

    final Optional<String> maybeContentType = Optional.ofNullable(headers.get("Content-Type"));

    try {
      final String contentType = selectRequestContentType(maybeContentType);
      final RequestContentTypeDefinition<T> requestContentTypeDefinition =
          (RequestContentTypeDefinition<T>) requestBodyTypes.get(contentType);
      final T result = requestContentTypeDefinition.deserialize(body, headers);
      if (result == null) {
        throw new MissingRequestBodyException();
      }
      return result;
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String selectRequestContentType(final Optional<String> maybeContentType)
      throws BadRequestException {
    return ContentTypes.getRequestContentType(
        maybeContentType, requestBodyTypes.keySet(), defaultRequestContentType);
  }

  public HandlerType getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public Optional<String> getSecurity() {
    return security;
  }

  public List<String> getTags() {
    return tags;
  }

  public ResponseContentTypeDefinition<?> getResponseType(
      final int statusCode, final String contentType) {
    final OpenApiResponse response = responses.get(Integer.toString(statusCode));
    checkArgument(response != null, "Unexpected response for status code %s", statusCode);

    final ResponseContentTypeDefinition<?> responseType = response.getType(contentType);
    checkArgument(
        responseType != null,
        "Unexpected content type %s for status code %s",
        contentType,
        statusCode);
    return responseType;
  }

  public boolean isNoContentResponse(final int statusCode) {
    final OpenApiResponse response = responses.get(Integer.toString(statusCode));
    return response != null && response.getSupportedContentTypes().isEmpty();
  }

  @SuppressWarnings("unchecked")
  public <T> ResponseMetadata createResponseMetadata(
      final int statusCode, final Optional<String> acceptHeader, final T response) {
    final OpenApiResponse openApiResponse = responses.get(Integer.toString(statusCode));
    if (openApiResponse == null) {
      throw new IllegalArgumentException("Status code " + statusCode + " not supported");
    }
    final String selectedType = selectContentType(openApiResponse, acceptHeader);
    final ResponseContentTypeDefinition<T> typeDefinition =
        (ResponseContentTypeDefinition<T>) openApiResponse.getType(selectedType);
    return new ResponseMetadata(selectedType, typeDefinition.getAdditionalHeaders(response));
  }

  public String getContentType(final int statusCode, final Optional<String> acceptHeader) {
    final OpenApiResponse openApiResponse = responses.get(Integer.toString(statusCode));
    return selectContentType(openApiResponse, acceptHeader);
  }

  private String selectContentType(
      final OpenApiResponse openApiResponse, final Optional<String> acceptHeader) {
    final List<String> supportedTypes = openApiResponse.getSupportedContentTypes();
    final String selectedType =
        ContentTypes.getResponseContentType(supportedTypes, acceptHeader)
            .orElse(defaultResponseContentType);
    checkState(
        supportedTypes.contains(selectedType),
        "Response content type %s was selected but is not supported.",
        selectedType);
    return selectedType;
  }

  @SuppressWarnings("unchecked")
  public <T> void serialize(
      final int statusCode, final String contentType, final T response, final OutputStream out)
      throws JsonProcessingException {
    final ResponseContentTypeDefinition<T> type =
        (ResponseContentTypeDefinition<T>) getResponseType(statusCode, contentType);
    try {
      type.serialize(response, out);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void writeOpenApi(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart(method.name().toLowerCase(Locale.ROOT));
    writeTags(gen);
    gen.writeStringField("operationId", operationId);
    gen.writeStringField("summary", summary);
    gen.writeStringField("description", description);
    if (deprecated) {
      gen.writeBooleanField("deprecated", true);
    }
    if (!pathParams.isEmpty()
        || !queryParams.isEmpty()
        || !queryParamsAllowEmpty.isEmpty()
        || !requiredQueryParams.isEmpty()
        || !queryListParams.isEmpty()
        || !requiredHeaders.isEmpty()
        || !headers.isEmpty()) {
      gen.writeArrayFieldStart("parameters");
      writeParameters(gen, pathParams, "path", true, false, false);
      writeParameters(gen, requiredQueryParams, "query", true, false, false);
      writeParameters(gen, queryParams, "query", false, false, false);
      writeParameters(gen, queryParamsAllowEmpty, "query", false, false, true);
      writeParameters(gen, queryListParams, "query", false, true, false);
      writeParameters(gen, requiredHeaders, "header", true, false, false);
      writeParameters(gen, headers, "header", false, false, false);
      gen.writeEndArray();
    }

    if (!requestBodyTypes.isEmpty()) {
      gen.writeObjectFieldStart("requestBody");
      if (!requiredRequestBody) {
        gen.writeBooleanField("required", false);
      }
      gen.writeObjectFieldStart("content");
      for (Map.Entry<String, RequestContentTypeDefinition<?>> requestTypeEntry :
          requestBodyTypes.entrySet()) {
        gen.writeObjectFieldStart(requestTypeEntry.getKey());
        gen.writeFieldName("schema");
        requestTypeEntry.getValue().serializeOpenApiTypeOrReference(gen);
        gen.writeEndObject();
      }
      gen.writeEndObject();
      gen.writeEndObject();
    }

    if (security.isPresent()) {
      gen.writeArrayFieldStart("security");
      gen.writeStartObject();
      gen.writeArrayFieldStart(security.get());
      gen.writeEndArray();
      gen.writeEndObject();
      gen.writeEndArray();
    }

    gen.writeObjectFieldStart("responses");
    for (Entry<String, OpenApiResponse> responseEntry : responses.entrySet()) {
      gen.writeFieldName(responseEntry.getKey());
      responseEntry.getValue().writeOpenApi(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  private void writeParameters(
      final JsonGenerator gen,
      final Map<String, StringValueTypeDefinition<?>> fields,
      final String parameterUsedIn,
      final boolean isMandatoryField,
      final boolean isList,
      final boolean allowEmptyValue)
      throws IOException {
    for (Map.Entry<String, StringValueTypeDefinition<?>> entry : fields.entrySet()) {
      gen.writeStartObject();
      gen.writeObjectField("name", entry.getKey());
      if (isMandatoryField) {
        gen.writeObjectField("required", true);
      }
      if (allowEmptyValue) {
        gen.writeObjectField("allowEmptyValue", true);
      }
      gen.writeObjectField("in", parameterUsedIn);
      gen.writeFieldName("schema");
      if (isList) { // Handle list parameter
        gen.writeStartObject();
        gen.writeObjectField("type", "array");
        gen.writeFieldName("items");
        entry.getValue().serializeOpenApiTypeOrReference(gen);
        gen.writeEndObject();
      } else { // Handle regular parameter
        entry.getValue().serializeOpenApiTypeOrReference(gen);
      }

      gen.writeEndObject();
    }
  }

  private void writeTags(final JsonGenerator gen) throws IOException {
    if (tags.isEmpty()) {
      return;
    }
    gen.writeArrayFieldStart("tags");

    for (String tag : tags) {
      gen.writeString(tag);
    }
    gen.writeEndArray();
  }

  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return Stream.concat(
            responses.values().stream()
                .flatMap(response -> response.getReferencedTypeDefinitions().stream()),
            requestBodyTypes.values().stream()
                .flatMap(bodyType -> bodyType.getSelfAndReferencedTypeDefinitions().stream()))
        .collect(Collectors.toSet());
  }

  public RequestContentTypeDefinition<?> getRequestBodyType() {
    final RequestContentTypeDefinition<?> requestContentTypeDefinition =
        requestBodyTypes.get(ContentTypes.JSON);
    checkArgument(requestContentTypeDefinition != null, "Request body not found");
    return requestContentTypeDefinition;
  }

  public boolean isRequiredRequestBody() {
    return requiredRequestBody;
  }

  public static class EndpointMetaDataBuilder {
    private HandlerType method;
    private String path;
    private String operationId;
    private String summary;
    private String description;
    private boolean deprecated = false;
    private boolean requiredRequestBody = true;
    private final Map<String, StringValueTypeDefinition<?>> pathParams = new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> queryParams = new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> queryParamsAllowEmpty =
        new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> requiredQueryParams =
        new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> queryListParams = new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> requiredHeaders = new HashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> headers = new HashMap<>();
    private Optional<String> security = Optional.empty();
    private String defaultRequestType = ContentTypes.JSON;
    private final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes = new HashMap<>();
    private String defaultResponseType = ContentTypes.JSON;
    private final Map<String, OpenApiResponse> responses = new LinkedHashMap<>();

    private List<String> tags = Collections.emptyList();

    public EndpointMetaDataBuilder method(final HandlerType method) {
      this.method = method;
      return this;
    }

    public EndpointMetaDataBuilder path(final String path) {
      this.path = path;
      return this;
    }

    public EndpointMetaDataBuilder pathParam(final ParameterMetadata<?> parameterMetadata) {
      if (pathParams.containsKey(parameterMetadata.getName())) {
        throw new IllegalStateException(
            "Path Parameters already contains " + parameterMetadata.getName());
      }
      pathParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryParam(final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParamAlreadyAdded(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      queryParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryParamAllowsEmpty(
        final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParamAlreadyAdded(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      queryParamsAllowEmpty.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryParamRequired(
        final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParamAlreadyAdded(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      requiredQueryParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryListParam(final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParamAlreadyAdded(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      queryListParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    private boolean queryParamAlreadyAdded(final String param) {
      return queryParams.containsKey(param)
          || queryParamsAllowEmpty.containsKey(param)
          || requiredQueryParams.containsKey(param)
          || queryListParams.containsKey(param);
    }

    public EndpointMetaDataBuilder headerRequired(final ParameterMetadata<?> headerMetadata) {
      checkRequestHeader(headerMetadata);
      requiredHeaders.put(headerMetadata.getName(), headerMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder header(final ParameterMetadata<?> headerMetadata) {
      checkRequestHeader(headerMetadata);
      headers.put(headerMetadata.getName(), headerMetadata.getType());
      return this;
    }

    private void checkRequestHeader(final ParameterMetadata<?> headerMetadata) {
      final String param = headerMetadata.getName();
      if (requiredHeaders.containsKey(param) || headers.containsKey(param)) {
        throw new IllegalStateException("Header already contains " + headerMetadata.getName());
      } else if ("Content-Type".equalsIgnoreCase(param)
          || "Accept".equalsIgnoreCase(param)
          || "Authorization".equalsIgnoreCase(param)) {
        throw new IllegalArgumentException(
            "Request headers cannot be named Content-Type, Accept, or Authorization");
      }
    }

    public EndpointMetaDataBuilder withBearerAuthSecurity() {
      return security("bearerAuth");
    }

    public EndpointMetaDataBuilder security(final String security) {
      this.security = Optional.ofNullable(security);
      return this;
    }

    public EndpointMetaDataBuilder operationId(final String operationId) {
      this.operationId = operationId;
      return this;
    }

    public EndpointMetaDataBuilder summary(final String summary) {
      this.summary = summary;
      return this;
    }

    public EndpointMetaDataBuilder description(final String description) {
      this.description = description;
      return this;
    }

    public EndpointMetaDataBuilder deprecated(final boolean deprecated) {
      this.deprecated = deprecated;
      return this;
    }

    public EndpointMetaDataBuilder response(final int responseCode, final String description) {
      return response(responseCode, description, emptyList(), emptyList());
    }

    public EndpointMetaDataBuilder defaultResponseType(final String defaultContentType) {
      this.defaultResponseType = defaultContentType;
      return this;
    }

    public EndpointMetaDataBuilder defaultRequestType(final String defaultRequestType) {
      this.defaultRequestType = defaultRequestType;
      return this;
    }

    public EndpointMetaDataBuilder optionalRequestBody() {
      this.requiredRequestBody = false;
      return this;
    }

    public EndpointMetaDataBuilder requestBodyType(
        final DeserializableTypeDefinition<?> requestBodyType) {
      this.requestBodyTypes.put(
          ContentTypes.JSON, new SimpleJsonRequestContentTypeDefinition<>(requestBodyType));
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableTypeDefinition<List<T>> requestBodyType,
        final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<T> bodyTypeSelector) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfArrayJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final DeserializableTypeDefinition<T> requestBodyType,
        final IOFunction<Bytes, T> octetStreamParser) {
      this.requestBodyTypes.put(
          ContentTypes.JSON, new SimpleJsonRequestContentTypeDefinition<>(requestBodyType));
      this.requestBodyTypes.put(
          ContentTypes.OCTET_STREAM,
          OctetStreamRequestContentTypeDefinition.parseBytes(octetStreamParser));
      return withUnsupportedMediaTypeResponse();
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector,
        final IOFunction<Bytes, T> octetStreamParser) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      this.requestBodyTypes.put(
          ContentTypes.OCTET_STREAM,
          OctetStreamRequestContentTypeDefinition.parseBytes(octetStreamParser));
      return withUnsupportedMediaTypeResponse();
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector,
        final RequestContentTypeDefinition<T> octetStreamRequestContentTypeDefinition) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      this.requestBodyTypes.put(ContentTypes.OCTET_STREAM, octetStreamRequestContentTypeDefinition);
      return withUnsupportedMediaTypeResponse();
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector,
        final BiFunction<Bytes, Optional<String>, T> milestoneSpecificOctetStreamParser) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      this.requestBodyTypes.put(
          ContentTypes.OCTET_STREAM,
          MilestoneSpecificOctetStreamRequestContentTypeDefinition.parseBytes(
              milestoneSpecificOctetStreamParser));
      return withUnsupportedMediaTypeResponse();
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableTypeDefinition<List<T>> requestBodyType,
        final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<T> bodyTypeSelector,
        final BiFunction<Bytes, Optional<String>, List<T>> milestoneSpecificOctetStreamParser) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfArrayJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      this.requestBodyTypes.put(
          ContentTypes.OCTET_STREAM,
          MilestoneSpecificOctetStreamRequestContentTypeDefinition.parseBytes(
              milestoneSpecificOctetStreamParser));
      return withUnsupportedMediaTypeResponse();
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content) {
      return response(
          responseCode,
          description,
          List.of(new JsonResponseContentTypeDefinition<>(content)),
          emptyList());
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content,
        final SerializableTypeDefinition<?> header) {
      return response(
          responseCode,
          description,
          List.of(new JsonResponseContentTypeDefinition<>(content)),
          List.of(new JsonResponseContentTypeDefinition<>(header)));
    }

    public <T> EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<? extends T> content,
        final ResponseContentTypeDefinition<? extends T> octetStreamTypeDefinition) {
      return response(
          responseCode,
          description,
          List.of(new JsonResponseContentTypeDefinition<>(content), octetStreamTypeDefinition),
          emptyList());
    }

    public <T> EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<? extends T> content,
        final ResponseContentTypeDefinition<? extends T> octetStreamTypeDefinition,
        final SerializableTypeDefinition<? extends T> header) {
      return response(
          responseCode,
          description,
          List.of(new JsonResponseContentTypeDefinition<>(content), octetStreamTypeDefinition),
          List.of(new JsonResponseContentTypeDefinition<>(header)));
    }

    public <T> EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<? extends T> content,
        final ResponseContentTypeDefinition<? extends T> octetStreamTypeDefinition,
        final List<SerializableTypeDefinition<? extends T>> headers) {
      return response(
          responseCode,
          description,
          List.of(new JsonResponseContentTypeDefinition<>(content), octetStreamTypeDefinition),
          headers.stream()
              .map(JsonResponseContentTypeDefinition::new)
              .collect(Collectors.toList()));
    }

    public <T> EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final ResponseContentTypeDefinition<T> octetStreamTypeDefinition) {
      return response(responseCode, description, List.of(octetStreamTypeDefinition), emptyList());
    }

    public EndpointMetaDataBuilder withUnauthorizedResponse() {
      return response(SC_UNAUTHORIZED, "Unauthorized, no token is found", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withNotFoundResponse() {
      return response(SC_NOT_FOUND, "Not found", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withNotAcceptableResponse() {
      return response(SC_NOT_ACCEPTABLE, "Not acceptable", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withServiceUnavailableResponse() {
      response(
          SC_SERVICE_UNAVAILABLE,
          "Beacon node is currently syncing and not serving requests.",
          HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder withChainDataResponses() {
      return response(SC_SERVICE_UNAVAILABLE, "Service unavailable", HTTP_ERROR_RESPONSE_TYPE)
          .response(
              SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis");
    }

    public EndpointMetaDataBuilder withForbiddenResponse() {
      return response(
          SC_FORBIDDEN, "Forbidden, a token is found but is invalid", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withAuthenticationResponses() {
      return withUnauthorizedResponse().withForbiddenResponse();
    }

    public EndpointMetaDataBuilder withInternalErrorResponse() {
      response(SC_INTERNAL_SERVER_ERROR, "Internal server error", HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder withUnsupportedMediaTypeResponse() {
      response(SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder withNotImplementedResponse() {
      response(SC_NOT_IMPLEMENTED, "Not implemented", HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder withBadRequestResponse(final Optional<String> maybeMessage) {
      response(
          SC_BAD_REQUEST,
          maybeMessage.orElse(
              "The request could not be processed, check the response for more information."),
          HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final List<? extends ResponseContentTypeDefinition<?>> content) {
      return response(responseCode, description, content, emptyList());
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final List<? extends ResponseContentTypeDefinition<?>> content,
        final List<? extends ResponseContentTypeDefinition<?>> header) {
      this.responses.put(
          Integer.toString(responseCode), new OpenApiResponse(description, header, content));
      return this;
    }

    public EndpointMetadata build() {
      checkNotNull(method, "method must be specified");
      checkNotNull(path, "path must be specified");
      checkNotNull(operationId, "operationId must be specified");
      checkNotNull(summary, "summary must be specified");
      checkNotNull(description, "description must be specified");
      checkState(!responses.isEmpty(), "Must specify at least one response");
      checkState(
          requestBodyTypes.isEmpty() || requestBodyTypes.containsKey(defaultRequestType),
          "Default request body type is not a supported request type");

      if (!responses.containsKey(Integer.toString(SC_BAD_REQUEST))) {
        withBadRequestResponse(Optional.empty());
      }
      if (!responses.containsKey(Integer.toString(SC_INTERNAL_SERVER_ERROR))) {
        // add internal error response if a custom response hasn't been defined
        withInternalErrorResponse();
      }
      return new EndpointMetadata(
          method,
          path,
          operationId,
          summary,
          security,
          description,
          deprecated,
          defaultResponseType,
          responses,
          defaultRequestType,
          requiredRequestBody,
          requestBodyTypes,
          tags,
          pathParams,
          queryParams,
          queryParamsAllowEmpty,
          requiredQueryParams,
          queryListParams,
          requiredHeaders,
          headers);
    }

    public EndpointMetaDataBuilder tags(final String... tags) {
      this.tags = List.of(tags);
      return this;
    }
  }
}

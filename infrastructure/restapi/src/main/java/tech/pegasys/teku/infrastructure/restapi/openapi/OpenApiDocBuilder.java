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

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PREFERRED_DISPLAY_TAGS_ORDER;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;

public class OpenApiDocBuilder {

  public static final String OPENAPI_VERSION = "3.0.1";

  // Info
  private String title;
  private String version;
  private String description;
  private String licenseName;
  private String licenseUrl;
  private boolean bearerAuth;

  private final Map<String, Map<HandlerType, RestApiEndpoint>> endpoints = new LinkedHashMap<>();

  public OpenApiDocBuilder title(final String title) {
    this.title = title;
    return this;
  }

  public OpenApiDocBuilder version(final String version) {
    this.version = version;
    return this;
  }

  public OpenApiDocBuilder description(final String description) {
    this.description = description;
    return this;
  }

  public OpenApiDocBuilder license(final String licenseName, final String licenseUrl) {
    this.licenseName = licenseName;
    this.licenseUrl = licenseUrl;
    return this;
  }

  public OpenApiDocBuilder bearerAuth(final boolean bearerAuth) {
    this.bearerAuth = bearerAuth;
    return this;
  }

  public OpenApiDocBuilder endpoint(final RestApiEndpoint endpoint) {
    this.endpoints
        .computeIfAbsent(endpoint.getMetadata().getPath(), __ -> new LinkedHashMap<>())
        .put(endpoint.getMetadata().getMethod(), endpoint);
    return this;
  }

  public String build() {
    checkNotNull(title, "title must be supplied");
    checkNotNull(version, "version must be supplied");
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = new ObjectMapper().createGenerator(writer)) {

      gen.writeStartObject();
      gen.writeStringField("openapi", OPENAPI_VERSION);
      writeInfo(gen);
      writePaths(gen);

      writeComponents(gen);
      gen.writeEndObject();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  private void writeInfo(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("info");
    gen.writeStringField("title", title);
    if (description != null) {
      gen.writeStringField("description", description);
    }
    if (licenseName != null) {
      gen.writeObjectFieldStart("license");
      gen.writeStringField("name", licenseName);
      gen.writeStringField("url", licenseUrl);
      gen.writeEndObject();
    }
    gen.writeStringField("version", version);
    gen.writeEndObject();
  }

  private void writePaths(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("paths");
    final List<Entry<String, Map<HandlerType, RestApiEndpoint>>> sortedEndpoints =
        sortRestApiEndpoints(endpoints);
    for (final Entry<String, Map<HandlerType, RestApiEndpoint>> pathEntry : sortedEndpoints) {
      gen.writeObjectFieldStart(pathEntry.getKey());
      for (RestApiEndpoint endpoint : pathEntry.getValue().values()) {
        final EndpointMetadata metadata = endpoint.getMetadata();
        metadata.writeOpenApi(gen);
      }
      gen.writeEndObject();
    }
    gen.writeEndObject();
  }

  @SuppressWarnings("unchecked")
  private List<Entry<String, Map<HandlerType, RestApiEndpoint>>> sortRestApiEndpoints(
      final Map<String, Map<HandlerType, RestApiEndpoint>> endpoints) {
    final List<Entry<String, Map<HandlerType, RestApiEndpoint>>> entries =
        new ArrayList<>(endpoints.entrySet());

    // fill desired order for tags
    final Map<String, Integer> tagOrder = new HashMap<>();
    IntStream.range(0, PREFERRED_DISPLAY_TAGS_ORDER.size())
        .forEach(i -> tagOrder.put(PREFERRED_DISPLAY_TAGS_ORDER.get(i), i));

    // sort by tag, then by path, tag order is not guaranteed, endpoint could have several tags,
    // first appearance of any tag defines its order on UI
    final int endOrder = 999;
    entries.sort(
        Comparator.comparingInt(
                entry ->
                    ((Entry<String, Map<HandlerType, RestApiEndpoint>>) entry)
                        .getValue().values().stream()
                            .findFirst()
                            .map(
                                restApiEndpoint ->
                                    restApiEndpoint.getMetadata().getTags().stream()
                                        .mapToInt(tag -> tagOrder.getOrDefault(tag, endOrder))
                                        .min()
                                        .orElse(endOrder))
                            .orElse(endOrder))
            .thenComparing(
                entry -> ((Entry<String, Map<HandlerType, RestApiEndpoint>>) entry).getKey()));

    return entries;
  }

  private void writeComponents(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("components");
    writeSecuritySchemas(gen);
    writeSchemas(gen);
    gen.writeEndObject();
  }

  private void writeSecuritySchemas(final JsonGenerator gen) throws IOException {
    if (!bearerAuth) {
      return;
    }
    gen.writeObjectFieldStart("securitySchemes");
    gen.writeObjectFieldStart("bearerAuth");
    gen.writeStringField("type", "http");
    gen.writeStringField("scheme", "bearer");
    gen.writeEndObject();
    gen.writeEndObject();
  }

  private void writeSchemas(final JsonGenerator gen) throws IOException {
    final List<OpenApiTypeDefinition> typeDefinitions =
        endpoints.values().stream()
            .flatMap(pathEndpoints -> pathEndpoints.values().stream())
            .flatMap(endpoint -> endpoint.getMetadata().getReferencedTypeDefinitions().stream())
            .filter(type -> type.getTypeName().isPresent())
            .distinct()
            .collect(Collectors.toList());
    // sort by name
    typeDefinitions.sort(
        Comparator.comparing(typeDefinition -> typeDefinition.getTypeName().orElseThrow()));
    gen.writeObjectFieldStart("schemas");
    for (OpenApiTypeDefinition type : typeDefinitions) {
      gen.writeFieldName(type.getTypeName().orElseThrow());
      type.serializeOpenApiType(gen);
    }
    gen.writeEndObject();
  }
}

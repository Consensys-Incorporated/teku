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

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class OpenApiResponse {
  private final String description;
  private final List<? extends ResponseContentTypeDefinition<?>> header;
  private final List<? extends ResponseContentTypeDefinition<?>> content;

  public OpenApiResponse(
      final String description,
      final List<? extends ResponseContentTypeDefinition<?>> header,
      final List<? extends ResponseContentTypeDefinition<?>> content) {
    this.description = description;
    this.header = header;
    this.content = content;
  }

  public ResponseContentTypeDefinition<?> getType(final String contentType) {
    return content.stream()
        .filter(type -> type.getContentType().equals(contentType))
        .findFirst()
        .orElse(null);
  }

  public void writeOpenApi(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("description", description);
    if (!header.isEmpty()) {
      gen.writeObjectFieldStart("headers");
      for (var headerEntry : header) {
        headerEntry.serializeOpenApiTypeOrReference(gen);
      }
      gen.writeEndObject();
    }

    gen.writeObjectFieldStart("content");
    for (ResponseContentTypeDefinition<?> contentEntry : content) {
      gen.writeObjectFieldStart(contentEntry.getContentType());
      gen.writeFieldName("schema");
      contentEntry.serializeOpenApiTypeOrReference(gen);
      gen.writeEndObject();
    }

    gen.writeEndObject();
    gen.writeEndObject();
  }

  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return content.stream()
        .flatMap(type -> type.getSelfAndReferencedTypeDefinitions().stream())
        .collect(toSet());
  }

  public List<String> getSupportedContentTypes() {
    return content.stream().map(ResponseContentTypeDefinition::getContentType).toList();
  }
}

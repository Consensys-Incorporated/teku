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

package tech.pegasys.teku.infrastructure.json.types;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

public interface OpenApiTypeDefinition {
  default Optional<String> getTypeName() {
    return Optional.empty();
  }

  OpenApiTypeDefinition withDescription(final String description);

  void serializeOpenApiType(JsonGenerator gen) throws IOException;

  default Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return Collections.emptySet();
  }

  default Collection<OpenApiTypeDefinition> getSelfAndReferencedTypeDefinitions() {
    return Stream.concat(Stream.of(this), getReferencedTypeDefinitions().stream()).collect(toSet());
  }

  @SuppressWarnings("ReferenceEquality")
  default boolean isEquivalentToDeserializableType(final DeserializableTypeDefinition<?> type) {
    return this == type;
  }

  default void serializeOpenApiTypeOrReference(final JsonGenerator gen) throws IOException {
    if (getTypeName().isPresent()) {
      gen.writeStartObject();
      gen.writeStringField("$ref", "#/components/schemas/" + getTypeName().get());
      gen.writeEndObject();
    } else {
      serializeOpenApiType(gen);
    }
  }
}

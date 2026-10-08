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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class SerializableOneOfTypeDefinition<TObject>
    implements SerializableTypeDefinition<TObject> {
  private final Map<Predicate<TObject>, ? extends SerializableTypeDefinition<? extends TObject>>
      types;
  private final Optional<String> name;
  private final Optional<String> title;
  private final Optional<String> description;

  SerializableOneOfTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description,
      final Map<Predicate<TObject>, ? extends SerializableTypeDefinition<? extends TObject>>
          types) {
    this.name = name;
    this.title = title;
    this.description = description;
    this.types = types;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  public Optional<String> getTitle() {
    return title;
  }

  @Override
  @SuppressWarnings({"ReferenceComparison", "ReferenceEquality"})
  public boolean isEquivalentToDeserializableType(final DeserializableTypeDefinition<?> type) {
    for (SerializableTypeDefinition<?> current : types.values()) {
      if (current == type) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (title.isPresent()) {
      gen.writeStringField("title", title.get());
    }
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    gen.writeStringField("type", "object");
    gen.writeArrayFieldStart("oneOf");
    for (SerializableTypeDefinition<? extends TObject> t :
        types.values().stream().distinct().toList()) {
      t.serializeOpenApiTypeOrReference(gen);
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public void serialize(final TObject value, final JsonGenerator gen) throws IOException {
    SerializableTypeDefinition typeDefinition = null;
    // entryset
    for (Predicate<TObject> predicate : types.keySet()) {
      if (predicate.test(value)) {
        typeDefinition = types.get(predicate);
        break;
      }
    }

    checkArgument(
        typeDefinition != null, "No class serialization method found: %s", value.getClass());
    typeDefinition.serialize(value, gen);
  }

  @Override
  public SerializableTypeDefinition<TObject> withDescription(final String description) {
    return new SerializableOneOfTypeDefinition<>(
        Optional.empty(), // Clear name to ensure customised type is inlined
        title,
        Optional.of(description),
        this.types);
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return types.values().stream()
        .flatMap(type -> type.getSelfAndReferencedTypeDefinitions().stream())
        .collect(toSet());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SerializableOneOfTypeDefinition<?> that = (SerializableOneOfTypeDefinition<?>) o;
    return Objects.equals(types, that.types)
        && Objects.equals(name, that.name)
        && Objects.equals(title, that.title)
        && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(types, name, title, description);
  }
}

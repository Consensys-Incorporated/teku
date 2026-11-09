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

package tech.pegasys.teku.infrastructure.json.types;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class SerializableObjectTypeDefinition<TObject> implements SerializableTypeDefinition<TObject> {

  private final Optional<String> name;
  private final Optional<String> title;
  private final Optional<String> description;
  private final Map<String, ? extends SerializableFieldDefinition<TObject>> fields;

  SerializableObjectTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description,
      final Map<String, ? extends SerializableFieldDefinition<TObject>> fields) {
    this.name = name;
    this.title = title;
    this.description = description;
    this.fields = fields;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  public Optional<String> getTitle() {
    return title;
  }

  @Override
  public void serialize(final TObject value, final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    for (SerializableFieldDefinition<TObject> field : fields.values()) {
      field.writeField(value, gen);
    }
    gen.writeEndObject();
  }

  @Override
  public SerializableTypeDefinition<TObject> withDescription(final String description) {
    return new SerializableObjectTypeDefinition<>(
        Optional.empty(), // Clear name to ensure customised type is inlined
        title,
        Optional.of(description),
        fields);
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
    writeRequiredFields(gen);
    gen.writeObjectFieldStart("properties");
    for (SerializableFieldDefinition<TObject> field : fields.values()) {
      field.writeOpenApiField(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  private void writeRequiredFields(final JsonGenerator gen) throws IOException {
    final String[] requiredFieldNames =
        fields.values().stream()
            .filter(SerializableFieldDefinition::isRequired)
            .map(SerializableFieldDefinition::getName)
            .toArray(String[]::new);
    gen.writeFieldName("required");
    gen.writeArray(requiredFieldNames, 0, requiredFieldNames.length);
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return fields.values().stream()
        .flatMap(field -> field.getReferencedTypeDefinitions().stream())
        .collect(toSet());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", name).add("fields", fields).toString();
  }

  public Optional<String> getName() {
    return name;
  }

  public Optional<String> getDescription() {
    return description;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SerializableObjectTypeDefinition<?> that = (SerializableObjectTypeDefinition<?>) o;
    return Objects.equals(name, that.name)
        && Objects.equals(title, that.title)
        && Objects.equals(description, that.description)
        && Objects.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, title, description, fields);
  }
}

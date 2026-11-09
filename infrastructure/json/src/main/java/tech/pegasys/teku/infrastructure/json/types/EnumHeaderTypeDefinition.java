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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class EnumHeaderTypeDefinition<T extends Enum<T>> implements StringValueTypeDefinition<T> {
  final Class<T> itemType;
  private final Function<T, String> serializer;
  private final Optional<String> name;
  private final String title;
  private final Optional<Boolean> required;
  private final Optional<String> description;
  private final Optional<String> example;
  private final Set<T> excludedEnumerations;

  public EnumHeaderTypeDefinition(
      final Class<T> itemType,
      final Function<T, String> serializer,
      final Optional<String> name,
      final String title,
      final Optional<Boolean> required,
      final Optional<String> description,
      final Optional<String> example,
      final Set<T> excludedEnumerations) {
    this.itemType = itemType;
    this.serializer = serializer;
    this.name = name;
    this.title = title;
    this.required = required;
    this.description = description;
    this.example = example;
    this.excludedEnumerations = excludedEnumerations;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(serializeToString(value));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeFieldName(title);
    gen.writeStartObject();

    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (required.isPresent()) {
      gen.writeBooleanField("required", required.get());
    }
    gen.writeFieldName("schema");
    gen.writeStartObject();
    gen.writeStringField("type", "string");

    gen.writeArrayFieldStart("enum");

    for (T value : itemType.getEnumConstants()) {
      if (excludedEnumerations.contains(value)) {
        continue;
      }
      gen.writeString(serializeToString(value));
    }
    gen.writeEndArray();

    if (example.isPresent()) {
      gen.writeStringField("example", example.get());
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  @Override
  public String serializeToString(final T value) {
    return value != null ? serializer.apply(value) : null;
  }

  @Override
  public T deserializeFromString(final String value) {
    for (T t : itemType.getEnumConstants()) {
      if (t.toString().equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new IllegalArgumentException("Unknown enum value: " + value);
  }

  @Override
  public StringValueTypeDefinition<T> withDescription(final String description) {
    return new EnumHeaderTypeDefinition<>(
        itemType,
        serializer,
        Optional.empty(),
        title,
        required,
        Optional.of(description),
        example,
        excludedEnumerations);
  }

  @Override
  public String toString() {
    return "EnumTypeHeaderDefinition{"
        + "itemType="
        + itemType
        + ", serializer="
        + serializer
        + ", name="
        + name
        + ", title='"
        + title
        + '\''
        + ", required="
        + required
        + ", description="
        + description
        + ", example="
        + example
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnumHeaderTypeDefinition<?> that = (EnumHeaderTypeDefinition<?>) o;
    return Objects.equals(itemType, that.itemType)
        && Objects.equals(serializer, that.serializer)
        && Objects.equals(name, that.name)
        && Objects.equals(title, that.title)
        && Objects.equals(required, that.required)
        && Objects.equals(description, that.description)
        && Objects.equals(example, that.example);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType, serializer, name, title, required, description, example);
  }

  public static class EnumTypeHeaderDefinitionBuilder<T extends Enum<T>> {
    private final Class<T> itemType;
    private final Function<T, String> serializer;
    private Optional<String> name = Optional.empty();
    private String title;
    private Optional<Boolean> required = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<String> example = Optional.empty();
    private final Set<T> excludedEnumerations = new HashSet<>();

    public EnumTypeHeaderDefinitionBuilder(
        final Class<T> itemType, final Function<T, String> serializer) {
      this.itemType = itemType;
      this.serializer = serializer;
    }

    public EnumTypeHeaderDefinitionBuilder<T> name(final String name) {
      this.name = Optional.of(name);
      return this;
    }

    public EnumTypeHeaderDefinitionBuilder<T> title(final String title) {
      this.title = title;
      return this;
    }

    public EnumTypeHeaderDefinitionBuilder<T> required(final Boolean required) {
      this.required = Optional.of(required);
      return this;
    }

    public EnumTypeHeaderDefinitionBuilder<T> description(final String description) {
      this.description = Optional.of(description);
      return this;
    }

    public EnumTypeHeaderDefinitionBuilder<T> example(final String example) {
      this.example = Optional.of(example);
      return this;
    }

    public EnumTypeHeaderDefinitionBuilder<T> excludedEnumerations(
        final Set<T> excludedEnumerations) {
      this.excludedEnumerations.addAll(excludedEnumerations);
      return this;
    }

    public EnumHeaderTypeDefinition<T> build() {
      return new EnumHeaderTypeDefinition<>(
          itemType, serializer, name, title, required, description, example, excludedEnumerations);
    }
  }
}

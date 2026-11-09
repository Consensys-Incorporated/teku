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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class EnumTypeDefinition<T extends Enum<T>> extends PrimitiveTypeDefinition<T> {
  final Class<T> itemType;
  private final Function<T, String> serializer;

  private final Set<T> excludedEnumerations = new HashSet<>();
  private Optional<String> example = Optional.empty();
  private Optional<String> description = Optional.empty();
  private Optional<String> format = Optional.empty();

  public EnumTypeDefinition(final Class<T> itemType) {
    this(itemType, Objects::toString);
  }

  public EnumTypeDefinition(final Class<T> itemType, final Function<T, String> serializer) {
    this.itemType = itemType;
    this.serializer = serializer;
  }

  public EnumTypeDefinition(
      final Class<T> itemType,
      final Function<T, String> serializer,
      final Set<T> excludedEnumerations) {
    this.itemType = itemType;
    this.serializer = serializer;
    this.excludedEnumerations.addAll(excludedEnumerations);
  }

  public EnumTypeDefinition(
      final Class<T> itemType,
      final Function<T, String> serializer,
      final Optional<String> example,
      final Optional<String> description,
      final Optional<String> format,
      final Optional<Set<T>> excludedEnumerations) {
    this.itemType = itemType;
    this.serializer = serializer;
    this.example = example;
    this.description = description;
    this.format = format;
    excludedEnumerations.ifPresent(this.excludedEnumerations::addAll);
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "string");

    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (example.isPresent()) {
      gen.writeStringField("example", example.get());
    }
    if (format.isPresent()) {
      gen.writeStringField("format", format.get());
    }

    gen.writeArrayFieldStart("enum");

    for (T value : itemType.getEnumConstants()) {
      if (excludedEnumerations.contains(value)) {
        continue;
      }
      gen.writeString(serializeToString(value));
    }
    gen.writeEndArray();
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    if (excludedEnumerations.contains(value)) {
      throw new IllegalArgumentException("Unknown enum value: " + value);
    }
    gen.writeString(serializeToString(value));
  }

  @Override
  public String serializeToString(final T value) {
    return value != null ? serializer.apply(value) : null;
  }

  @Override
  public T deserializeFromString(final String value) {
    for (T t : itemType.getEnumConstants()) {
      if (excludedEnumerations.contains(t)) {
        continue;
      }
      if (t.toString().equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new IllegalArgumentException("Unknown enum value: " + value);
  }

  public static class EnumTypeBuilder<T extends Enum<T>> {

    private Class<T> itemType;
    private Function<T, String> serializer;
    private Optional<String> example = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<String> format = Optional.empty();
    private Optional<Set<T>> excludedEnumerations = Optional.empty();

    public EnumTypeBuilder(final Class<T> itemType, final Function<T, String> serializer) {
      this.itemType = itemType;
      this.serializer = serializer;
    }

    public EnumTypeBuilder(final Class<T> itemType) {
      this(itemType, false);
    }

    public EnumTypeBuilder(final Class<T> itemType, final boolean forceLowercase) {
      this(
          itemType,
          forceLowercase ? (val) -> val.toString().toLowerCase(Locale.ROOT) : Enum::toString);
    }

    public EnumTypeBuilder<T> parser(final Class<T> itemType) {
      this.itemType = itemType;
      return this;
    }

    public EnumTypeBuilder<T> parser(final Function<T, String> serializer) {
      this.serializer = serializer;
      return this;
    }

    public EnumTypeBuilder<T> example(final String example) {
      this.example = Optional.of(example);
      return this;
    }

    public EnumTypeBuilder<T> description(final String description) {
      this.description = Optional.of(description);
      return this;
    }

    public EnumTypeBuilder<T> format(final String format) {
      this.format = Optional.of(format);
      return this;
    }

    public EnumTypeBuilder<T> excludedEnumerations(final Set<T> excludedEnumerations) {
      this.excludedEnumerations = Optional.of(excludedEnumerations);
      return this;
    }

    public EnumTypeDefinition<T> build() {
      return new EnumTypeDefinition<>(
          itemType, serializer, example, description, format, excludedEnumerations);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnumTypeDefinition<?> that = (EnumTypeDefinition<?>) o;
    return Objects.equals(itemType, that.itemType)
        && Objects.equals(example, that.example)
        && Objects.equals(description, that.description)
        && Objects.equals(format, that.format)
        && Objects.equals(excludedEnumerations, that.excludedEnumerations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType, example, description, format, excludedEnumerations);
  }
}

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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class StringBasedPrimitiveTypeDefinition<T> implements StringValueTypeDefinition<T> {

  private final Optional<String> name;
  private final Optional<String> title;
  private final Function<String, T> objectFromString;
  private final Function<T, String> stringFromObject;
  private final Optional<String> description;
  private final Optional<String> example;
  private final Optional<String> format;
  private final Optional<String> pattern;
  private final Optional<Integer> minLength;
  private final Optional<Integer> maxLength;

  private StringBasedPrimitiveTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Function<String, T> objectFromString,
      final Function<T, String> stringFromObject,
      final Optional<String> example,
      final Optional<String> description,
      final Optional<String> format,
      final Optional<String> pattern,
      final Optional<Integer> minLength,
      final Optional<Integer> maxLength) {
    this.name = name;
    this.title = title;
    this.objectFromString = objectFromString;
    this.stringFromObject = stringFromObject;
    this.example = example;
    this.description = description;
    this.format = format;
    this.pattern = pattern;
    this.minLength = minLength;
    this.maxLength = maxLength;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    if (!parser.getCurrentToken().isScalarValue()) {
      throw MismatchedInputException.from(
          parser, String.class, "Expected scalar value but got " + parser.getCurrentToken());
    }
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public StringValueTypeDefinition<T> withDescription(final String description) {
    return new StringBasedPrimitiveTypeDefinition<>(
        Optional.empty(), // Clear name to ensure custom variant is inlined.
        title,
        objectFromString,
        stringFromObject,
        example,
        Optional.of(description),
        format,
        pattern,
        minLength,
        maxLength);
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(stringFromObject.apply(value));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "string");
    if (title.isPresent()) {
      gen.writeStringField("title", title.get());
    }
    if (pattern.isPresent()) {
      gen.writeStringField("pattern", pattern.get());
    }
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (example.isPresent()) {
      gen.writeStringField("example", example.get());
    }
    if (format.isPresent()) {
      gen.writeStringField("format", format.get());
    }
    if (minLength.isPresent()) {
      gen.writeNumberField("minLength", minLength.get());
    }
    if (maxLength.isPresent()) {
      gen.writeNumberField("maxLength", maxLength.get());
    }
    gen.writeEndObject();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("title", title)
        .add("description", description)
        .add("example", example)
        .add("format", format)
        .add("pattern", pattern)
        .add("minLength", minLength)
        .add("maxLength", maxLength)
        .toString();
  }

  @Override
  public String serializeToString(final T value) {
    return stringFromObject.apply(value);
  }

  @Override
  public T deserializeFromString(final String value) {
    try {
      return objectFromString.apply(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }
  }

  public static class StringTypeBuilder<T> {

    private Optional<String> name = Optional.empty();
    private Optional<String> title = Optional.empty();
    private Function<String, T> parser;
    private Function<T, String> formatter;
    private Optional<String> example = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<String> format = Optional.empty();
    private Optional<String> pattern = Optional.empty();
    private Optional<Integer> minLength = Optional.empty();
    private Optional<Integer> maxLength = Optional.empty();

    public StringTypeBuilder<T> name(final String name) {
      this.name = Optional.of(name);
      return this;
    }

    public StringTypeBuilder<T> title(final String title) {
      this.title = Optional.of(title);
      return this;
    }

    public StringTypeBuilder<T> parser(final Function<String, T> parser) {
      this.parser = parser;
      return this;
    }

    public StringTypeBuilder<T> formatter(final Function<T, String> formatter) {
      this.formatter = formatter;
      return this;
    }

    public StringTypeBuilder<T> example(final String example) {
      this.example = Optional.of(example);
      return this;
    }

    public StringTypeBuilder<T> description(final String description) {
      this.description = Optional.of(description);
      return this;
    }

    public StringTypeBuilder<T> format(final String format) {
      this.format = Optional.of(format);
      return this;
    }

    public StringTypeBuilder<T> pattern(final String pattern) {
      this.pattern = Optional.of(pattern);
      return this;
    }

    public StringTypeBuilder<T> minLength(final int minLength) {
      this.minLength = Optional.of(minLength);
      return this;
    }

    public StringTypeBuilder<T> maxLength(final int maxLength) {
      this.maxLength = Optional.of(maxLength);
      return this;
    }

    public StringValueTypeDefinition<T> build() {
      checkNotNull(parser, "Must specify parser");
      checkNotNull(formatter, "Must specify formatter");

      return new StringBasedPrimitiveTypeDefinition<>(
          name,
          title.or(() -> name),
          parser,
          formatter,
          example,
          description,
          format,
          pattern,
          minLength,
          maxLength);
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
    final StringBasedPrimitiveTypeDefinition<?> that = (StringBasedPrimitiveTypeDefinition<?>) o;
    return Objects.equals(name, that.name)
        && Objects.equals(title, that.title)
        && Objects.equals(description, that.description)
        && Objects.equals(example, that.example)
        && Objects.equals(format, that.format)
        && Objects.equals(pattern, that.pattern)
        && Objects.equals(minLength, that.minLength)
        && Objects.equals(maxLength, that.maxLength);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, title, description, example, format, pattern, minLength, maxLength);
  }
}

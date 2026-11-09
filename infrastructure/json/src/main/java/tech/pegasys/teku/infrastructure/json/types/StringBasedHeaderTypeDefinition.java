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
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class StringBasedHeaderTypeDefinition<T> implements StringValueTypeDefinition<T> {
  private final Optional<String> name;
  private final Optional<String> title;
  private final Optional<String> example;
  private final Optional<String> description;
  private final Optional<Boolean> required;
  final Function<String, T> objectFromString;
  final Function<T, String> stringFromObject;

  public StringBasedHeaderTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> example,
      final Optional<String> description,
      final Optional<Boolean> required,
      final Function<String, T> objectFromString,
      final Function<T, String> stringFromObject) {
    this.name = name;
    this.title = title;
    this.example = example;
    this.description = description;
    this.required = required;
    this.objectFromString = objectFromString;
    this.stringFromObject = stringFromObject;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    if (title.isPresent()) {
      gen.writeFieldName(title.get());
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

      if (example.isPresent()) {
        gen.writeStringField("example", example.get());
      }
      gen.writeEndObject();
      gen.writeEndObject();
    }
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

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    if (!parser.getCurrentToken().isScalarValue()) {
      throw MismatchedInputException.from(
          parser, String.class, "Expected scalar value but got " + parser.getCurrentToken());
    }
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(stringFromObject.apply(value));
  }

  @Override
  public StringValueTypeDefinition<T> withDescription(final String description) {
    return new StringBasedHeaderTypeDefinition<>(
        Optional.empty(),
        title,
        example,
        Optional.of(description),
        required,
        objectFromString,
        stringFromObject);
  }

  public static class Builder<T> {
    private Optional<String> name = Optional.empty();
    private Optional<String> title = Optional.empty();
    private Optional<String> example = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<Boolean> required = Optional.empty();
    private Function<String, T> parser;
    private Function<T, String> formatter;

    public Builder<T> name(final String name) {
      this.name = Optional.of(name);
      return this;
    }

    public Builder<T> title(final String title) {
      this.title = Optional.of(title);
      return this;
    }

    public Builder<T> example(final String example) {
      this.example = Optional.of(example);
      return this;
    }

    public Builder<T> description(final String description) {
      this.description = Optional.of(description);
      return this;
    }

    public Builder<T> required(final Boolean required) {
      this.required = Optional.of(required);
      return this;
    }

    public Builder<T> parser(final Function<String, T> parser) {
      this.parser = parser;
      return this;
    }

    public Builder<T> formatter(final Function<T, String> formatter) {
      this.formatter = formatter;
      return this;
    }

    public StringBasedHeaderTypeDefinition<T> build() {
      checkNotNull(parser, "Must specify parser");
      checkNotNull(formatter, "Must specify formatter");

      return new StringBasedHeaderTypeDefinition<>(
          name, title, example, description, required, parser, formatter);
    }
  }
}

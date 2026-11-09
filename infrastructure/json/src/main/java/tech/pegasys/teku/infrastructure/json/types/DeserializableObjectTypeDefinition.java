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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;

class DeserializableObjectTypeDefinition<TObject, TBuilder>
    extends SerializableObjectTypeDefinition<TObject>
    implements DeserializableTypeDefinition<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<String, DeserializableFieldDefinition<TObject, TBuilder>> deserializableFields;
  private final Supplier<TBuilder> initializer;
  private final Function<TBuilder, TObject> finisher;

  DeserializableObjectTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description,
      final Supplier<TBuilder> initializer,
      final Function<TBuilder, TObject> finisher,
      final Map<String, DeserializableFieldDefinition<TObject, TBuilder>> fields) {
    super(name, title, description, fields);
    this.initializer = initializer;
    this.finisher = finisher;
    this.deserializableFields = fields;
  }

  @Override
  public TObject deserialize(final JsonParser p) throws IOException {
    final TBuilder builder = initializer.get();
    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    final Set<String> presentFields = new HashSet<>();
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      String fieldName = p.currentName();
      p.nextToken();
      final DeserializableFieldDefinition<TObject, TBuilder> fieldDefinition =
          deserializableFields.get(fieldName);
      if (fieldDefinition != null) {
        fieldDefinition.readField(builder, p);
        presentFields.add(fieldName);
      } else {
        LOG.debug("Unknown field: {}", fieldName);
        p.skipChildren();
      }
    }

    final Stream<String> missingFields =
        deserializableFields.keySet().stream().filter(key -> !presentFields.contains(key));

    final List<String> missingRequiredFields = new ArrayList<>();

    missingFields.forEach(
        field -> {
          final DeserializableFieldDefinition<TObject, TBuilder> fieldDefinition =
              deserializableFields.get(field);
          if (fieldDefinition.isRequired()) {
            missingRequiredFields.add(field);
          } else {
            // if missing field is optional, set it to empty
            fieldDefinition.toOptional().ifPresent(fd -> fd.setFieldToEmpty(builder));
          }
        });

    if (!missingRequiredFields.isEmpty()) {
      throw new MissingRequiredFieldException(
          "required fields: (" + String.join(", ", missingRequiredFields) + ") were not set");
    }

    return finisher.apply(builder);
  }

  @Override
  public DeserializableTypeDefinition<TObject> withDescription(final String description) {
    return new DeserializableObjectTypeDefinition<>(
        Optional.empty(), // Clear name to ensure customised type is inlined
        getTitle(),
        Optional.of(description),
        initializer,
        finisher,
        deserializableFields);
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return deserializableFields.values().stream()
        .flatMap(field -> field.getReferencedTypeDefinitions().stream())
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
    if (!super.equals(o)) {
      return false;
    }
    final DeserializableObjectTypeDefinition<?, ?> that =
        (DeserializableObjectTypeDefinition<?, ?>) o;
    return Objects.equals(deserializableFields, that.deserializableFields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), deserializableFields);
  }
}

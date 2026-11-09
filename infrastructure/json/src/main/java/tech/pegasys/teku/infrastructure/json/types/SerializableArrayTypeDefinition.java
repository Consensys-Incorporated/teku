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
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class SerializableArrayTypeDefinition<ItemT, CollectionT extends Iterable<ItemT>>
    implements SerializableTypeDefinition<CollectionT> {
  private final SerializableTypeDefinition<ItemT> itemType;
  private final Optional<String> description;
  protected final Optional<Integer> minItems;
  protected final Optional<Integer> maxItems;

  public SerializableArrayTypeDefinition(final SerializableTypeDefinition<ItemT> itemType) {
    this.itemType = itemType;
    this.description = Optional.empty();
    this.minItems = Optional.empty();
    this.maxItems = Optional.empty();
  }

  public SerializableArrayTypeDefinition(
      final SerializableTypeDefinition<ItemT> itemType,
      final Optional<String> description,
      final Optional<Integer> minItems,
      final Optional<Integer> maxItems) {
    this.itemType = itemType;
    this.description = description;
    this.minItems = minItems;
    this.maxItems = maxItems;
    if (minItems.isPresent() && minItems.get() < 0) {
      throw new InvalidConfigurationException(
          String.format("minItems (%d) must be at least 0", minItems.get()));
    }
    if (maxItems.isPresent() && maxItems.get() < 0) {
      throw new InvalidConfigurationException(
          String.format("maxItems (%d) must be at least 0", maxItems.get()));
    }
    if (minItems.isPresent() && maxItems.isPresent() && minItems.get() > maxItems.get()) {
      throw new InvalidConfigurationException(
          String.format(
              "minItems (%d) must be LEQ maxItems (%d) in array type definition",
              minItems.get(), maxItems.get()));
    }
  }

  @Override
  public void serialize(final CollectionT values, final JsonGenerator gen) throws IOException {
    gen.writeStartArray();
    for (ItemT value : values) {
      itemType.serialize(value, gen);
    }
    gen.writeEndArray();
  }

  @Override
  public SerializableTypeDefinition<CollectionT> withDescription(final String description) {
    return new SerializableArrayTypeDefinition<>(
        itemType, Optional.of(description), minItems, maxItems);
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "array");
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (minItems.isPresent()) {
      gen.writeNumberField("minItems", minItems.get());
    }
    if (maxItems.isPresent()) {
      gen.writeNumberField("maxItems", maxItems.get());
    }
    gen.writeFieldName("items");
    itemType.serializeOpenApiTypeOrReference(gen);
    gen.writeEndObject();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return itemType.getSelfAndReferencedTypeDefinitions();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SerializableArrayTypeDefinition<?, ?> that = (SerializableArrayTypeDefinition<?, ?>) o;
    return Objects.equals(itemType, that.itemType) && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType, description);
  }
}

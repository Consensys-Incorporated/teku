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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class DeserializableArrayTypeDefinition<ItemT, CollectionT extends Iterable<ItemT>>
    extends SerializableArrayTypeDefinition<ItemT, CollectionT>
    implements DeserializableTypeDefinition<CollectionT> {

  private final DeserializableTypeDefinition<ItemT> itemType;
  private final Function<List<ItemT>, CollectionT> createFromList;

  public DeserializableArrayTypeDefinition(
      final DeserializableTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList) {
    super(itemType);
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  public DeserializableArrayTypeDefinition(
      final DeserializableTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList,
      final Optional<Integer> minItems,
      final Optional<Integer> maxItems) {
    super(itemType, Optional.empty(), minItems, maxItems);
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  public DeserializableArrayTypeDefinition(
      final DeserializableTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList,
      final String description) {
    super(itemType, Optional.of(description), Optional.empty(), Optional.empty());
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  @Override
  public CollectionT deserialize(final JsonParser parser) throws IOException {
    if (!parser.isExpectedStartArrayToken()) {
      throw MismatchedInputException.from(
          parser, String.class, "Array expected but got " + parser.getCurrentToken());
    }
    final List<ItemT> result = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      result.add(itemType.deserialize(parser));
    }
    if (minItems.isPresent() && result.size() < minItems.get()) {
      throw MismatchedInputException.from(
          parser,
          String.class,
          String.format("Provided array has less than %s minimum required items", minItems.get()));
    }
    if (maxItems.isPresent() && result.size() > maxItems.get()) {
      throw MismatchedInputException.from(
          parser,
          String.class,
          String.format("Provided array has more than %s maximum required items", maxItems.get()));
    }
    return createFromList.apply(result);
  }

  @Override
  public DeserializableTypeDefinition<CollectionT> withDescription(final String description) {
    return new DeserializableArrayTypeDefinition<>(itemType, createFromList, description);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeserializableArrayTypeDefinition<?, ?> that =
        (DeserializableArrayTypeDefinition<?, ?>) o;
    return Objects.equals(itemType, that.itemType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType);
  }
}

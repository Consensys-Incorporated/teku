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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;

public interface DeserializableTypeDefinition<TObject> extends SerializableTypeDefinition<TObject> {

  TObject deserialize(JsonParser parser) throws IOException;

  @Override
  DeserializableTypeDefinition<TObject> withDescription(final String description);

  static <TObject> StringTypeBuilder<TObject> string(
      @SuppressWarnings("unused") final Class<TObject> clazz) {
    return new StringTypeBuilder<>();
  }

  static <TObject> DeserializableTypeDefinition<List<TObject>> listOf(
      final DeserializableTypeDefinition<TObject> itemType) {
    return new DeserializableListTypeDefinition<>(itemType, Optional.empty(), Optional.empty());
  }

  static <TObject> DeserializableTypeDefinition<List<TObject>> listOf(
      final DeserializableTypeDefinition<TObject> itemType,
      final Optional<Integer> minItems,
      final Optional<Integer> maxItems) {
    return new DeserializableListTypeDefinition<>(itemType, minItems, maxItems);
  }

  static DeserializableTypeDefinition<Map<String, String>> mapOfStrings() {
    return mapOf(STRING_TYPE, STRING_TYPE, TreeMap::new);
  }

  static DeserializableTypeDefinition<Map<String, Object>> configMap() {
    return new DeserializableConfigTypeDefinition(
        Optional.empty(), Optional.empty(), Optional.empty(), TreeMap::new);
  }

  static <TKey, TValue> DeserializableTypeDefinition<Map<TKey, TValue>> mapOf(
      final StringValueTypeDefinition<TKey> keyType,
      final DeserializableTypeDefinition<TValue> valueType,
      final Supplier<Map<TKey, TValue>> mapConstructor) {
    return new DeserializableMapTypeDefinition<>(keyType, valueType, mapConstructor);
  }

  static <TObject extends Enum<TObject>> EnumTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType) {
    return new EnumTypeDefinition.EnumTypeBuilder<>(itemType, false).build();
  }

  static <TObject extends Enum<TObject>> EnumTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType, final boolean forceLowercase) {
    return new EnumTypeDefinition.EnumTypeBuilder<>(itemType, forceLowercase).build();
  }

  static <TObject> DeserializableObjectTypeDefinitionBuilder<TObject, TObject> object(
      @SuppressWarnings("unused") final Class<TObject> type) {
    final DeserializableObjectTypeDefinitionBuilder<TObject, TObject> typeBuilder = object();
    return typeBuilder.finisher(Function.identity());
  }

  static <TObject, TBuilder> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> object(
      @SuppressWarnings("unused") final Class<TObject> type,
      @SuppressWarnings("unused") final Class<TBuilder> builderType) {
    return object();
  }

  static <TObject, TBuilder> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> object() {
    return new DeserializableObjectTypeDefinitionBuilder<>();
  }
}

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
import java.util.List;
import java.util.Optional;

public interface SerializableTypeDefinition<T> extends OpenApiTypeDefinition {
  void serialize(T value, JsonGenerator gen) throws IOException;

  @Override
  SerializableTypeDefinition<T> withDescription(final String description);

  static <T> SerializableObjectTypeDefinitionBuilder<T> object(
      @SuppressWarnings("unused") final Class<T> type) {
    return object();
  }

  static <T> SerializableObjectTypeDefinitionBuilder<T> object() {
    return new SerializableObjectTypeDefinitionBuilder<>();
  }

  static <T> SerializableTypeDefinition<List<T>> listOf(
      final SerializableTypeDefinition<T> itemType) {
    return new SerializableArrayTypeDefinition<>(itemType);
  }

  static <T> SerializableTypeDefinition<List<T>> listOf(
      final SerializableTypeDefinition<T> itemType,
      final Optional<Integer> minItems,
      final Optional<Integer> maxItems) {
    return new SerializableArrayTypeDefinition<>(itemType, Optional.empty(), minItems, maxItems);
  }
}

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
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class DeserializableMapTypeDefinition<TKey, TValue>
    extends SerializableObjectTypeDefinition<Map<TKey, TValue>>
    implements DeserializableTypeDefinition<Map<TKey, TValue>> {

  private final StringValueTypeDefinition<TKey> keyType;
  private final DeserializableTypeDefinition<TValue> valueType;
  private final Supplier<? extends Map<TKey, TValue>> mapConstructor;

  public DeserializableMapTypeDefinition(
      final StringValueTypeDefinition<TKey> keyType,
      final DeserializableTypeDefinition<TValue> valueType,
      final Supplier<? extends Map<TKey, TValue>> mapConstructor) {
    this(keyType, valueType, mapConstructor, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public DeserializableMapTypeDefinition(
      final StringValueTypeDefinition<TKey> keyType,
      final DeserializableTypeDefinition<TValue> valueType,
      final Supplier<? extends Map<TKey, TValue>> mapConstructor,
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description) {
    super(name, title, description, Map.of());
    this.keyType = keyType;
    this.valueType = valueType;
    this.mapConstructor = mapConstructor;
  }

  @Override
  public Map<TKey, TValue> deserialize(final JsonParser p) throws IOException {
    final Map<TKey, TValue> result = mapConstructor.get();
    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      final String fieldName = p.currentName();
      p.nextToken();
      final TValue fieldValue = valueType.deserialize(p);
      result.put(keyType.deserializeFromString(fieldName), fieldValue);
    }
    return result;
  }

  @Override
  public void serialize(final Map<TKey, TValue> value, final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    for (Map.Entry<TKey, TValue> entry : value.entrySet()) {
      gen.writeFieldName(keyType.serializeToString(entry.getKey()));

      valueType.serialize(entry.getValue(), gen);
    }
    gen.writeEndObject();
  }

  @Override
  public DeserializableTypeDefinition<Map<TKey, TValue>> withDescription(final String description) {
    return new DeserializableMapTypeDefinition<>(
        keyType, valueType, mapConstructor, Optional.empty(), getTitle(), Optional.of(description));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "object");
    if (getTitle().isPresent()) {
      gen.writeStringField("title", getTitle().get());
    }
    if (getDescription().isPresent()) {
      gen.writeStringField("description", getDescription().get());
    }
    gen.writeObjectFieldStart("additionalProperties");
    gen.writeStringField("type", "string");
    gen.writeEndObject();
    gen.writeEndObject();
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
    final DeserializableMapTypeDefinition<?, ?> that = (DeserializableMapTypeDefinition<?, ?>) o;
    return Objects.equals(keyType, that.keyType) && Objects.equals(valueType, that.valueType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), keyType, valueType);
  }
}

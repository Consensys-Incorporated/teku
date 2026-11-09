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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

// Configuration is Extended in fulu with BPO to also have a list of objects potentially
// This class allows a Map, where entries are
// - String -> String
// - String -> List<Map<String,String>>
//
// Before Fulu, Config was simply a Map<String,String>.
class DeserializableConfigTypeDefinition
    implements SerializableTypeDefinition<Map<String, Object>>,
        DeserializableTypeDefinition<Map<String, Object>> {
  private final Optional<String> name;
  private final Optional<String> title;
  private final Optional<String> description;
  private final DeserializableTypeDefinition<List<Map<String, String>>>
      listDeserializableTypeDefinition =
          DeserializableTypeDefinition.listOf(DeserializableTypeDefinition.mapOfStrings());
  private final Supplier<Map<String, Object>> mapConstructor;

  public DeserializableConfigTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description,
      final Supplier<Map<String, Object>> mapConstructor) {
    this.name = name;
    this.title = title;
    this.description = description;
    this.mapConstructor = mapConstructor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void serialize(final Map<String, Object> value, final JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    for (Map.Entry<String, Object> entry : value.entrySet()) {
      gen.writeFieldName(STRING_TYPE.serializeToString(entry.getKey()));
      if (entry.getValue() instanceof String) {
        STRING_TYPE.serialize((String) entry.getValue(), gen);
      } else {
        listDeserializableTypeDefinition.serialize(
            (List<Map<String, String>>) entry.getValue(), gen);
      }
    }
    gen.writeEndObject();
  }

  @Override
  public Map<String, Object> deserialize(final JsonParser p) throws IOException {
    final Map<String, Object> result = mapConstructor.get();
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
      if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
        result.put(fieldName, p.getValueAsString());
      } else if (p.getCurrentToken() == JsonToken.START_ARRAY) {
        result.put(fieldName, listDeserializableTypeDefinition.deserialize(p));
      }
    }
    return result;
  }

  @Override
  public DeserializableConfigTypeDefinition withDescription(final String description) {
    return new DeserializableConfigTypeDefinition(
        this.name, this.title, Optional.ofNullable(description), mapConstructor);
  }

  public Optional<String> getTitle() {
    return title;
  }

  public Optional<String> getName() {
    return name;
  }

  public Optional<String> getDescription() {
    return description;
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
}

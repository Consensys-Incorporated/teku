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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class DeserializableConfigTypeDefinitionTest {
  final DeserializableConfigTypeDefinition typeDefinition =
      new DeserializableConfigTypeDefinition(
          Optional.of("MyName"),
          Optional.of("MyTitle"),
          Optional.of("MyDescription"),
          LinkedHashMap::new);

  @Test
  void shouldSerializeSimpleMap() throws JsonProcessingException {
    final Map<String, Object> objectMap = Map.of("key", "value");
    final String output = JsonUtil.serialize(objectMap, typeDefinition);
    assertThat(output).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  void shouldSerializeListEntry() throws JsonProcessingException {
    final Map<String, Object> objectMap =
        Map.of("key", List.of(Map.of("a", "b"), Map.of("c", "d")));
    final String output = JsonUtil.serialize(objectMap, typeDefinition);
    assertThat(output).isEqualTo("{\"key\":[{\"a\":\"b\"},{\"c\":\"d\"}]}");
  }

  @Test
  void shouldDeserializeSimpleMap() throws JsonProcessingException {
    final String simpleMap = "{\"key\":\"value\"}";
    final Map<String, Object> z = JsonUtil.parse(simpleMap, typeDefinition);
    assertThat(z.get("key")).isEqualTo("value");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDeserializeListEntry() throws JsonProcessingException {
    final String listEntry = "{\"key\":[{\"a\":\"b\"},{\"c\":\"d\"}]}";
    final Map<String, Object> z = JsonUtil.parse(listEntry, typeDefinition);
    assertThat(z.get("key")).isInstanceOf(List.class);
    final List<Map<String, String>> list = (List<Map<String, String>>) z.get("key");
    assertThat(list).containsExactly(Map.of("a", "b"), Map.of("c", "d"));
  }

  @Test
  void shouldSerializeShortConfigMap() throws JsonProcessingException {
    final Map<String, Object> objectMap = new LinkedHashMap<>();
    objectMap.put("key", "value");
    objectMap.put("key2", List.of(Map.of("a", "b"), Map.of("c", "d")));

    final String output = JsonUtil.serialize(objectMap, typeDefinition);
    assertThat(output).isEqualTo("{\"key\":\"value\",\"key2\":[{\"a\":\"b\"},{\"c\":\"d\"}]}");
  }

  @Test
  void shouldWriteOpenApiDefinition() throws JsonProcessingException {
    final String openApiType = JsonUtil.serialize(typeDefinition::serializeOpenApiType);
    assertThat(openApiType)
        .isEqualTo(
            "{\"type\":\"object\",\"title\":\"MyTitle\",\"description\":\"MyDescription\",\"additionalProperties\":{\"type\":\"string\"}}");
  }
}

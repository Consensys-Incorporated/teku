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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class DeserializableMapTypeDefinitionTest {

  final String serializedData = "{\"ak\":\"av\",\"bk\":\"bv\",\"ck\":\"cv\"}";
  private final DeserializableMapTypeDefinition<String, String> stringMapDefinition =
      new DeserializableMapTypeDefinition<>(
          CoreTypes.STRING_TYPE,
          CoreTypes.STRING_TYPE,
          TreeMap::new,
          Optional.empty(),
          Optional.empty(),
          Optional.empty());

  private final DeserializableTypeDefinition<Map<Integer, String>> integerKeyedMapDefinition =
      DeserializableTypeDefinition.mapOf(
          CoreTypes.RAW_INTEGER_TYPE, CoreTypes.STRING_TYPE, TreeMap::new);

  private static final DeserializableTypeDefinition<TestObj> TESTOBJ_TYPE =
      DeserializableTypeDefinition.object(TestObj.class)
          .initializer(TestObj::new)
          .withField("data", CoreTypes.STRING_TYPE, TestObj::getData, TestObj::setData)
          .build();

  private final DeserializableTypeDefinition<Map<String, TestObj>> objectMap =
      DeserializableTypeDefinition.mapOf(CoreTypes.STRING_TYPE, TESTOBJ_TYPE, TreeMap::new);

  @Test
  void shouldSerializeMap() throws JsonProcessingException {
    assertThat(JsonUtil.serialize(getStringMap(), stringMapDefinition)).isEqualTo(serializedData);
  }

  @Test
  void shouldDeserializeMap() throws JsonProcessingException {
    assertThat(JsonUtil.parse(serializedData, stringMapDefinition)).isEqualTo(getStringMap());
  }

  @Test
  void shouldRejectNestedArray() {
    final String input = "{\"ak\":[1,2,3]}";
    assertThatThrownBy(() -> JsonUtil.parse(input, stringMapDefinition))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void shouldRejectNestedObject() {
    final String input = "{\"ak\":{\"bk\":1}}";
    assertThatThrownBy(() -> JsonUtil.parse(input, stringMapDefinition))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void shouldWriteOpenApiType() throws JsonProcessingException {
    final String json = JsonUtil.serialize(stringMapDefinition::serializeOpenApiType);
    assertThat(json)
        .isEqualTo("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}");
  }

  @Test
  void shouldAllowTypedKeys() throws JsonProcessingException {
    final Map<Integer, String> data = new TreeMap<>();
    data.putAll(Map.of(1, "a", 2, "b"));
    final String json = JsonUtil.serialize(data, integerKeyedMapDefinition);
    assertThat(json).isEqualTo("{\"1\":\"a\",\"2\":\"b\"}");
    final Map<Integer, String> dataOut = JsonUtil.parse(json, integerKeyedMapDefinition);
    assertThat(dataOut).isEqualTo(data);
  }

  @Test
  void shouldAllowTypedValues() throws JsonProcessingException {
    final Map<String, TestObj> data = new TreeMap<>();
    data.put("a", new TestObj("aa"));
    final String json = JsonUtil.serialize(data, objectMap);
    AssertionsForClassTypes.assertThat(json).isEqualTo("{\"a\":{\"data\":\"aa\"}}");

    final Map<String, TestObj> dataOut = JsonUtil.parse(json, objectMap);
    assertThat(dataOut).isEqualTo(data);
  }

  private Map<String, String> getStringMap() {
    final Map<String, String> data = new TreeMap<>();
    data.put("ak", "av");
    data.put("bk", "bv");
    data.put("ck", "cv");
    return data;
  }

  private static class TestObj {
    private String data;

    public TestObj() {}

    public TestObj(final String data) {
      this.data = data;
    }

    public String getData() {
      return data;
    }

    public void setData(final String data) {
      this.data = data;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TestObj testObj = (TestObj) o;
      return Objects.equals(data, testObj.data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(data);
    }
  }
}

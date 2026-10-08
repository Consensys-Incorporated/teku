/*
 * Copyright Consensys Software Inc., 2026
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
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SerializableObjectTypeDefinitionBuilderTest {

  @Test
  void shouldSerializeSimpleValue() throws Exception {
    final SerializableTypeDefinition<SimpleValue> type =
        SerializableTypeDefinition.object(SimpleValue.class)
            .withField("value1", STRING_TYPE, SimpleValue::getValue1)
            .withField("value2", UINT64_TYPE, SimpleValue::getValue2)
            .build();

    final String json = JsonUtil.serialize(new SimpleValue("abc", UInt64.valueOf(300)), type);
    assertThat(JsonTestUtil.parse(json))
        .containsExactly(entry("value1", "abc"), entry("value2", "300"));
  }

  @Test
  void shouldNotIncludeOptionalFieldWithValue() throws Exception {
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withOptionalField("optional", STRING_TYPE, WithOptionalValue::getValue)
            .build();

    final String json = JsonUtil.serialize(new WithOptionalValue(Optional.of("foo")), type);
    assertThat(JsonTestUtil.parse(json)).containsExactly(entry("optional", "foo"));
  }

  @Test
  void shouldNotIncludeOptionalFieldWithNoValue() throws Exception {
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withOptionalField("optional", STRING_TYPE, WithOptionalValue::getValue)
            .build();

    final String json = JsonUtil.serialize(new WithOptionalValue(Optional.empty()), type);
    assertThat(JsonTestUtil.parse(json)).isEmpty();
  }

  @Test
  void shouldIncludeNullableFieldWithValue() throws Exception {
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withNullableField("nullable", STRING_TYPE, WithOptionalValue::getValue)
            .build();

    final String json = JsonUtil.serialize(new WithOptionalValue(Optional.of("foo")), type);
    assertThat(JsonTestUtil.parse(json)).containsExactly(entry("nullable", "foo"));
  }

  @Test
  void shouldWriteNullForNullableFieldWithNoValue() throws Exception {
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withNullableField("nullable", STRING_TYPE, WithOptionalValue::getValue)
            .build();

    final String json = JsonUtil.serialize(new WithOptionalValue(Optional.empty()), type);
    assertThat(json).isEqualTo("{\"nullable\":null}");
  }

  @Test
  void shouldDescribeNullableFieldAsRequiredAndNullable() throws Exception {
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withNullableField("nullable", STRING_TYPE, WithOptionalValue::getValue)
            .build();

    final String json = JsonUtil.serialize(type::serializeOpenApiType);
    assertThat(json)
        .isEqualTo(
            "{\"type\":\"object\",\"required\":[\"nullable\"],\"properties\":"
                + "{\"nullable\":{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"object\",\"nullable\":true,\"enum\":[null]}]}}}");
  }

  @Test
  void shouldDescribeNullableEnumWithoutAllowingOtherValues() throws Exception {
    final SerializableTypeDefinition<Optional<TestEnum>> type =
        SerializableTypeDefinition.<Optional<TestEnum>>object()
            .withNullableField(
                "value", DeserializableTypeDefinition.enumOf(TestEnum.class), value -> value)
            .build();
    final JsonNode field =
        new ObjectMapper()
            .readTree(JsonUtil.serialize(type::serializeOpenApiType))
            .path("properties")
            .path("value");
    assertThat(field.path("anyOf").size()).isEqualTo(2);
    assertThat(field.path("anyOf").get(0).path("enum").get(0).asText()).isEqualTo("VALUE");
    assertNullOnlySchema(field.path("anyOf").get(1));
    assertThat(JsonUtil.serialize(Optional.empty(), type)).isEqualTo("{\"value\":null}");
    assertThat(JsonUtil.serialize(Optional.of(TestEnum.VALUE), type))
        .isEqualTo("{\"value\":\"VALUE\"}");
  }

  @Test
  void shouldDescribeNullableReferenceWithoutChangingReferencedType() throws Exception {
    final SerializableTypeDefinition<String> referenced =
        SerializableTypeDefinition.object(String.class)
            .name("ReferencedType")
            .withField("value", STRING_TYPE, value -> value)
            .build();
    final SerializableTypeDefinition<WithOptionalValue> type =
        SerializableTypeDefinition.object(WithOptionalValue.class)
            .withNullableField("value", referenced, WithOptionalValue::getValue)
            .build();
    final JsonNode field =
        new ObjectMapper()
            .readTree(JsonUtil.serialize(type::serializeOpenApiType))
            .path("properties")
            .path("value");
    assertThat(field.path("anyOf").size()).isEqualTo(2);
    assertThat(field.path("anyOf").get(0).path("$ref").asText())
        .isEqualTo("#/components/schemas/ReferencedType");
    assertNullOnlySchema(field.path("anyOf").get(1));
    assertThat(type.getReferencedTypeDefinitions()).contains(referenced);
    assertThat(JsonUtil.serialize(new WithOptionalValue(Optional.of("foo")), type))
        .isEqualTo("{\"value\":{\"value\":\"foo\"}}");
  }

  private void assertNullOnlySchema(final JsonNode schema) {
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("nullable").asBoolean()).isTrue();
    assertThat(schema.path("enum").size()).isEqualTo(1);
    assertThat(schema.path("enum").get(0).isNull()).isTrue();
  }

  private enum TestEnum {
    VALUE
  }

  @Test
  void shouldGetReferencedTypesRecursively() {
    final SerializableTypeDefinition<String> type1 =
        SerializableTypeDefinition.object(String.class).name("Type1").build();
    final SerializableTypeDefinition<String> type2 =
        SerializableTypeDefinition.object(String.class)
            .name("Type2")
            .withField("type1", type1, __ -> null)
            .build();
    final SerializableTypeDefinition<String> type3 =
        SerializableTypeDefinition.object(String.class)
            .name("Type3")
            .withField("type2", type2, __ -> null)
            .build();

    assertThat(type3.getReferencedTypeDefinitions()).containsExactlyInAnyOrder(type1, type2);
  }

  private static class WithOptionalValue {
    private final Optional<String> value;

    private WithOptionalValue(final Optional<String> value) {
      this.value = value;
    }

    public Optional<String> getValue() {
      return value;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final WithOptionalValue that = (WithOptionalValue) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("value", value).toString();
    }
  }

  private static class SimpleValue {
    private final String value1;
    private final UInt64 value2;

    private SimpleValue(final String value1, final UInt64 value2) {
      this.value1 = value1;
      this.value2 = value2;
    }

    public String getValue1() {
      return value1;
    }

    public UInt64 getValue2() {
      return value2;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SimpleValue that = (SimpleValue) o;
      return Objects.equals(value1, that.value1) && Objects.equals(value2, that.value2);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value1, value2);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("value1", value1)
          .add("value2", value2)
          .toString();
    }
  }
}

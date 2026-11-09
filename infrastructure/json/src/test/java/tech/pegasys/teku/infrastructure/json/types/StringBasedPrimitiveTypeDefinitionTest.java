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
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.json.JsonTestUtil.parse;
import static tech.pegasys.teku.infrastructure.json.JsonTestUtil.parseString;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

class StringBasedPrimitiveTypeDefinitionTest {
  @Test
  void serializeOpenApiType_minimalOptions() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .formatter(Function.identity())
            .parser(Function.identity())
            .example("ex")
            .build();

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result).containsOnly(entry("type", "string"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withFormat() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .formatter(Function.identity())
            .parser(Function.identity())
            .example("ex")
            .format("My format")
            .build();

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("format", "My format"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withDescription() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .formatter(Function.identity())
            .parser(Function.identity())
            .example("ex")
            .description("A description")
            .build();

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("description", "A description"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withName() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .name("TestName")
            .formatter(Function.identity())
            .parser(Function.identity())
            .example("ex")
            .build();

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(entry("type", "string"), entry("title", "TestName"), entry("example", "ex"));
    assertThat(type.getTypeName()).contains("TestName");
  }

  @Test
  void serialize_shouldApplyConverter() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .formatter(value -> value.toUpperCase(Locale.ROOT))
            .parser(value -> null)
            .example("ex")
            .build();

    final String result = parseString(serialize(gen -> type.serialize("Foo", gen)));

    assertThat(result).isEqualTo("FOO");
  }

  @Test
  void deserialize_shouldApplyConverter() throws Exception {
    final DeserializableTypeDefinition<String> type =
        DeserializableTypeDefinition.string(String.class)
            .parser(value -> value.toUpperCase(Locale.ROOT))
            .formatter(value -> null)
            .example("ex")
            .build();

    final String result = JsonUtil.parse("\"Foo\"", type);

    assertThat(result).isEqualTo("FOO");
  }
}

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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class SerializableArrayTypeDefinitionTest {
  private final SerializableTypeDefinition<List<String>> stringListType =
      SerializableTypeDefinition.listOf(STRING_TYPE);

  @Test
  void serialize_shouldSerializeEmptyArray() throws Exception {
    final List<Object> result =
        JsonTestUtil.parseList(JsonUtil.serialize(emptyList(), stringListType));
    assertThat(result).isEmpty();
  }

  @Test
  void serialize_shouldSerializeArrayWithValues() throws Exception {
    final List<Object> result =
        JsonTestUtil.parseList(JsonUtil.serialize(List.of("a", "b", "c"), stringListType));
    assertThat(result).containsExactly("a", "b", "c");
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

    assertThat(SerializableTypeDefinition.listOf(type2).getReferencedTypeDefinitions())
        .containsExactlyInAnyOrder(type1, type2);
  }

  @Test
  void shouldFailToConfigureArrayDefinitionWithMinMaxMismatch() {
    assertThatThrownBy(
            () -> SerializableTypeDefinition.listOf(STRING_TYPE, Optional.of(4), Optional.of(3)))
        .satisfies(
            ex -> {
              assertThat(ex).isInstanceOf(InvalidConfigurationException.class);
              assertThat(ex.getMessage()).contains("minItems (4) must be LEQ maxItems (3)");
            });
  }

  @Test
  void shouldFailToConfigureArrayTypeDefinitionWithNegativeMinimum() {
    assertThatThrownBy(
            () -> SerializableTypeDefinition.listOf(STRING_TYPE, Optional.of(-1), Optional.of(3)))
        .satisfies(
            ex -> {
              assertThat(ex).isInstanceOf(InvalidConfigurationException.class);
              assertThat(ex.getMessage()).isEqualTo("minItems (-1) must be at least 0");
            });
  }

  @Test
  void shouldFailToConfigureArrayTypeDefinitionWithNegativeMaximum() {
    assertThatThrownBy(
            () -> SerializableTypeDefinition.listOf(STRING_TYPE, Optional.empty(), Optional.of(-1)))
        .satisfies(
            ex -> {
              assertThat(ex).isInstanceOf(InvalidConfigurationException.class);
              assertThat(ex.getMessage()).isEqualTo("maxItems (-1) must be at least 0");
            });
  }
}

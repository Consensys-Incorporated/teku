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

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

class DeserializableArrayTypeDefinitionTest {

  private final DeserializableTypeDefinition<List<String>> stringListType =
      DeserializableTypeDefinition.listOf(STRING_TYPE);

  @Test
  void shouldRoundTripEmptyList() throws Exception {
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(emptyList(), stringListType), stringListType);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldRoundTripNonEmptyList() throws Exception {
    final List<String> value = List.of("x", "y", "z", "x");
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(value, stringListType), stringListType);

    assertThat(result).isEqualTo(value);
  }

  @Test
  void shouldRoundTripMultipleElementList() throws Exception {
    final DeserializableTypeDefinition<List<String>> boundedArrayType =
        DeserializableTypeDefinition.listOf(STRING_TYPE, Optional.of(4), Optional.of(4));
    final List<String> value = List.of("x", "y", "z", "x");
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(value, boundedArrayType), boundedArrayType);

    assertThat(result).isEqualTo(value);
  }

  @Test
  void shouldThrowIfMinItemsNotMetWhenParse() {
    final DeserializableTypeDefinition<List<String>> stringMinItemsType =
        DeserializableTypeDefinition.listOf(STRING_TYPE, Optional.of(1), Optional.empty());
    final List<String> value = List.of();
    assertThatThrownBy(
            () -> JsonUtil.parse(JsonUtil.serialize(value, stringMinItemsType), stringMinItemsType))
        .satisfies(
            ex -> {
              assertThat(ex).isInstanceOf(MismatchedInputException.class);
              assertThat(ex)
                  .hasMessageContaining("Provided array has less than 1 minimum required items");
            });
  }

  @Test
  void shouldThrowIfMaxItemsNotMetWhenParse() {
    final DeserializableTypeDefinition<List<String>> boundedArrayType =
        DeserializableTypeDefinition.listOf(STRING_TYPE, Optional.of(1), Optional.of(2));
    final List<String> value = List.of("a", "b", "c");
    assertThatThrownBy(
            () -> JsonUtil.parse(JsonUtil.serialize(value, boundedArrayType), boundedArrayType))
        .satisfies(
            ex -> {
              assertThat(ex).isInstanceOf(MismatchedInputException.class);
              assertThat(ex)
                  .hasMessageContaining("Provided array has more than 2 maximum required items");
            });
  }

  @Test
  void shouldParseIfMinItemsMet() throws Exception {
    final DeserializableTypeDefinition<List<String>> stringMinItemsType =
        DeserializableTypeDefinition.listOf(STRING_TYPE, Optional.of(1), Optional.empty());
    final List<String> value = List.of("x");
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(value, stringMinItemsType), stringMinItemsType);

    assertThat(result).isEqualTo(value);
  }
}

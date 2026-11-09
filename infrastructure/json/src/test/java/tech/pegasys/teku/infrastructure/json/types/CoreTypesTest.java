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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class CoreTypesTest {
  final DeserializableTypeDefinition<List<String>> stringListType =
      DeserializableTypeDefinition.listOf(STRING_TYPE);

  @Test
  void uint64_shouldRoundTrip() throws Exception {
    assertRoundTrip(UInt64.valueOf(200), CoreTypes.UINT64_TYPE);
  }

  @Test
  void string_shouldRoundTrip() throws Exception {
    assertRoundTrip("some string", CoreTypes.STRING_TYPE);
  }

  @Test
  void integer_shouldRoundTrip() throws Exception {
    assertRoundTrip(458, CoreTypes.RAW_INTEGER_TYPE);
  }

  @Test
  void double_shouldRoundTrip() throws Exception {
    assertRoundTrip(458.23, CoreTypes.RAW_DOUBLE_TYPE);
  }

  @Test
  void httpErrorResponse_shouldSerialize() throws Exception {
    final HttpErrorResponse value = new HttpErrorResponse(442, "No good");
    final Map<String, Object> result =
        JsonTestUtil.parse(serialize(value, CoreTypes.HTTP_ERROR_RESPONSE_TYPE));

    assertThat(result).containsOnly(entry("code", 442), entry("message", "No good"));
  }

  @Test
  void stringListShouldRejectNestedArray() throws Exception {
    final String input = "[\"a\", \"b\", [\"c\"], \"d\"]";
    assertThatThrownBy(() -> JsonUtil.parse(input, stringListType))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void stringListShouldRejectNestedObject() throws Exception {
    final String input = "[\"a\", \"b\", {\"c\": \"d\"}, \"e\"]";
    assertThatThrownBy(() -> JsonUtil.parse(input, stringListType))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void stringListShouldAcceptNullValue() throws Exception {
    final String input = "[\"a\", \"b\", null, \"c\"]";
    final List<String> result = JsonUtil.parse(input, stringListType);
    assertThat(result).containsNull();
  }

  @Test
  void stringListShouldAcceptQuotedNullValue() throws Exception {
    final String input = "[\"a\", \"b\", \"null\", \"c\"]";
    final List<String> result = JsonUtil.parse(input, stringListType);
    assertThat(result).doesNotContainNull();
  }

  @Test
  void stringListShouldAcceptNumberAsString() throws Exception {
    final String input = "[\"a\", \"b\", 256, \"c\"]";
    final List<String> result = JsonUtil.parse(input, stringListType);
    assertThat(result).contains("256");
  }

  @Test
  void stringListShouldAcceptBooleanAsString() throws Exception {
    final String input = "[\"a\", \"b\", true, \"c\"]";
    final List<String> result = JsonUtil.parse(input, stringListType);
    assertThat(result).contains("true");
  }

  @Test
  void shouldAcceptListOfLists() throws Exception {
    final DeserializableTypeDefinition<List<List<String>>> listOfStringListType =
        DeserializableTypeDefinition.listOf(stringListType);

    final String input = "[[\"a\", \"b\", \"c\"], [\"d\", \"e\", \"f\"]]";
    final List<List<String>> result = JsonUtil.parse(input, listOfStringListType);
    assertThat(result).isEqualTo(List.of(List.of("a", "b", "c"), List.of("d", "e", "f")));
  }
}

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class EnumTypeDefinitionTest {
  DeserializableTypeDefinition<YesNo> definition = DeserializableTypeDefinition.enumOf(YesNo.class);

  @Test
  void shouldSerializeEnum() throws Exception {
    final String json = JsonUtil.serialize(YesNo.YES, definition);
    assertThat(json).isEqualTo("\"yes\"");
  }

  @Test
  void shouldSerializeEnumForceLowerCase() throws Exception {
    DeserializableTypeDefinition<YES_NO> definition =
        DeserializableTypeDefinition.enumOf(YES_NO.class);
    String json = JsonUtil.serialize(YES_NO.YES, definition);
    assertThat(json).isEqualTo("\"YES\"");

    definition = DeserializableTypeDefinition.enumOf(YES_NO.class, true);
    json = JsonUtil.serialize(YES_NO.YES, definition);
    assertThat(json).isEqualTo("\"yes\"");
  }

  @Test
  void shouldParseEnum() throws Exception {
    assertThat(JsonUtil.parse("\"no\"", definition)).isEqualTo(YesNo.NO);
  }

  @Test
  void excludedShouldThrowExceptionSerialize() throws JsonProcessingException {
    DeserializableTypeDefinition<YesNo> definition =
        new EnumTypeDefinition.EnumTypeBuilder<>(YesNo.class, Objects::toString)
            .excludedEnumerations(Set.of(YesNo.YES))
            .build();
    assertThatThrownBy(() -> JsonUtil.serialize(YesNo.YES, definition))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(JsonUtil.serialize(YesNo.NO, definition)).isEqualTo("\"no\"");
  }

  @Test
  void excludedShouldThrowExceptionDeserialize() throws JsonProcessingException {
    DeserializableTypeDefinition<YesNo> definition =
        new EnumTypeDefinition.EnumTypeBuilder<>(YesNo.class, Objects::toString)
            .excludedEnumerations(Set.of(YesNo.YES))
            .build();
    assertThatThrownBy(() -> JsonUtil.parse("\"yes\"", definition))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(JsonUtil.parse("\"no\"", definition)).isEqualTo(YesNo.NO);
  }

  private enum YesNo {
    YES("yes"),
    NO("no");

    private final String displayName;

    YesNo(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  @SuppressWarnings("JavaCase")
  private enum YES_NO {
    YES,
    NO
  }
}

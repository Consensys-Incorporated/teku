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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;

public class RequiredDeserializableFieldDefinitionTest {
  private final DeserializableTypeDefinition<TestClass> definition =
      DeserializableTypeDefinition.object(TestClass.class)
          .name("TestClass")
          .initializer(TestClass::new)
          .withField("a", STRING_TYPE, TestClass::getFirst, TestClass::setFirst)
          .withField("b", STRING_TYPE, TestClass::getSecond, TestClass::setSecond)
          .withOptionalField("c", STRING_TYPE, TestClass::getThird, TestClass::setThird)
          .build();

  @Test
  void shouldCauseExceptionIfRequiredFieldIsMissing() {
    assertThatThrownBy(() -> JsonUtil.parse("{\"a\": \"zz\"}", definition))
        .isInstanceOf(MissingRequiredFieldException.class)
        .hasMessageContaining("fields: (b)");
  }

  @Test
  void shouldCauseExceptionIfMultipleRequiredFieldIsMissing() {
    assertThatThrownBy(() -> JsonUtil.parse("{}", definition))
        .isInstanceOf(MissingRequiredFieldException.class)
        .hasMessageContaining("fields: (a, b)");
  }

  @Test
  void shouldParseIfAllRequiredFieldsPresent() throws JsonProcessingException {
    final TestClass testClass = JsonUtil.parse("{\"a\": \"aa\", \"b\": \"bb\"}", definition);
    assertThat(testClass).isNotNull();
  }

  @Test
  void shouldParseIfAllFieldsPresent() throws JsonProcessingException {
    final TestClass testClass =
        JsonUtil.parse("{\"a\": \"aa\", \"b\": \"bb\", \"c\": \"cc\"}", definition);
    assertThat(testClass).isNotNull();
  }

  private static class TestClass {
    private String first;
    private String second;
    private Optional<String> third;

    TestClass() {}

    public String getFirst() {
      return first;
    }

    public void setFirst(final String first) {
      this.first = first;
    }

    public String getSecond() {
      return second;
    }

    public void setSecond(final String second) {
      this.second = second;
    }

    public Optional<String> getThird() {
      return third;
    }

    public void setThird(final Optional<String> third) {
      this.third = third;
    }
  }
}

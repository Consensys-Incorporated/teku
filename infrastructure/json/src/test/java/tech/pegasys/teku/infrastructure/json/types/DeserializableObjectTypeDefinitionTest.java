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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;

class DeserializableObjectTypeDefinitionTest {

  public static final DeserializableTypeDefinition<MutableValue> MUTABLE_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(MutableValue.class)
          .initializer(MutableValue::new)
          .withField(
              "required",
              CoreTypes.STRING_TYPE,
              MutableValue::getRequired,
              MutableValue::setRequired)
          .withOptionalField(
              "optional",
              CoreTypes.STRING_TYPE,
              MutableValue::getOptional,
              MutableValue::setOptional)
          .build();

  public static final DeserializableTypeDefinition<ImmutableValue> IMMUTABLE_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(ImmutableValue.class, ImmutableValueBuilder.class)
          .initializer(ImmutableValueBuilder::new)
          .finisher(ImmutableValueBuilder::build)
          .withField(
              "required",
              CoreTypes.STRING_TYPE,
              ImmutableValue::getRequired,
              ImmutableValueBuilder::required)
          .withOptionalField(
              "optional",
              CoreTypes.STRING_TYPE,
              ImmutableValue::getOptional,
              ImmutableValueBuilder::optional)
          .build();

  @Test
  void shouldDeserializeIntoMutableObject() throws Exception {
    final MutableValue result =
        JsonUtil.parse(
            "{\"required\":\"value1\", \"optional\":\"value2\"}", MUTABLE_TYPE_DEFINITION);

    assertThat(result.required).isEqualTo("value1");
    assertThat(result.optional).contains("value2");
  }

  @Test
  void shouldFailToDeserializeIntoMutableObjectWhenRequiredFieldMissing() {
    assertThatThrownBy(() -> JsonUtil.parse("{\"optional\":\"value2\"}", MUTABLE_TYPE_DEFINITION))
        .isInstanceOf(MissingRequiredFieldException.class);
  }

  @Test
  void shouldDeserializeIntoMutableObjectWithoutOptionalField() throws Exception {
    final MutableValue result =
        JsonUtil.parse("{\"required\":\"value1\"}", MUTABLE_TYPE_DEFINITION);

    assertThat(result.required).isEqualTo("value1");
    assertThat(result.optional).isEmpty();
  }

  @Test
  void shouldDeserializeUsingABuilder() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse(
            "{\"required\":\"value1\", \"optional\":\"value2\"}", IMMUTABLE_TYPE_DEFINITION);

    assertThat(result.required).isEqualTo("value1");
    assertThat(result.optional).contains("value2");
  }

  @Test
  void shouldFailToDeserializeIntoImmutableObjectWhenRequiredFieldMissing() {
    assertThatThrownBy(() -> JsonUtil.parse("{\"optional\":\"value2\"}", IMMUTABLE_TYPE_DEFINITION))
        .isInstanceOf(MissingRequiredFieldException.class);
  }

  @Test
  void shouldDeserializeUsingABuilderWithoutOptionalField() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse("{\"required\":\"value1\"}", IMMUTABLE_TYPE_DEFINITION);

    assertThat(result.required).isEqualTo("value1");
    assertThat(result.optional).isEmpty();
  }

  @Test
  void shouldIgnoreUnknownFieldWithSimpleValue() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse(
            "{\"required\":\"value\", \"unknown\":\"ignored\"}", IMMUTABLE_TYPE_DEFINITION);
    assertThat(result.required).isEqualTo("value");
  }

  @Test
  void shouldIgnoreUnknownFieldWithObjectValue() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse(
            "{\"unknown\":{\"optional\":\"ignored\"}, \"required\":\"value\"}",
            IMMUTABLE_TYPE_DEFINITION);
    assertThat(result.required).isEqualTo("value");
    assertThat(result.optional).isEmpty();
  }

  @Test
  void shouldIgnoreUnknownFieldWithArrayValue() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse(
            "{\"unknown\":[\"optional\"], \"required\":\"value\"}", IMMUTABLE_TYPE_DEFINITION);
    assertThat(result.required).isEqualTo("value");
    assertThat(result.optional).isEmpty();
  }

  @Test
  void shouldIgnoreUnknownFieldWithArrayOfObjectValue() throws Exception {
    final ImmutableValue result =
        JsonUtil.parse(
            "{\"unknown\":[{\"optional\":\"ignored\"}], \"required\":\"value\"}",
            IMMUTABLE_TYPE_DEFINITION);
    assertThat(result.required).isEqualTo("value");
    assertThat(result.optional).isEmpty();
  }

  private static class MutableValue {
    private String required;
    private Optional<String> optional;

    public String getRequired() {
      return required;
    }

    public void setRequired(final String required) {
      this.required = required;
    }

    public Optional<String> getOptional() {
      return optional;
    }

    public void setOptional(final Optional<String> optional) {
      this.optional = optional;
    }
  }

  private static class ImmutableValue {
    private final String required;
    private final Optional<String> optional;

    private ImmutableValue(final String required, final Optional<String> optional) {
      this.required = required;
      this.optional = optional;
    }

    public String getRequired() {
      return required;
    }

    public Optional<String> getOptional() {
      return optional;
    }
  }

  private static class ImmutableValueBuilder {
    private String required;
    private Optional<String> optional;

    public ImmutableValueBuilder required(final String required) {
      this.required = required;
      return this;
    }

    public ImmutableValueBuilder optional(final Optional<String> optional) {
      this.optional = optional;
      return this;
    }

    public ImmutableValue build() {
      checkNotNull(required);
      checkNotNull(optional);
      return new ImmutableValue(required, optional);
    }
  }
}

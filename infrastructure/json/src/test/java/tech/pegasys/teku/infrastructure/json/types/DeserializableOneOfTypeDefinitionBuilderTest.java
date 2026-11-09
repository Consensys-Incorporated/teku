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
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_A;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_B;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class DeserializableOneOfTypeDefinitionBuilderTest {
  public static final DeserializableOneOfTypeDefinition<OneOfTypeTestTypeDefinition.TestType>
      DESERIALIZABLE_ONE_OF_TYPE_DEFINITION =
          DeserializableOneOfTypeDefinition.object(OneOfTypeTestTypeDefinition.TestType.class)
              .description("meaningful description")
              .withType(
                  OneOfTypeTestTypeDefinition.TestObjA.IS_INSTANCE,
                  s -> s.contains("value1"),
                  TYPE_A)
              .withType(
                  OneOfTypeTestTypeDefinition.TestObjB.IS_INSTANCE,
                  s -> s.contains("value2"),
                  TYPE_B)
              .build();

  @Test
  void shouldBuildDeserializableOneOfType() throws JsonProcessingException {
    final OneOfTypeTestTypeDefinition.TestType result =
        JsonUtil.parse("{\"value1\":\"FOO\"}", DESERIALIZABLE_ONE_OF_TYPE_DEFINITION);
    assertThat(result).isInstanceOf(OneOfTypeTestTypeDefinition.TestObjA.class);
  }
}

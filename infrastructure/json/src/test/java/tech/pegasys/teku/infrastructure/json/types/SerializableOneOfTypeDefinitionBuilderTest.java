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
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.SERIALIZABLE_ONE_OF_TYPE_DEFINITION;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class SerializableOneOfTypeDefinitionBuilderTest {

  @Test
  void serialize_shouldWrite() throws JsonProcessingException {
    final String json =
        JsonUtil.serialize(
            new OneOfTypeTestTypeDefinition.TestObjA("FOO"), SERIALIZABLE_ONE_OF_TYPE_DEFINITION);
    assertThat(json).isEqualTo("{\"value1\":\"FOO\"}");
  }

  @SuppressWarnings("unchecked")
  @Test
  void openapiType() throws Exception {
    final String json =
        JsonUtil.serialize(SERIALIZABLE_ONE_OF_TYPE_DEFINITION::serializeOpenApiTypeOrReference);
    final Map<String, Object> swaggerDocumentMap = JsonTestUtil.parse(json);
    final List<Map<String, String>> oneOf =
        (ArrayList<Map<String, String>>) swaggerDocumentMap.get("oneOf");
    assertThat(swaggerDocumentMap.get("description")).isEqualTo("meaningful description");

    final List<String> refs =
        oneOf.stream().map(element -> element.get("$ref")).collect(Collectors.toList());
    assertThat(refs).containsOnly("#/components/schemas/TA", "#/components/schemas/TB");
  }
}

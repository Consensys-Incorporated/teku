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

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

class OpenApiTypeDefinitionTest {

  @Test
  void serializeOpenApiTypeOrReference_shouldSerializeAsReferenceWhenNamePresent()
      throws Exception {
    final NameableOpenApiTypeDefinition type = new NameableOpenApiTypeDefinition("foo");
    final String json = JsonUtil.serialize(type::serializeOpenApiTypeOrReference);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    assertThat(result).containsOnly(entry("$ref", "#/components/schemas/foo"));
  }

  @Test
  void serializeOpenApiTypeOrReference_shouldSerializeInlineWhenNameNotPresent() throws Exception {
    final NameableOpenApiTypeDefinition type = new NameableOpenApiTypeDefinition();
    final String json = JsonUtil.serialize(type::serializeOpenApiTypeOrReference);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    assertThat(result).containsOnly(entry("type", "custom"));
  }

  @Test
  void serializeOpenApiType_shouldSerializeInlineWhenNamePresent() throws Exception {
    final NameableOpenApiTypeDefinition type = new NameableOpenApiTypeDefinition("foo");
    final String json = JsonUtil.serialize(type::serializeOpenApiType);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    assertThat(result).containsOnly(entry("type", "custom"));
  }

  @Test
  void serializeOpenApiType_shouldSerializeInlineWhenNameNotPresent() throws Exception {
    final NameableOpenApiTypeDefinition type = new NameableOpenApiTypeDefinition();
    final String json = JsonUtil.serialize(type::serializeOpenApiType);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    assertThat(result).containsOnly(entry("type", "custom"));
  }

  private static class NameableOpenApiTypeDefinition implements OpenApiTypeDefinition {
    private final Optional<String> name;

    public NameableOpenApiTypeDefinition(final String name) {
      this.name = Optional.of(name);
    }

    public NameableOpenApiTypeDefinition() {
      this.name = Optional.empty();
    }

    @Override
    public Optional<String> getTypeName() {
      return name;
    }

    @Override
    public OpenApiTypeDefinition withDescription(final String description) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "custom");
      gen.writeEndObject();
    }
  }
}

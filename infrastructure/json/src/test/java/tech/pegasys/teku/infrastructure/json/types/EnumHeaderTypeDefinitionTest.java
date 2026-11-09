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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class EnumHeaderTypeDefinitionTest {
  final DeserializableTypeDefinition<Color> definition =
      DeserializableTypeDefinition.enumOf(Color.class);

  enum Color {
    RED,
    GREEN,
    BLUE
  }

  @Test
  void shouldSerializeEnum() throws Exception {
    final String json = JsonUtil.serialize(Color.RED, definition);
    assertThat(json).isEqualTo("\"RED\"");
  }

  @Test
  void shouldParseEnum() throws Exception {
    assertThat(JsonUtil.parse("\"BLUE\"", definition)).isEqualTo(Color.BLUE);
  }

  @Test
  public void serializeOpenApiType() throws IOException {

    final EnumHeaderTypeDefinition<Color> definition =
        new EnumHeaderTypeDefinition<>(
            Color.class,
            Color::name,
            Optional.of("color"),
            "Color",
            Optional.of(true),
            Optional.of("The color of the object"),
            Optional.of("RED"),
            Set.of());

    final String json = getJsonDefinition(definition);

    final String expectedJson =
        "{\"Color\":{\"description\":\"The color of the object\",\"required\":true,\"schema\":{\"type\":\"string\",\"enum\":[\"RED\",\"GREEN\",\"BLUE\"],\"example\":\"RED\"}}}";
    assertEquals(expectedJson, json);
  }

  @Test
  public void serializeOpenApiTypeIgnoresExcludedElements() throws IOException {
    final EnumHeaderTypeDefinition<Color> definition =
        new EnumHeaderTypeDefinition<>(
            Color.class,
            Color::name,
            Optional.of("color"),
            "Color",
            Optional.of(true),
            Optional.of("The color of the object"),
            Optional.of("RED"),
            Set.of(Color.BLUE));

    final String json = getJsonDefinition(definition);

    final String expectedJson =
        "{\"Color\":{\"description\":\"The color of the object\",\"required\":true,\"schema\":{\"type\":\"string\",\"enum\":[\"RED\",\"GREEN\"],\"example\":\"RED\"}}}";
    assertEquals(expectedJson, json);
  }

  private static String getJsonDefinition(final EnumHeaderTypeDefinition<Color> definition)
      throws IOException {
    final StringWriter writer = new StringWriter();
    final JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
    gen.writeStartObject();
    definition.serializeOpenApiType(gen);
    gen.writeEndObject();
    gen.close();
    return writer.toString();
  }
}

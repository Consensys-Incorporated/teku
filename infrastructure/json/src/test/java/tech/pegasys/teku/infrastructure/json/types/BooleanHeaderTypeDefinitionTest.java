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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class BooleanHeaderTypeDefinitionTest {

  @Test
  public void serializeOpenApiType() throws IOException {

    final BooleanHeaderTypeDefinition definition =
        new BooleanHeaderTypeDefinition("MyBoolean", Optional.of(true), "This is a boolean header");

    final StringWriter writer = new StringWriter();
    final JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);

    gen.writeStartObject();
    definition.serializeOpenApiType(gen);
    gen.writeEndObject();
    gen.close();

    final String json = writer.toString();

    final String expectedJson =
        "{\"MyBoolean\":{\"description\":\"This is a boolean header\",\"required\":true,\"schema\":{\"type\":\"boolean\"}}}";
    assertEquals(expectedJson, json);
  }
}

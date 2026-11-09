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

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Optional;

public class BooleanHeaderTypeDefinition extends BooleanTypeDefinition {
  private final String title;
  private final Optional<Boolean> required;

  public BooleanHeaderTypeDefinition(
      final String title, final Optional<Boolean> required, final String description) {
    super(description);
    this.title = title;
    this.required = required;
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeFieldName(title);
    gen.writeStartObject();

    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (required.isPresent()) {
      gen.writeBooleanField("required", required.get());
    }
    gen.writeFieldName("schema");
    gen.writeStartObject();
    gen.writeStringField("type", "boolean");
    gen.writeEndObject();

    gen.writeEndObject();
  }
}

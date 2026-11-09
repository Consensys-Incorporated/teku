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
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class BooleanTypeDefinition implements StringValueTypeDefinition<Boolean> {
  final Optional<String> description;

  public BooleanTypeDefinition() {
    this.description = Optional.empty();
  }

  public BooleanTypeDefinition(final String description) {
    this.description = Optional.of(description);
  }

  @Override
  public Boolean deserialize(final JsonParser parser) throws IOException {
    return parser.getBooleanValue();
  }

  @Override
  public StringValueTypeDefinition<Boolean> withDescription(final String description) {
    return new BooleanTypeDefinition(description);
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "boolean");
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    gen.writeEndObject();
  }

  @Override
  public void serialize(final Boolean value, final JsonGenerator gen) throws IOException {
    gen.writeBoolean(value);
  }

  @Override
  public String serializeToString(final Boolean value) {
    return Objects.toString(value, null);
  }

  @Override
  public Boolean deserializeFromString(final String value) {
    return Boolean.valueOf(value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BooleanTypeDefinition that = (BooleanTypeDefinition) o;
    return Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description);
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Optional;

public class UInt8TypeDefinition extends PrimitiveTypeDefinition<Byte> {
  private final Optional<String> description;

  public UInt8TypeDefinition() {
    this.description = Optional.empty();
  }

  public UInt8TypeDefinition(final String description) {
    this.description = Optional.of(description);
  }

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "string");
    gen.writeStringField("example", "1");
    gen.writeStringField(
        "description", description.orElse("unsigned 8 bit integer, max value 255"));
    gen.writeStringField("format", "uint8");
  }

  @Override
  public void serialize(final Byte value, final JsonGenerator gen) throws IOException {
    gen.writeString(serializeToString(value));
  }

  @Override
  public Byte deserialize(final JsonParser parser) throws IOException {
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public String serializeToString(final Byte value) {
    return value != null ? Integer.toString(Byte.toUnsignedInt(value)) : null;
  }

  @Override
  public Byte deserializeFromString(final String valueAsString) {
    final int value = Integer.parseUnsignedInt(valueAsString, 10);
    checkArgument(0 <= value && value <= 255, "Value %s is outside range for uint8", value);
    return (byte) value;
  }

  @Override
  public PrimitiveTypeDefinition<Byte> withDescription(final String description) {
    return new UInt8TypeDefinition(description);
  }
}

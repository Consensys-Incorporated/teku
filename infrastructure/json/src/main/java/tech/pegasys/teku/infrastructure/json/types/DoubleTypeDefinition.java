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

class DoubleTypeDefinition extends PrimitiveTypeDefinition<Double> {

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "number");
    gen.writeStringField("format", "double");
  }

  @Override
  public void serialize(final Double value, final JsonGenerator gen) throws IOException {
    gen.writeNumber(value);
  }

  @Override
  public Double deserialize(final JsonParser parser) throws IOException {
    return parser.getDoubleValue();
  }

  @Override
  public String serializeToString(final Double value) {
    return Objects.toString(value, null);
  }

  @Override
  public Double deserializeFromString(final String value) {
    return Double.valueOf(value);
  }
}

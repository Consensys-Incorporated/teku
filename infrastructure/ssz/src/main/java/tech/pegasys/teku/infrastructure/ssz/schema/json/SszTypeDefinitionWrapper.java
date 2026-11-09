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

package tech.pegasys.teku.infrastructure.ssz.schema.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;

public class SszTypeDefinitionWrapper<DataT, SszDataT extends SszPrimitive<DataT>>
    implements DeserializableTypeDefinition<SszDataT> {

  private final SszPrimitiveSchema<DataT, SszDataT> schema;
  private final DeserializableTypeDefinition<DataT> primitiveTypeDefinition;

  public SszTypeDefinitionWrapper(
      final SszPrimitiveSchema<DataT, SszDataT> schema,
      final DeserializableTypeDefinition<DataT> primitiveTypeDefinition) {
    this.schema = schema;
    this.primitiveTypeDefinition = primitiveTypeDefinition;
  }

  @Override
  public SszDataT deserialize(final JsonParser parser) throws IOException {
    return schema.boxed(primitiveTypeDefinition.deserialize(parser));
  }

  @Override
  public DeserializableTypeDefinition<SszDataT> withDescription(final String description) {
    return new SszTypeDefinitionWrapper<>(
        schema, primitiveTypeDefinition.withDescription(description));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    primitiveTypeDefinition.serializeOpenApiType(gen);
  }

  @Override
  public void serialize(final SszDataT value, final JsonGenerator gen) throws IOException {
    primitiveTypeDefinition.serialize(value.get(), gen);
  }

  @Override
  public Optional<String> getTypeName() {
    return primitiveTypeDefinition.getTypeName();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return primitiveTypeDefinition.getReferencedTypeDefinitions();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getSelfAndReferencedTypeDefinitions() {
    return primitiveTypeDefinition.getSelfAndReferencedTypeDefinitions();
  }

  @Override
  public void serializeOpenApiTypeOrReference(final JsonGenerator gen) throws IOException {
    primitiveTypeDefinition.serializeOpenApiTypeOrReference(gen);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SszTypeDefinitionWrapper<?, ?> that = (SszTypeDefinitionWrapper<?, ?>) o;
    return Objects.equals(schema, that.schema)
        && Objects.equals(primitiveTypeDefinition, that.primitiveTypeDefinition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schema, primitiveTypeDefinition);
  }
}

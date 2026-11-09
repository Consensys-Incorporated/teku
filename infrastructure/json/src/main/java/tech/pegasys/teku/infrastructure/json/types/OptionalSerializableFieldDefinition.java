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
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

class OptionalSerializableFieldDefinition<TObject, TField>
    implements SerializableFieldDefinition<TObject> {
  private final String name;
  private final Function<TObject, Optional<TField>> getter;
  private final SerializableTypeDefinition<TField> type;

  OptionalSerializableFieldDefinition(
      final String name,
      final Function<TObject, Optional<TField>> getter,
      final SerializableTypeDefinition<TField> type) {
    this.name = name;
    this.getter = getter;
    this.type = type;
  }

  @Override
  public void writeField(final TObject source, final JsonGenerator gen) throws IOException {
    final Optional<TField> maybeValue = getter.apply(source);
    if (maybeValue.isPresent()) {
      gen.writeFieldName(name);
      type.serialize(maybeValue.get(), gen);
    }
  }

  @Override
  public void writeOpenApiField(final JsonGenerator gen) throws IOException {
    gen.writeFieldName(name);
    type.serializeOpenApiTypeOrReference(gen);
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return type.getSelfAndReferencedTypeDefinitions();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OptionalSerializableFieldDefinition<?, ?> that =
        (OptionalSerializableFieldDefinition<?, ?>) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}

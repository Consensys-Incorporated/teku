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

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class OptionalDeserializableFieldDefinition<TObject, TBuilder, TField>
    extends OptionalSerializableFieldDefinition<TObject, TField>
    implements DeserializableFieldDefinition<TObject, TBuilder> {

  // Computed on first use. The definition is immutable, so the hash never changes; a race only
  // computes it twice
  private int cachedHashCode;
  private final BiConsumer<TBuilder, Optional<TField>> setter;
  private final DeserializableTypeDefinition<TField> deserializableType;

  OptionalDeserializableFieldDefinition(
      final String name,
      final Function<TObject, Optional<TField>> getter,
      final BiConsumer<TBuilder, Optional<TField>> setter,
      final DeserializableTypeDefinition<TField> type) {
    super(name, getter, type);
    this.setter = setter;
    this.deserializableType = type;
  }

  @Override
  public void readField(final TBuilder target, final JsonParser parser) throws IOException {
    final Optional<TField> value = Optional.ofNullable(deserializableType.deserialize(parser));
    setter.accept(target, value);
  }

  @Override
  public Optional<OptionalDeserializableFieldDefinition<TObject, TBuilder, ?>> toOptional() {
    return Optional.of(this);
  }

  public void setFieldToEmpty(final TBuilder target) {
    setter.accept(target, Optional.empty());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final OptionalDeserializableFieldDefinition<?, ?, ?> that =
        (OptionalDeserializableFieldDefinition<?, ?, ?>) o;
    return Objects.equals(deserializableType, that.deserializableType);
  }

  @Override
  public int hashCode() {
    // Cached: hashing walks the whole nested type, and building the OpenAPI docs hashes the same
    // types again and again while collecting the types each endpoint references
    int hash = cachedHashCode;
    if (hash == 0) {
      hash = Objects.hash(super.hashCode(), deserializableType);
      cachedHashCode = hash;
    }
    return hash;
  }
}

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
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

class DescribedPrimitiveTypeDefinition<T> implements StringValueTypeDefinition<T> {

  private final PrimitiveTypeDefinition<T> delegate;
  private final String description;

  DescribedPrimitiveTypeDefinition(
      final PrimitiveTypeDefinition<T> delegate, final String description) {
    this.delegate = delegate;
    this.description = description;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return delegate.deserialize(parser);
  }

  @Override
  public Optional<String> getTypeName() {
    // Clear name to ensure customised type is inlined
    return Optional.empty();
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    delegate.serializeOpenApiTypeFields(gen);
    gen.writeStringField("description", description);
    gen.writeEndObject();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return delegate.getReferencedTypeDefinitions();
  }

  @Override
  public StringValueTypeDefinition<T> withDescription(final String description) {
    return delegate.withDescription(description);
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    delegate.serialize(value, gen);
  }

  @Override
  public String serializeToString(final T value) {
    return delegate.serializeToString(value);
  }

  @Override
  public T deserializeFromString(final String value) {
    return delegate.deserializeFromString(value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DescribedPrimitiveTypeDefinition<?> that = (DescribedPrimitiveTypeDefinition<?>) o;
    return Objects.equals(delegate, that.delegate) && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(delegate, description);
  }
}

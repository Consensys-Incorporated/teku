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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;

public class ParameterMetadata<T> {
  private final String name;
  private final StringValueTypeDefinition<T> type;

  public ParameterMetadata(final String name, final StringValueTypeDefinition<T> type) {
    this.name = name;
    this.type = type;
  }

  public ParameterMetadata<T> withDescription(final String typeDescription) {
    return new ParameterMetadata<>(name, type.withDescription(typeDescription));
  }

  public String getName() {
    return name;
  }

  public StringValueTypeDefinition<T> getType() {
    return type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ParameterMetadata<?> that = (ParameterMetadata<?>) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}

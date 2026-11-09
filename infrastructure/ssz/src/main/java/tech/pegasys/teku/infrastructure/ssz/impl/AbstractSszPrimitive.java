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

package tech.pegasys.teku.infrastructure.ssz.impl;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszPrimitive<C> implements SszPrimitive<C> {
  private final AbstractSszPrimitiveSchema<C, ?> schema;
  private final C value;

  protected AbstractSszPrimitive(final C value, final AbstractSszPrimitiveSchema<C, ?> schema) {
    this.schema = schema;
    this.value = value;
  }

  @Override
  public C get() {
    return value;
  }

  @Override
  public AbstractSszPrimitiveSchema<C, ?> getSchema() {
    return schema;
  }

  @Override
  public TreeNode getBackingNode() {
    return getSchema().createBackingNode(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszPrimitive<?> that = (AbstractSszPrimitive<?>) o;
    return Objects.equals(get(), that.get());
  }

  @Override
  public int hashCode() {
    return Objects.hash(get());
  }

  @Override
  public String toString() {
    return get().toString();
  }
}

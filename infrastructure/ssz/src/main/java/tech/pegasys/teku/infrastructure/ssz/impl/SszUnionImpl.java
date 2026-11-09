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

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszUnion;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszUnionSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszUnionImpl implements SszUnion {

  private final SszUnionSchemaImpl<?> schema;
  private final TreeNode backingNode;
  private final Supplier<SszData> value = Suppliers.memoize(this::createValue);
  private final Supplier<Integer> selector = Suppliers.memoize(this::createSelector);

  public SszUnionImpl(final SszUnionSchemaImpl<?> schema, final TreeNode backingNode) {
    this.schema = schema;
    this.backingNode = backingNode;
  }

  @Override
  public int getSelector() {
    return selector.get();
  }

  @Override
  public SszUnionSchemaImpl<?> getSchema() {
    return schema;
  }

  @Override
  public SszData getValue() {
    return value.get();
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  private SszData createValue() {
    SszSchema<?> valueSchema = getSchema().getChildSchema(getSelector());
    TreeNode valueNode = getSchema().getValueNode(getBackingNode());
    return valueSchema.createFromBackingNode(valueNode);
  }

  private int createSelector() {
    return getSchema().getSelector(getBackingNode());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof final SszUnion sszUnion) {
      return Objects.equal(getSchema(), sszUnion.getSchema())
          && getSelector() == sszUnion.getSelector()
          && Objects.equal(hashTreeRoot(), sszUnion.hashTreeRoot());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getSchema(), getSelector(), hashTreeRoot());
  }

  @Override
  public String toString() {
    return "SszUnion{" + "selector=" + getSelector() + ", value=" + getValue() + '}';
  }
}

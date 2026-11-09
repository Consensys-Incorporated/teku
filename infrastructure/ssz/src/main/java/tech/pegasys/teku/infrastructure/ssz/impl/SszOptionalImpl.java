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
import com.google.common.base.Suppliers;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/** SszOptional according to the <a href="https://eips.ethereum.org/EIPS/eip-6475">EIP-6475</a> */
public class SszOptionalImpl<ElementDataT extends SszData> implements SszOptional<ElementDataT> {

  private final SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> schema;
  private final TreeNode backingNode;
  private final Supplier<Optional<ElementDataT>> value = Suppliers.memoize(this::createValue);

  public SszOptionalImpl(
      final SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> schema,
      final TreeNode backingNode) {
    this.schema = schema;
    this.backingNode = backingNode;
  }

  @Override
  public SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> getSchema() {
    return schema;
  }

  @Override
  public Optional<ElementDataT> getValue() {
    return value.get();
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  private Optional<ElementDataT> createValue() {
    if (!getSchema().isPresent(getBackingNode())) {
      return Optional.empty();
    }
    final SszSchema<ElementDataT> valueSchema = getSchema().getChildSchema();
    final TreeNode valueNode = getSchema().getValueNode(getBackingNode());
    return Optional.of(valueSchema.createFromBackingNode(valueNode));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszOptional<?> sszOptional)) {
      return false;
    }
    return Objects.equal(getSchema(), sszOptional.getSchema())
        && Objects.equal(hashTreeRoot(), sszOptional.hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getSchema(), hashTreeRoot());
  }

  @Override
  public String toString() {
    return "SszOptional{value=" + getValue() + '}';
  }
}

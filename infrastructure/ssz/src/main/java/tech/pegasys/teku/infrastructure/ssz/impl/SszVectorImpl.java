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

import java.util.function.Supplier;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszVectorImpl<SszElementT extends SszData> extends AbstractSszCollection<SszElementT>
    implements SszVector<SszElementT> {

  public SszVectorImpl(
      final SszCompositeSchema<?> schema, final Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  public SszVectorImpl(final SszCompositeSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszVectorImpl(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  protected int sizeImpl() {
    return (int) Long.min(Integer.MAX_VALUE, this.getSchema().getMaxLength());
  }

  @Override
  public SszMutableVector<SszElementT> createWritableCopy() {
    return new SszMutableVectorImpl<>(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszVectorSchema<SszElementT, ?> getSchema() {
    return (SszVectorSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  protected IntCache<SszElementT> createCache() {
    return size() > 16384 ? new ArrayIntCache<>() : new ArrayIntCache<>(size());
  }

  @Override
  protected void checkIndex(final int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  public String toString() {
    return "SszVector{" + stream().map(Object::toString).collect(Collectors.joining(", ")) + "}";
  }
}

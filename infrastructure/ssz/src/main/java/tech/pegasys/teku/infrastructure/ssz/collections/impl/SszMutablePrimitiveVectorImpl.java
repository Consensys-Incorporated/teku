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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszMutablePrimitiveVectorImpl<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends AbstractSszMutablePrimitiveCollection<ElementT, SszElementT>
    implements SszMutablePrimitiveVector<ElementT, SszElementT> {

  @SuppressWarnings("unchecked")
  public SszMutablePrimitiveVectorImpl(
      final AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveVectorSchema<ElementT, SszElementT, ?> getSchema() {
    return (SszPrimitiveVectorSchema<ElementT, SszElementT, ?>) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveVector<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveVector<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  protected AbstractSszComposite<SszElementT> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszElementT> childrenCache) {
    return new SszPrimitiveVectorImpl<>(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszMutablePrimitiveVector<ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}

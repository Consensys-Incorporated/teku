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

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.impl.SszVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveVectorImpl<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends SszVectorImpl<SszElementT> implements SszPrimitiveVector<ElementT, SszElementT> {

  SszPrimitiveVectorImpl(
      final SszCompositeSchema<?> schema, final Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  SszPrimitiveVectorImpl(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  public SszPrimitiveVectorImpl(final SszCompositeSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  @Override
  public SszMutablePrimitiveVector<ElementT, SszElementT> createWritableCopy() {
    return new SszMutablePrimitiveVectorImpl<>(this);
  }
}

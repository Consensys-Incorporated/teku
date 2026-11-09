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
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszCollection<SszElementT extends SszData>
    extends AbstractSszComposite<SszElementT> implements SszCollection<SszElementT> {

  protected AbstractSszCollection(
      final SszCompositeSchema<?> schema, final Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  protected AbstractSszCollection(final SszCompositeSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected AbstractSszCollection(
      final SszCompositeSchema<?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszCollectionSchema<SszElementT, ?> getSchema() {
    return (SszCollectionSchema<SszElementT, ?>) super.getSchema();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected SszElementT getImpl(final int index) {
    SszCollectionSchema<SszElementT, ?> type = this.getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    if (elementType.isPrimitive()) {
      // several primitive values could be packed to a single leaf node
      SszPrimitiveSchema<?, ?> primitiveElementType = (SszPrimitiveSchema<?, ?>) elementType;
      TreeNode node =
          getBackingNode().get(type.getChildGeneralizedIndex(index / type.getElementsPerChunk()));
      return (SszElementT)
          primitiveElementType.createFromPackedNode(node, index % type.getElementsPerChunk());
    } else {
      TreeNode node = getBackingNode().get(type.getChildGeneralizedIndex(index));
      return (SszElementT) elementType.createFromBackingNode(node);
    }
  }
}

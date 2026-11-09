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
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.CachingTreeAccessor;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveListImpl<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends SszListImpl<SszElementT> implements SszPrimitiveList<ElementT, SszElementT> {

  protected final int elementsPerChunk;
  protected final SszPrimitiveSchema<ElementT, SszElementT> elementType;
  private final CachingTreeAccessor cachingTreeAccessor;

  @SuppressWarnings("unchecked")
  public SszPrimitiveListImpl(
      final SszListSchema<SszElementT, ?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListImpl(
      final SszListSchema<SszElementT, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
  }

  @Override
  protected SszElementT getImpl(final int index) {
    return elementType.createFromPackedNode(getTreeNode(index), index % elementsPerChunk);
  }

  protected TreeNode getTreeNode(final int index) {
    int nodeIndex = index / elementsPerChunk;
    return cachingTreeAccessor.getNodeByVectorIndex(nodeIndex);
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    return new SszMutablePrimitiveListImpl<>(this);
  }
}

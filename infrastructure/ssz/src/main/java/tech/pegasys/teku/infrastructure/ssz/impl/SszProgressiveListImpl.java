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

import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.AbstractSszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.CachingTreeAccessor;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Immutable implementation of SszList backed by a progressive merkle tree. The backing tree has the
 * structure: BranchNode(progressiveDataTree, lengthNode)
 *
 * <p>Uses {@link CachingTreeAccessor} to optimize sequential element access. For primitive types,
 * consecutive elements within the same 32-byte chunk hit the cache, avoiding redundant tree
 * traversals through the progressive tree's right spine.
 */
public class SszProgressiveListImpl<SszElementT extends SszData>
    extends AbstractSszCollection<SszElementT> implements SszList<SszElementT> {

  private final CachingTreeAccessor cachingTreeAccessor;

  public SszProgressiveListImpl(
      final AbstractSszProgressiveListSchema<SszElementT, ?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
  }

  public SszProgressiveListImpl(
      final AbstractSszProgressiveListSchema<SszElementT, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszElementT, ?> getSchema() {
    return (SszListSchema<SszElementT, ?>) super.getSchema();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected SszElementT getImpl(final int index) {
    final SszSchema<?> elementType = getSchema().getElementSchema();
    if (elementType.isPrimitive()) {
      final int elementsPerChunk = getSchema().getElementsPerChunk();
      final TreeNode node = cachingTreeAccessor.getNodeByVectorIndex(index / elementsPerChunk);
      return (SszElementT)
          ((SszPrimitiveSchema<?, ?>) elementType)
              .createFromPackedNode(node, index % elementsPerChunk);
    } else {
      final TreeNode node = cachingTreeAccessor.getNodeByVectorIndex(index);
      return (SszElementT) elementType.createFromBackingNode(node);
    }
  }

  @Override
  protected int sizeImpl() {
    return SszPrimitiveSchemas.UINT64_SCHEMA.createFromBackingNode(getSizeNode()).get().intValue();
  }

  private TreeNode getSizeNode() {
    return getBackingNode().get(GIndexUtil.RIGHT_CHILD_G_INDEX);
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    return new SszMutableProgressiveListImpl<>(this);
  }

  @Override
  public boolean isWritableSupported() {
    return true;
  }

  @Override
  protected void checkIndex(final int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive list with size " + size());
    }
  }

  @Override
  public String toString() {
    final int maxToDisplay = 1024;
    String elements =
        stream().limit(maxToDisplay).map(Object::toString).collect(Collectors.joining(", "));
    if (size() > maxToDisplay) {
      elements += " ... more " + (size() - maxToDisplay) + " elements";
    }
    return "SszProgressiveList{size=" + size() + ": " + elements + "}";
  }
}

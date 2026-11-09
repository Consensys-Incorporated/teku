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

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefList;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszMutableListImpl<SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableCollection<SszElementT, SszMutableElementT>
    implements SszMutableRefList<SszElementT, SszMutableElementT> {

  private int cachedSize;
  private final long cachedMaxLength;

  public SszMutableListImpl(final SszListImpl<SszElementT> backingImmutableList) {
    super(backingImmutableList);
    cachedSize = backingImmutableList.size();
    cachedMaxLength = getSchema().getMaxLength();
  }

  @Override
  protected SszListImpl<SszElementT> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszElementT> childrenCache) {
    return new SszListImpl<>(getSchema(), backingNode, childrenCache);
  }

  @Override
  protected TreeNode doFinalTreeUpdates(final TreeNode updatedTree) {
    return updateSize(updatedTree);
  }

  @Override
  public int size() {
    return cachedSize;
  }

  private TreeNode updateSize(final TreeNode root) {
    return BranchNode.create(root.get(GIndexUtil.LEFT_CHILD_G_INDEX), createSizeNode());
  }

  private TreeNode createSizeNode() {
    return SszUInt64.of(UInt64.fromLongBits(size())).getBackingNode();
  }

  @Override
  public void set(final int index, final SszElementT value) {
    super.set(index, value);
    if (index == size()) {
      cachedSize++;
    }
  }

  @Override
  public void clear() {
    super.clear();
    cachedSize = 0;
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index < 0
        || (!set && index >= size())
        || (set && (index > size() || index >= cachedMaxLength))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszElementT, ?> getSchema() {
    return (SszListSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszList<SszElementT> commitChanges() {
    return (SszList<SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}

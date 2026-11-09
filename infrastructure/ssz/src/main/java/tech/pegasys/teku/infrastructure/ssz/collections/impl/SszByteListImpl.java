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

import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszByteListImpl extends SszPrimitiveListImpl<Byte, SszByte> implements SszByteList {

  public SszByteListImpl(final SszByteListSchema<?> schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  @Override
  public Byte getElement(final int index) {
    return elementType.createFromPackedNodeUnboxed(getTreeNode(index), index % elementsPerChunk);
  }

  @Override
  protected IntCache<SszByte> createCache() {
    // caching with Bytes in this class
    return IntCache.noop();
  }

  @Override
  public SszByteListSchema<?> getSchema() {
    return (SszByteListSchema<?>) super.getSchema();
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszByteList{" + getBytes() + '}';
  }
}

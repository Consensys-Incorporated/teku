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
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Immutable byte list backed by a progressive merkle tree (EIP-7916). Element access is delegated
 * to the inherited packed-primitive access of {@link SszProgressiveListImpl}, which routes through
 * the schema's progressive generalized indices.
 */
public class SszProgressiveByteListImpl extends SszProgressiveListImpl<SszByte>
    implements SszByteList {

  public SszProgressiveByteListImpl(
      final SszProgressiveByteListSchema<?> schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public SszProgressiveByteListImpl(
      final SszProgressiveByteListSchema<?> schema,
      final TreeNode backingTree,
      final IntCache<SszByte> cache) {
    super(schema, backingTree, cache);
  }

  @Override
  public Byte getElement(final int index) {
    return get(index).get();
  }

  @Override
  public SszProgressiveByteListSchema<?> getSchema() {
    return (SszProgressiveByteListSchema<?>) super.getSchema();
  }

  @Override
  public SszMutablePrimitiveList<Byte, SszByte> createWritableCopy() {
    return new SszMutableProgressiveByteListImpl(this);
  }

  @Override
  public boolean isWritableSupported() {
    return true;
  }

  @Override
  public String toString() {
    return "SszProgressiveByteList{" + getBytes() + '}';
  }
}

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
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.impl.SszMutableProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Mutable progressive list that additionally implements {@link SszMutableUInt64List}. All
 * UInt64-specific default methods ({@code setElement}, {@code appendElement}, {@code
 * getPrimitiveElementSchema}) delegate to {@code set()}/{@code append()}/{@code
 * getSchema().getElementSchema()} which already work correctly from the parent.
 */
public class SszMutableProgressiveUInt64ListImpl
    extends SszMutableProgressiveListImpl<SszUInt64, SszUInt64> implements SszMutableUInt64List {

  public SszMutableProgressiveUInt64ListImpl(
      final SszProgressiveUInt64ListImpl backingImmutableList) {
    super(backingImmutableList);
  }

  @Override
  public SszUInt64List commitChanges() {
    return (SszUInt64List) super.commitChanges();
  }

  @Override
  protected SszProgressiveUInt64ListImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszUInt64> childrenCache) {
    return new SszProgressiveUInt64ListImpl(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszMutableUInt64List createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}

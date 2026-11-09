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
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.AbstractSszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Immutable progressive list that additionally implements {@link SszUInt64List}. All
 * UInt64-specific default methods ({@code getElement}, {@code asListUnboxed}, {@code
 * streamUnboxed}) delegate to {@code get(index)} which is already handled efficiently by the
 * parent's packed primitive access via {@link
 * tech.pegasys.teku.infrastructure.ssz.tree.CachingTreeAccessor}.
 */
public class SszProgressiveUInt64ListImpl extends SszProgressiveListImpl<SszUInt64>
    implements SszUInt64List {

  public SszProgressiveUInt64ListImpl(
      final AbstractSszProgressiveListSchema<SszUInt64, ?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszProgressiveUInt64ListImpl(
      final AbstractSszProgressiveListSchema<SszUInt64, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszUInt64> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableUInt64List createWritableCopy() {
    return new SszMutableProgressiveUInt64ListImpl(this);
  }
}

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
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszMutableUInt64ListImpl extends SszMutablePrimitiveListImpl<UInt64, SszUInt64>
    implements SszMutableUInt64List {

  public SszMutableUInt64ListImpl(final SszUInt64ListImpl backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszUInt64List commitChanges() {
    return (SszUInt64List) super.commitChanges();
  }

  @Override
  protected SszUInt64ListImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszUInt64> childrenCache) {
    return new SszUInt64ListImpl(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszUInt64ListSchema<?> getSchema() {
    return (SszUInt64ListSchema<?>) super.getSchema();
  }

  @Override
  public SszMutableUInt64ListImpl createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}

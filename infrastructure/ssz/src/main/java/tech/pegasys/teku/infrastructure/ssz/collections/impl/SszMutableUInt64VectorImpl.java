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
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszMutableUInt64VectorImpl extends SszMutablePrimitiveVectorImpl<UInt64, SszUInt64>
    implements SszMutableUInt64Vector {

  public SszMutableUInt64VectorImpl(final SszUInt64VectorImpl backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszUInt64Vector commitChanges() {
    return (SszUInt64Vector) super.commitChanges();
  }

  @Override
  protected SszUInt64VectorImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszUInt64> childrenCache) {
    return new SszUInt64VectorImpl(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszUInt64VectorSchema<?> getSchema() {
    return (SszUInt64VectorSchema<?>) super.getSchema();
  }

  @Override
  public SszMutableUInt64VectorImpl createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}

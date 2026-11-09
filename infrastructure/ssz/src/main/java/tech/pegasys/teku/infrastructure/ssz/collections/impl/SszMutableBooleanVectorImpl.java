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
import tech.pegasys.teku.infrastructure.ssz.collections.SszBooleanVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBooleanVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBooleanVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszMutableBooleanVectorImpl extends SszMutablePrimitiveVectorImpl<Boolean, SszBoolean>
    implements SszMutableBooleanVector {

  public SszMutableBooleanVectorImpl(final SszBooleanVectorImpl backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszBooleanVector commitChanges() {
    return (SszBooleanVector) super.commitChanges();
  }

  @Override
  protected SszBooleanVectorImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszBoolean> childrenCache) {
    return new SszBooleanVectorImpl(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszBooleanVectorSchema<?> getSchema() {
    return (SszBooleanVectorSchema<?>) super.getSchema();
  }

  @Override
  public SszMutableBooleanVectorImpl createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}

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
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBooleanVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBooleanVectorImpl extends SszPrimitiveVectorImpl<Boolean, SszBoolean>
    implements SszBooleanVector {

  public SszBooleanVectorImpl(final SszBooleanVectorSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    validate();
  }

  /**
   * Iterate over all elements to ensure it's correct. Incorrect element will throw. Our SSZ
   * implementation is lazy, and we need to read elements explicitly to check if it's correct.
   */
  private void validate() {
    this.stream().forEach(__ -> {});
  }

  SszBooleanVectorImpl(
      final SszVectorSchema<SszBoolean, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszBoolean> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableBooleanVector createWritableCopy() {
    return new SszMutableBooleanVectorImpl(this);
  }
}

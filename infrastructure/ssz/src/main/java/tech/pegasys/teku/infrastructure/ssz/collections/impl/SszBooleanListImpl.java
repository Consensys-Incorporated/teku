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
import tech.pegasys.teku.infrastructure.ssz.collections.SszBooleanList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBooleanList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBooleanListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBooleanListImpl extends SszPrimitiveListImpl<Boolean, SszBoolean>
    implements SszBooleanList {

  public SszBooleanListImpl(final SszBooleanListSchema<?> schema, final TreeNode backingNode) {
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

  SszBooleanListImpl(
      final SszListSchema<SszBoolean, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszBoolean> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableBooleanList createWritableCopy() {
    return new SszMutableBooleanListImpl(this);
  }
}

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
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszUInt64VectorImpl extends SszPrimitiveVectorImpl<UInt64, SszUInt64>
    implements SszUInt64Vector {

  public SszUInt64VectorImpl(final SszUInt64VectorSchema<?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  SszUInt64VectorImpl(
      final SszVectorSchema<SszUInt64, ?> schema,
      final TreeNode backingNode,
      final IntCache<SszUInt64> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableUInt64Vector createWritableCopy() {
    return new SszMutableUInt64VectorImpl(this);
  }
}

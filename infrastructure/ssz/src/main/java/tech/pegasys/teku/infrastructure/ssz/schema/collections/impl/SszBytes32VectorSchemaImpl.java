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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBytes32VectorImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBytes32VectorSchemaImpl<SszVectorT extends SszBytes32Vector>
    extends AbstractSszVectorSchema<SszBytes32, SszVectorT>
    implements SszBytes32VectorSchema<SszVectorT> {

  public SszBytes32VectorSchemaImpl(final long vectorLength) {
    super(SszPrimitiveSchemas.BYTES32_SCHEMA, vectorLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT createFromBackingNode(final TreeNode node) {
    return (SszVectorT) new SszBytes32VectorImpl(this, node);
  }
}

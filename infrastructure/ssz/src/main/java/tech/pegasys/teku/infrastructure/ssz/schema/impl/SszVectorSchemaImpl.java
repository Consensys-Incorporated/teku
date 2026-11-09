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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.impl.SszVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszVectorSchemaImpl<SszElementT extends SszData>
    extends AbstractSszVectorSchema<SszElementT, SszVector<SszElementT>> {

  SszVectorSchemaImpl(final SszSchema<SszElementT> elementType, final long vectorLength) {
    this(elementType, vectorLength, false, SszSchemaHints.none());
  }

  public SszVectorSchemaImpl(
      final SszSchema<SszElementT> elementSchema,
      final long vectorLength,
      final boolean isListBacking,
      final SszSchemaHints hints) {
    super(elementSchema, vectorLength, isListBacking, hints);
  }

  @Override
  public SszVector<SszElementT> createFromBackingNode(final TreeNode node) {
    return new SszVectorImpl<>(this, node);
  }
}

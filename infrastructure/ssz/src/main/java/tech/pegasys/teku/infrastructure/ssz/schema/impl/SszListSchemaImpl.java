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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszListSchemaImpl<ElementDataT extends SszData>
    extends AbstractSszListSchema<ElementDataT, SszList<ElementDataT>> {

  public SszListSchemaImpl(
      final SszSchema<ElementDataT> elementSchema,
      final long maxLength,
      final SszSchemaHints hints) {
    super(elementSchema, maxLength, hints);
  }

  @Override
  public SszList<ElementDataT> createFromBackingNode(final TreeNode node) {
    return new SszListImpl<>(this, node);
  }
}

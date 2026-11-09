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

package tech.pegasys.teku.spec.datastructures.state.versions.electra;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class PendingConsolidation extends Container2<PendingConsolidation, SszUInt64, SszUInt64> {
  protected PendingConsolidation(
      final ContainerSchema2<PendingConsolidation, SszUInt64, SszUInt64> schema) {
    super(schema);
  }

  public PendingConsolidation(
      final PendingConsolidationSchema pendingConsolidationSchema,
      final SszUInt64 sourceIndex,
      final SszUInt64 targetIndex) {
    super(pendingConsolidationSchema, sourceIndex, targetIndex);
  }

  public static class PendingConsolidationSchema
      extends ContainerSchema2<PendingConsolidation, SszUInt64, SszUInt64> {
    public PendingConsolidationSchema() {
      super(
          "PendingConsolidation",
          namedSchema("source_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("target_index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public PendingConsolidation createFromBackingNode(final TreeNode node) {
      return new PendingConsolidation(this, node);
    }

    public PendingConsolidation create(final SszUInt64 sourceIndex, final SszUInt64 targetIndex) {
      return new PendingConsolidation(this, sourceIndex, targetIndex);
    }
  }

  private PendingConsolidation(final PendingConsolidationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public int getSourceIndex() {
    return ((SszUInt64) get(0)).get().intValue();
  }

  public int getTargetIndex() {
    return ((SszUInt64) get(1)).get().intValue();
  }
}

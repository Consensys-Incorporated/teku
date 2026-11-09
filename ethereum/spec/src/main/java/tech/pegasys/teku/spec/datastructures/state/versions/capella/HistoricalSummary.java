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

package tech.pegasys.teku.spec.datastructures.state.versions.capella;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class HistoricalSummary extends Container2<HistoricalSummary, SszBytes32, SszBytes32> {
  public static class HistoricalSummarySchema
      extends ContainerSchema2<HistoricalSummary, SszBytes32, SszBytes32> {

    public HistoricalSummarySchema() {
      super(
          "HistoricalSummary",
          namedSchema("block_summary_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("state_summary_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public HistoricalSummary createFromBackingNode(final TreeNode node) {
      return new HistoricalSummary(this, node);
    }

    public HistoricalSummary create(
        final SszBytes32 blockSummaryRoot, final SszBytes32 stateSummaryRoot) {
      return new HistoricalSummary(this, blockSummaryRoot, stateSummaryRoot);
    }

    public SszBytes32 getBlockSummaryRootSchema() {
      return (SszBytes32) getFieldSchema0();
    }

    public SszBytes32 getStateSummaryRootSchema() {
      return (SszBytes32) getFieldSchema1();
    }
  }

  private HistoricalSummary(final HistoricalSummarySchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private HistoricalSummary(
      final HistoricalSummarySchema type,
      final SszBytes32 blockSummaryRoot,
      final SszBytes32 stateSummaryRoot) {
    super(type, blockSummaryRoot, stateSummaryRoot);
  }

  public SszBytes32 getBlockSummaryRoot() {
    return getField0();
  }

  public SszBytes32 getStateSummaryRoot() {
    return getField1();
  }
}

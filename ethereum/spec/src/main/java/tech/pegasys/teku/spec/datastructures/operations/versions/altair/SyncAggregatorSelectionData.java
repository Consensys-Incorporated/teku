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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncAggregatorSelectionData
    extends Container2<SyncAggregatorSelectionData, SszUInt64, SszUInt64> {

  protected SyncAggregatorSelectionData(
      final SyncAggregatorSelectionDataSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected SyncAggregatorSelectionData(
      final SyncAggregatorSelectionDataSchema schema,
      final SszUInt64 slot,
      final SszUInt64 subcommitteeIndex) {
    super(schema, slot, subcommitteeIndex);
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public UInt64 getSubcommitteeIndex() {
    return getField1().get();
  }

  @Override
  public SyncAggregatorSelectionDataSchema getSchema() {
    return (SyncAggregatorSelectionDataSchema) super.getSchema();
  }
}

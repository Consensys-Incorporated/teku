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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class DataColumnSidecarsByRangeRequestMessage
    extends Container3<DataColumnSidecarsByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64List>
    implements RpcRequest {

  public static class DataColumnSidecarsByRangeRequestMessageSchema
      extends ContainerSchema3<
          DataColumnSidecarsByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64List> {

    public DataColumnSidecarsByRangeRequestMessageSchema(final SpecConfigFulu specConfig) {
      super(
          "DataColumnSidecarsByRangeRequestMessage",
          namedSchema("start_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("columns", SszUInt64ListSchema.create(specConfig.getNumberOfColumns())));
    }

    @Override
    public DataColumnSidecarsByRangeRequestMessage createFromBackingNode(final TreeNode node) {
      return new DataColumnSidecarsByRangeRequestMessage(this, node);
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<SszUInt64, ?> getColumnsSchema() {
      return (SszListSchema<SszUInt64, ?>) getFieldSchema2();
    }

    public DataColumnSidecarsByRangeRequestMessage create(
        final UInt64 startSlot, final UInt64 count, final List<UInt64> columns) {
      return new DataColumnSidecarsByRangeRequestMessage(
          this,
          SszUInt64.of(startSlot),
          SszUInt64.of(count),
          (SszUInt64List)
              this.getColumnsSchema()
                  .createFromElements(columns.stream().map(SszUInt64::of).toList()));
    }
  }

  private DataColumnSidecarsByRangeRequestMessage(
      final DataColumnSidecarsByRangeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public DataColumnSidecarsByRangeRequestMessage(
      final DataColumnSidecarsByRangeRequestMessageSchema type,
      final SszUInt64 startSlot,
      final SszUInt64 count,
      final SszUInt64List columns) {
    super(type, startSlot, count, columns);
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getMaxSlot() {
    return getStartSlot().plus(getCount()).minusMinZero(1);
  }

  public List<UInt64> getColumns() {
    return getField2().stream().map(AbstractSszPrimitive::get).toList();
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCount().intValue() * getColumns().size();
  }
}

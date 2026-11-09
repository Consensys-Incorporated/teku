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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class DataColumnsByRootIdentifierSchema
    extends ContainerSchema2<DataColumnsByRootIdentifier, SszBytes32, SszUInt64List> {

  public DataColumnsByRootIdentifierSchema(final SpecConfigFulu specConfig) {
    super(
        "DataColumnIdentifier",
        namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("columns", SszUInt64ListSchema.create(specConfig.getNumberOfColumns())));
  }

  public DataColumnsByRootIdentifier create(final Bytes32 root, final UInt64 index) {
    return create(root, List.of(index));
  }

  public DataColumnsByRootIdentifier create(final Bytes32 root, final List<UInt64> indices) {
    return new DataColumnsByRootIdentifier(root, indices, this);
  }

  @Override
  public DataColumnsByRootIdentifier createFromBackingNode(final TreeNode node) {
    return new DataColumnsByRootIdentifier(node, this);
  }

  public SszUInt64ListSchema<SszUInt64List> getColumnsSchema() {
    return (SszUInt64ListSchema<SszUInt64List>) getFieldSchema1();
  }
}

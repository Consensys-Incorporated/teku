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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;

public class DataColumnsByRootIdentifier
    extends Container2<DataColumnsByRootIdentifier, SszBytes32, SszUInt64List> {

  public static DataColumnsByRootIdentifier createFromSidecar(
      final DataColumnSidecar sidecar, final DataColumnsByRootIdentifierSchema schema) {
    return new DataColumnsByRootIdentifier(
        sidecar.getBeaconBlockRoot(), List.of(sidecar.getIndex()), schema);
  }

  DataColumnsByRootIdentifier(final TreeNode node, final DataColumnsByRootIdentifierSchema schema) {
    super(schema, node);
  }

  public DataColumnsByRootIdentifier(
      final Bytes32 root, final UInt64 index, final DataColumnsByRootIdentifierSchema schema) {
    this(root, List.of(index), schema);
  }

  public DataColumnsByRootIdentifier(
      final Bytes32 root,
      final List<UInt64> indices,
      final DataColumnsByRootIdentifierSchema schema) {
    super(
        schema,
        SszBytes32.of(root),
        schema.getColumnsSchema().createFromElements(indices.stream().map(SszUInt64::of).toList()));
  }

  public Bytes32 getBlockRoot() {
    return getField0().get();
  }

  public List<UInt64> getColumns() {
    return getField1().asListUnboxed();
  }
}

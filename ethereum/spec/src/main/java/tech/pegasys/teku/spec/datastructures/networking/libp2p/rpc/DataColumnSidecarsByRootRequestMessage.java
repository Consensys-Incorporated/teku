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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class DataColumnSidecarsByRootRequestMessage extends SszListImpl<DataColumnsByRootIdentifier>
    implements SszList<DataColumnsByRootIdentifier>, RpcRequest {

  public DataColumnSidecarsByRootRequestMessage(
      final DataColumnSidecarsByRootRequestMessageSchema schema,
      final List<DataColumnsByRootIdentifier> dataColumnsByRootIdentifiers) {
    super(schema, schema.createTreeFromElements(dataColumnsByRootIdentifiers));
  }

  DataColumnSidecarsByRootRequestMessage(
      final DataColumnSidecarsByRootRequestMessageSchema schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public int getMaximumResponseChunks() {
    return stream()
        .reduce(
            0,
            (current, dataColumnsByRootIdentifier) ->
                current + dataColumnsByRootIdentifier.getColumns().size(),
            Integer::sum);
  }

  @Override
  public String toString() {
    return "DataColumnSidecarsByRootRequestMessage{" + super.toString() + "}";
  }
}

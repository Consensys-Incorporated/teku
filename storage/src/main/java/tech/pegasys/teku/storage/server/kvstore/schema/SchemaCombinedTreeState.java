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

package tech.pegasys.teku.storage.server.kvstore.schema;

import java.util.Collection;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SchemaCombinedTreeState extends SchemaCombined {

  @Override
  Map<String, KvStoreColumn<?, ?>> getColumnMap();

  @Override
  Map<String, KvStoreVariable<?>> getVariableMap();

  @Override
  default Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  default Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  KvStoreColumn<UInt64, Bytes32> getColumnFinalizedStateRootsBySlot();

  KvStoreColumn<Bytes32, Bytes> getColumnFinalizedStateMerkleTreeLeaves();

  KvStoreColumn<Bytes32, CompressedBranchInfo> getColumnFinalizedStateMerkleTreeBranches();
}

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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;

class KvStoreTreeNodeSource implements TreeNodeSource {

  private final KvStoreAccessor db;
  private final SchemaCombinedTreeState schema;

  public KvStoreTreeNodeSource(final KvStoreAccessor db, final SchemaCombinedTreeState schema) {
    this.db = db;
    this.schema = schema;
  }

  @Override
  public CompressedBranchInfo loadBranchNode(final Bytes32 rootHash, final long gIndex) {
    return db.get(schema.getColumnFinalizedStateMerkleTreeBranches(), rootHash)
        .orElseThrow(
            () ->
                new IllegalArgumentException("Unknown branch node: " + rootHash + " at " + gIndex));
  }

  @Override
  public Bytes loadLeafNode(final Bytes32 rootHash, final long gIndex) {
    return db.get(schema.getColumnFinalizedStateMerkleTreeLeaves(), rootHash).orElse(rootHash);
  }
}

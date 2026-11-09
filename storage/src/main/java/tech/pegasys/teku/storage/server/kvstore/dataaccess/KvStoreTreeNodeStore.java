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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;

public class KvStoreTreeNodeStore implements TreeNodeStore {

  private final Set<Bytes32> knownStoredBranchesCache;
  private final Set<Bytes32> newlyStoredBranches = new HashSet<>();
  private final KvStoreAccessor db;
  private final KvStoreTransaction transaction;
  private final SchemaCombinedTreeState schema;

  private int storedBranchNodes = 0;
  private int skippedBranchNodes = 0;
  private int storedLeafNodes = 0;

  public KvStoreTreeNodeStore(
      final Set<Bytes32> knownStoredBranchesCache,
      final KvStoreAccessor db,
      final KvStoreTransaction transaction,
      final SchemaCombinedTreeState schema) {
    this.knownStoredBranchesCache = knownStoredBranchesCache;
    this.db = db;
    this.transaction = transaction;
    this.schema = schema;
  }

  @Override
  public boolean canSkipBranch(final Bytes32 root, final long gIndex) {
    if (newlyStoredBranches.contains(root) || knownStoredBranchesCache.contains(root)) {
      skippedBranchNodes++;
      return true;
    }
    if (db.get(schema.getColumnFinalizedStateMerkleTreeBranches(), root).isPresent()) {
      knownStoredBranchesCache.add(root);
      skippedBranchNodes++;
      return true;
    }
    return false;
  }

  @Override
  public void storeBranchNode(
      final Bytes32 root, final long gIndex, final int depth, final Bytes32[] children) {
    if (knownStoredBranchesCache.contains(root) || !newlyStoredBranches.add(root)) {
      return;
    }
    storedBranchNodes++;
    transaction.put(
        schema.getColumnFinalizedStateMerkleTreeBranches(),
        root,
        new CompressedBranchInfo(depth, children));
  }

  @Override
  public void storeLeafNode(final TreeNode treeNode, final long gIndex) {
    checkArgument(treeNode instanceof LeafDataNode, "Can't store a non-leaf node");
    final LeafDataNode node = (LeafDataNode) treeNode;
    if (node.getData().size() > Bytes32.SIZE && !node.hashTreeRoot().isZero()) {
      storedLeafNodes++;
      transaction.put(
          schema.getColumnFinalizedStateMerkleTreeLeaves(), node.hashTreeRoot(), node.getData());
    }
  }

  @Override
  public Collection<Bytes32> getStoredBranchRoots() {
    return newlyStoredBranches;
  }

  @Override
  public int getStoredBranchNodeCount() {
    return storedBranchNodes;
  }

  @Override
  public int getSkippedBranchNodeCount() {
    return skippedBranchNodes;
  }

  @Override
  public int getStoredLeafNodeCount() {
    return storedLeafNodes;
  }
}

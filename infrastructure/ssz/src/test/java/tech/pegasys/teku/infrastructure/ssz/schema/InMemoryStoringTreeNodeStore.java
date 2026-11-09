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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

public class InMemoryStoringTreeNodeStore implements TreeNodeStore, TreeNodeSource {

  private final Map<Bytes32, CompressedBranchInfo> branchNodes = new HashMap<>();
  private final Map<Bytes32, Bytes> leafNodes = new HashMap<>();

  @Override
  public boolean canSkipBranch(final Bytes32 root, final long gIndex) {
    return branchNodes.containsKey(root);
  }

  @Override
  public void storeBranchNode(
      final Bytes32 root, final long gIndex, final int depth, final Bytes32[] children) {
    branchNodes.putIfAbsent(
        root, new CompressedBranchInfo(depth, Arrays.copyOf(children, children.length)));
  }

  @Override
  public void storeLeafNode(final TreeNode treeNode, final long gIndex) {
    checkArgument(treeNode instanceof LeafDataNode, "Can't store a non-leaf node");
    final LeafDataNode node = (LeafDataNode) treeNode;
    if (node.getData().size() > Bytes32.SIZE && !node.hashTreeRoot().isZero()) {
      leafNodes.putIfAbsent(node.hashTreeRoot(), node.getData());
    }
  }

  @Override
  public Collection<? extends Bytes32> getStoredBranchRoots() {
    return branchNodes.keySet();
  }

  @Override
  public int getStoredBranchNodeCount() {
    return 0;
  }

  @Override
  public int getSkippedBranchNodeCount() {
    return 0;
  }

  @Override
  public int getStoredLeafNodeCount() {
    return 0;
  }

  @Override
  public CompressedBranchInfo loadBranchNode(final Bytes32 rootHash, final long gIndex) {
    return checkNotNull(branchNodes.get(rootHash), "Unknown branch node %s", rootHash);
  }

  @Override
  public Bytes loadLeafNode(final Bytes32 rootHash, final long gIndex) {
    return leafNodes.getOrDefault(rootHash, rootHash);
  }
}

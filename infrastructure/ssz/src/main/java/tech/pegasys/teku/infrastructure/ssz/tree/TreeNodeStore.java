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

package tech.pegasys.teku.infrastructure.ssz.tree;

import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;

public interface TreeNodeStore {

  /**
   * Called prior to visiting a branch or its descendants to determine if the branch needs to be
   * stored.
   *
   * @param root the hash tree root of the branch node
   * @param gIndex the generalized index of the branch node
   * @return true if the branch node and all its descendants can be skipped, false to iterate into
   *     the descendants
   */
  boolean canSkipBranch(Bytes32 root, long gIndex);

  /**
   * Store an intermediate branch node. Multiple levels of branch nodes may be skipped to optimise
   * iteration and storage, in which case the children are {@code depth} levels from the branch
   * node.
   *
   * <p>The children may omit trailing nodes which are not required to be stored, for example
   * because they are the zero tree.
   *
   * @param root the hash tree root of the branch node
   * @param gIndex the generalised index of the branch node
   * @param depth the number of tree levels being skipped. ie the depth of the tree from the branch
   *     node to the children
   * @param children the non-empty children at the specified depth from the branch node.
   */
  void storeBranchNode(Bytes32 root, long gIndex, int depth, Bytes32[] children);

  /**
   * Store a leaf node.
   *
   * @param node the visited leaf node
   * @param gIndex the generalized index of the node
   */
  void storeLeafNode(TreeNode node, long gIndex);

  Collection<? extends Bytes32> getStoredBranchRoots();

  int getStoredBranchNodeCount();

  int getSkippedBranchNodeCount();

  int getStoredLeafNodeCount();
}

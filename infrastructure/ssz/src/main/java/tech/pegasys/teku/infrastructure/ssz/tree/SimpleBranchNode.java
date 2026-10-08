/*
 * Copyright Consensys Software Inc., 2026
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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

class SimpleBranchNode implements BranchNode, TreeNode {

  private final TreeNode left;
  private final TreeNode right;
  private volatile Bytes32 cachedHash = null;

  public SimpleBranchNode(final TreeNode left, final TreeNode right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public TreeNode left() {
    return left;
  }

  @Override
  public TreeNode right() {
    return right;
  }

  @Override
  public BranchNode rebind(final boolean left, final TreeNode newNode) {
    return left ? new SimpleBranchNode(newNode, right()) : new SimpleBranchNode(left(), newNode);
  }

  @Override
  public TreeNode updated(final TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    } else {
      Pair<TreeUpdates, TreeUpdates> children = newNodes.splitAtPivot();
      return new SimpleBranchNode(
          left().updated(children.getLeft()), right().updated(children.getRight()));
    }
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = BranchNode.super.hashTreeRoot();
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public Bytes32 hashTreeRoot(final Sha256 sha256) {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = BranchNode.super.hashTreeRoot(sha256);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  @SuppressWarnings({"ReferenceComparison", "ReferenceEquality"})
  public String toString() {
    return left == right ? ("(2x " + left + ")") : ("(" + left + ", " + right + ')');
  }
}

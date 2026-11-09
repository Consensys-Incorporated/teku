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

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

public class LazyBranchNode implements BranchNode {
  private volatile Bytes32 cachedHash;
  private final Bytes32 leftRoot;
  private final Bytes32 rightRoot;

  private final Supplier<TreeNode> leftLoader;
  private final Supplier<TreeNode> rightLoader;

  private LazyBranchNode(
      final Bytes32 hashTreeRoot,
      final Bytes32 leftRoot,
      final Bytes32 rightRoot,
      final Supplier<TreeNode> leftLoader,
      final Supplier<TreeNode> rightLoader) {
    this.cachedHash = hashTreeRoot;
    this.leftRoot = leftRoot;
    this.rightRoot = rightRoot;

    this.leftLoader = Suppliers.memoize(leftLoader::get);
    this.rightLoader = Suppliers.memoize(rightLoader::get);
  }

  private LazyBranchNode(
      final Bytes32 leftRoot,
      final Bytes32 rightRoot,
      final Supplier<TreeNode> leftLoader,
      final Supplier<TreeNode> rightLoader) {
    this.leftRoot = leftRoot;
    this.rightRoot = rightRoot;
    this.leftLoader = leftLoader;
    this.rightLoader = rightLoader;
  }

  public static LazyBranchNode createWithKnownHash(
      final Bytes32 hashTreeRoot,
      final Bytes32 leftRoot,
      final Bytes32 rightRoot,
      final Supplier<TreeNode> leftLoader,
      final Supplier<TreeNode> rightLoader) {
    return new LazyBranchNode(hashTreeRoot, leftRoot, rightRoot, leftLoader, rightLoader);
  }

  public static LazyBranchNode createWithUnknownHash(
      final Bytes32 leftRoot,
      final Bytes32 rightRoot,
      final Supplier<TreeNode> leftLoader,
      final Supplier<TreeNode> rightLoader) {
    return new LazyBranchNode(
        leftRoot,
        rightRoot,
        Suppliers.memoize(leftLoader::get),
        Suppliers.memoize(rightLoader::get));
  }

  @Override
  public TreeNode left() {
    return leftLoader.get();
  }

  @Override
  public TreeNode right() {
    return rightLoader.get();
  }

  @Override
  public BranchNode rebind(final boolean left, final TreeNode newNode) {
    return left
        ? new LazyBranchNode(newNode.hashTreeRoot(), rightRoot, () -> newNode, rightLoader)
        : new LazyBranchNode(leftRoot, newNode.hashTreeRoot(), leftLoader, () -> newNode);
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = Hash.sha256(leftRoot, rightRoot);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public Bytes32 hashTreeRoot(final Sha256 sha256) {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = Bytes32.wrap(sha256.digest(leftRoot, rightRoot));
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public TreeNode updated(final TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    } else {
      Pair<TreeUpdates, TreeUpdates> children = newNodes.splitAtPivot();
      return BranchNode.create(
          left().updated(children.getLeft()), right().updated(children.getRight()));
    }
  }
}

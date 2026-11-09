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

/**
 * Optimizes access to a tree node by its index. Instead of traversing the full path from the root
 * node on every access applies various optimizations like caching last accessed node
 *
 * <p>TODO: cache the full tree path for the last element and start traversing from the common
 * ancestor
 *
 * <p>The class should be thread-safe
 */
public final class CachingTreeAccessor {

  private static final CachedTreeNode NA_CACHED_NODE = new CachedTreeNode(-1, null);

  @FunctionalInterface
  public interface GeneralizedIndexCalculator {

    long toGeneralizedIndex(long vectorIndex);
  }

  private final TreeNode root;
  private final GeneralizedIndexCalculator generalizedIndexCalculator;

  private volatile CachedTreeNode cachedTreeNode = NA_CACHED_NODE;

  public CachingTreeAccessor(TreeNode root, GeneralizedIndexCalculator generalizedIndexCalculator) {
    this.root = root;
    this.generalizedIndexCalculator = generalizedIndexCalculator;
  }

  public TreeNode getNodeByVectorIndex(int index) {
    CachedTreeNode cached = cachedTreeNode;
    if (cached.getNodeIndex() == index) {
      return cached.getNode();
    } else {
      long generalizedIndex = generalizedIndexCalculator.toGeneralizedIndex(index);

      TreeNode node = root.get(generalizedIndex);
      cachedTreeNode = new CachedTreeNode(index, node);
      return node;
    }
  }

  private static class CachedTreeNode {

    private final long nodeIndex;
    private final TreeNode node;

    public CachedTreeNode(final long nodeIndex, final TreeNode node) {
      this.nodeIndex = nodeIndex;
      this.node = node;
    }

    public long getNodeIndex() {
      return nodeIndex;
    }

    public TreeNode getNode() {
      return node;
    }
  }
}

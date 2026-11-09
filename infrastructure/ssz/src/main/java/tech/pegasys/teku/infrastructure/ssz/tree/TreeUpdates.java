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

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The collection of nodes and their target generalized indices to be updated The class also
 * contains the target generalized index this set of changes is applicable to.
 *
 * @see TreeNode#updated(TreeUpdates)
 */
public class TreeUpdates {

  /** A single tree update with target generalized index and the new target {@link TreeNode} */
  public static class Update {
    private final long generalizedIndex;
    private final TreeNode newNode;

    public Update(final long generalizedIndex, final TreeNode newNode) {
      this.generalizedIndex = generalizedIndex;
      this.newNode = newNode;
    }

    public long getGeneralizedIndex() {
      return generalizedIndex;
    }

    public TreeNode getNewNode() {
      return newNode;
    }
  }

  /** Convenient collector for the stream with {@link Update} elements */
  public static Collector<Update, ?, TreeUpdates> collector() {
    return Collectors.collectingAndThen(Collectors.toList(), TreeUpdates::new);
  }

  private final List<Long> gIndices;
  private final List<TreeNode> nodes;

  private final long prefix;
  private final int heightFromLeaf;

  /**
   * Creates a new instance of TreeNodes
   *
   * @param updates the list of {@link Update}s
   *     <p><b>NOTE: the list should conform to the following prerequisites</b>:
   *     <ul>
   *       <li>all generalized indices are unique
   *       <li>the list should be sorted by the target generalized index
   *       <li>the generalized indices should be on the same tree level. I.e. the highest order bit
   *           should be the same for all indices
   *     </ul>
   *
   * @throws IllegalArgumentException if the list doesn't conform to above restrictions
   */
  public TreeUpdates(final List<Update> updates) {
    this(
        updates.stream().map(Update::getGeneralizedIndex).toList(),
        updates.stream().map(Update::getNewNode).toList());
  }

  public TreeUpdates(final List<Long> gIndices, final List<TreeNode> nodes) {
    this(gIndices, nodes, 1, getDepthAndValidate(gIndices));
  }

  public TreeUpdates(final List<Long> gIndices, final List<TreeNode> nodes, final int depth) {
    this(gIndices, nodes, 1, depth);
    assert depth == getDepthAndValidate(gIndices);
  }

  private static TreeUpdates create(
      final List<Long> gIndices,
      final List<TreeNode> nodes,
      final long prefix,
      final int heightFromLeaf) {
    return new TreeUpdates(gIndices, nodes, prefix, heightFromLeaf);
  }

  private TreeUpdates(
      final List<Long> gIndices,
      final List<TreeNode> nodes,
      final long prefix,
      final int heightFromLeaf) {
    assert gIndices.size() == nodes.size();

    this.gIndices = gIndices;
    this.nodes = nodes;
    this.prefix = prefix;
    this.heightFromLeaf = heightFromLeaf;
  }

  /**
   * Split the nodes to left and right subtree subsets according the target generalized index
   *
   * @return the pair of node updates for left and right subtrees with accordingly adjusted target
   *     generalized indices
   */
  public Pair<TreeUpdates, TreeUpdates> splitAtPivot() {
    if (heightFromLeaf <= 0) {
      throw new IllegalStateException("Can't split leaf update");
    }
    long lPrefix = prefix << 1;
    long rPrefix = lPrefix | 1;
    long pivotGIndex = rPrefix << (heightFromLeaf - 1);

    int idx = Collections.binarySearch(gIndices, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        TreeUpdates.create(
            gIndices.subList(0, insIdx), nodes.subList(0, insIdx), lPrefix, heightFromLeaf - 1),
        TreeUpdates.create(
            gIndices.subList(insIdx, gIndices.size()),
            nodes.subList(insIdx, nodes.size()),
            rPrefix,
            heightFromLeaf - 1));
  }

  /** Number of updated nodes in this set */
  public int size() {
    return gIndices.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  /** Gets generalized index for update at position [index] */
  @VisibleForTesting
  long getGIndex(final int index) {
    return gIndices.get(index);
  }

  /** Calculates and returns relative generalized index */
  public long getRelativeGIndex(final int index) {
    return GIndexUtil.gIdxGetRelativeGIndex(gIndices.get(index), GIndexUtil.gIdxGetDepth(prefix));
  }

  /** Gets new tree node for update at position [index] */
  public TreeNode getNode(final int index) {
    return nodes.get(index);
  }

  private static int getDepthAndValidate(final List<Long> gIndices) {
    if (gIndices.isEmpty()) {
      return 0;
    }
    long highestBit = Long.highestOneBit(gIndices.get(0));
    long mask = highestBit - 1;
    long checkMask = ~mask;

    long lastGIdx = -1;
    for (int i = 0; i < gIndices.size(); i++) {
      long gIdx = gIndices.get(i);
      if (gIdx < 1) {
        throw new IllegalArgumentException("Invalid gIndex: " + gIdx);
      }
      if (gIdx <= lastGIdx) {
        throw new IllegalArgumentException("Invalid gIndex ordering: " + gIndices);
      }
      if ((gIdx & checkMask) != highestBit) {
        throw new IllegalArgumentException("Indices are of different depth: [0] and [" + i + "]");
      }
      lastGIdx = gIdx;
    }
    return Long.bitCount(mask);
  }

  /**
   * Checks if this instance is correct for the leaf node
   *
   * @throws IllegalArgumentException if not correct
   */
  public void checkLeaf() {
    if (heightFromLeaf != 0) {
      throw new IllegalArgumentException(
          "Non-zero heightFromLeaf for the leaf node: " + heightFromLeaf);
    }
    if (gIndices.size() != 1) {
      throw new IllegalArgumentException(
          "Number of nodes should be 1 for a leaf node: " + gIndices.size());
    }
    if (gIndices.get(0) != prefix) {
      throw new IllegalArgumentException(
          "Leaf gIndex != prefix: " + gIndices.get(0) + " != " + prefix);
    }
  }

  /** Indicates that this update should be applied to the node target generalized index */
  public boolean isFinal() {
    return (gIndices.size() == 1 && gIndices.get(0) == prefix);
  }
}

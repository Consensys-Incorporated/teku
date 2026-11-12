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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

  // The updates of the whole set, sorted by generalized index. An instance covers the range
  // [from, to) of them, so splitting a set neither copies nor wraps them.
  private final long[] gIndices;
  private final TreeNode[] nodes;
  private final int from;
  private final int to;

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
        updates.stream().mapToLong(Update::getGeneralizedIndex).toArray(),
        updates.stream().map(Update::getNewNode).toArray(TreeNode[]::new));
  }

  public TreeUpdates(final List<Long> gIndices, final List<TreeNode> nodes) {
    this(toLongArray(gIndices), nodes.toArray(TreeNode[]::new));
  }

  private TreeUpdates(final long[] gIndices, final TreeNode[] nodes) {
    this(gIndices, nodes, 0, gIndices.length, 1, getDepthAndValidate(gIndices));
    checkArgument(
        gIndices.length == nodes.length,
        "%s generalized indices for %s nodes",
        gIndices.length,
        nodes.length);
  }

  private TreeUpdates(
      final long[] gIndices,
      final TreeNode[] nodes,
      final int from,
      final int to,
      final long prefix,
      final int heightFromLeaf) {
    this.gIndices = gIndices;
    this.nodes = nodes;
    this.from = from;
    this.to = to;
    this.prefix = prefix;
    this.heightFromLeaf = heightFromLeaf;
  }

  private static long[] toLongArray(final List<Long> values) {
    return values instanceof LongList longList
        ? longList.toLongArray()
        : values.stream().mapToLong(Long::longValue).toArray();
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

    int idx = Arrays.binarySearch(gIndices, from, to, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        new TreeUpdates(gIndices, nodes, from, insIdx, lPrefix, heightFromLeaf - 1),
        new TreeUpdates(gIndices, nodes, insIdx, to, rPrefix, heightFromLeaf - 1));
  }

  /** Number of updated nodes in this set */
  public int size() {
    return to - from;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  /** Gets generalized index for update at position [index] */
  @VisibleForTesting
  long getGIndex(final int index) {
    return gIndices[from + Objects.checkIndex(index, size())];
  }

  /** Calculates and returns relative generalized index */
  public long getRelativeGIndex(final int index) {
    return GIndexUtil.gIdxGetRelativeGIndex(getGIndex(index), GIndexUtil.gIdxGetDepth(prefix));
  }

  /** Gets new tree node for update at position [index] */
  public TreeNode getNode(final int index) {
    return nodes[from + Objects.checkIndex(index, size())];
  }

  private static int getDepthAndValidate(final long[] gIndices) {
    if (gIndices.length == 0) {
      return 0;
    }
    long highestBit = Long.highestOneBit(gIndices[0]);
    long mask = highestBit - 1;
    long checkMask = ~mask;

    long lastGIdx = -1;
    for (int i = 0; i < gIndices.length; i++) {
      long gIdx = gIndices[i];
      if (gIdx < 1) {
        throw new IllegalArgumentException("Invalid gIndex: " + gIdx);
      }
      if (gIdx <= lastGIdx) {
        throw new IllegalArgumentException("Invalid gIndex ordering: " + Arrays.toString(gIndices));
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
    if (size() != 1) {
      throw new IllegalArgumentException("Number of nodes should be 1 for a leaf node: " + size());
    }
    if (gIndices[from] != prefix) {
      throw new IllegalArgumentException(
          "Leaf gIndex != prefix: " + gIndices[from] + " != " + prefix);
    }
  }

  /** Indicates that this update should be applied to the node target generalized index */
  public boolean isFinal() {
    return size() == 1 && gIndices[from] == prefix;
  }
}

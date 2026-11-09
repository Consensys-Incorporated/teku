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

import static java.lang.Integer.min;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

/**
 * Util methods for binary tree generalized indices manipulations See
 * https://github.com/ethereum/consensus-specs/blob/v1.0.0/ssz/merkle-proofs.md#generalized-merkle-tree-index
 * for more info on generalized indices
 *
 * <p>Here the general index is represented by <code>long</code> which is treated as unsigned uint64
 * Thus the only illegal generalized index value is <code>0</code>
 */
public class GIndexUtil {

  /** See {@link #gIdxCompare(long, long)} */
  public enum NodeRelation {
    LEFT,
    RIGHT,
    SUCCESSOR,
    PREDECESSOR,
    SAME;

    /** <code>gIdxCompare(idx1, idx2) == gIdxCompare(idx2, idx1).inverse()</code> */
    public NodeRelation inverse() {
      return switch (this) {
        case LEFT -> RIGHT;
        case RIGHT -> LEFT;
        case PREDECESSOR -> SUCCESSOR;
        case SUCCESSOR -> PREDECESSOR;
        case SAME -> SAME;
        default -> throw new IllegalArgumentException("Unknown: " + this);
      };
    }
  }

  /** Maximal depth this generalized index implementation can handle */
  // with the depth 64 positive long would overflow and we don't want to handle it here
  public static final int MAX_DEPTH = 63;

  /**
   * The generalized index of either a root tree node or an index of a node relative to the node
   * itself. Effectively this is <code>1L</code>
   */
  public static final long SELF_G_INDEX = 1;

  /** The generalized index of the left child. Effectively <code>0b10</code> */
  public static final long LEFT_CHILD_G_INDEX = gIdxLeftGIndex(SELF_G_INDEX);

  /** The generalized index of the right child. Effectively <code>0b11</code> */
  public static final long RIGHT_CHILD_G_INDEX = gIdxRightGIndex(SELF_G_INDEX);

  /**
   * The generalized index (normally an index of non-existing node) of the leftmost possible node
   * Effectively this is {@link Long#MIN_VALUE} or <code>0b10000...000L</code> in binary form
   */
  public static final long LEFTMOST_G_INDEX = gIdxLeftmostFrom(SELF_G_INDEX);

  /**
   * The generalized index (normally an index of non-existing node) of the rightmost possible node
   * Effectively this is <code>-1L</code> or <code>0b11111...111L</code> in binary form
   */
  public static final long RIGHTMOST_G_INDEX = gIdxRightmostFrom(SELF_G_INDEX);

  /**
   * Indicates that a relative generalized index refers to the node itself
   *
   * @see #SELF_G_INDEX
   */
  public static boolean gIdxIsSelf(final long generalizedIndex) {
    checkGIndex(generalizedIndex);
    return generalizedIndex == SELF_G_INDEX;
  }

  /**
   * Indicates how the node with generalized index <code>idx1</code> relates to the node with
   * generalized index <code>idx2</code>:
   *
   * <ul>
   *   <li>{@link NodeRelation#LEFT}: idx1 is to the left of idx2
   *   <li>{@link NodeRelation#RIGHT}: idx1 is to the right of idx2
   *   <li>{@link NodeRelation#SUCCESSOR}: idx1 is the successor of idx2
   *   <li>{@link NodeRelation#PREDECESSOR}: idx1 is the predecessor of idx2
   *   <li>{@link NodeRelation#SAME}: idx1 is equal to idx2
   * </ul>
   */
  public static NodeRelation gIdxCompare(final long idx1, final long idx2) {
    checkGIndex(idx1);
    checkGIndex(idx2);
    long anchor1 = Long.highestOneBit(idx1);
    long anchor2 = Long.highestOneBit(idx2);
    int depth1 = Long.bitCount(anchor1 - 1);
    int depth2 = Long.bitCount(anchor2 - 1);
    int minDepth = min(depth1, depth2);
    long minDepthIdx1 = idx1 >>> (depth1 - minDepth);
    long minDepthIdx2 = idx2 >>> (depth2 - minDepth);
    if (minDepthIdx1 == minDepthIdx2) {
      if (depth1 < depth2) {
        return NodeRelation.PREDECESSOR;
      } else if (depth1 > depth2) {
        return NodeRelation.SUCCESSOR;
      } else {
        return NodeRelation.SAME;
      }
    } else {
      if (minDepthIdx1 < minDepthIdx2) {
        return NodeRelation.LEFT;
      } else {
        return NodeRelation.RIGHT;
      }
    }
  }

  /**
   * Returns the depth of the node denoted by the supplied generalized index. E.g. the depth of the
   * {@link #SELF_G_INDEX} would be 0
   */
  public static int gIdxGetDepth(final long generalizedIndex) {
    checkGIndex(generalizedIndex);
    long anchor = Long.highestOneBit(generalizedIndex);
    return Long.bitCount(anchor - 1);
  }

  /**
   * Returns the generalized index of the left child of the node with specified generalized index
   * E.g. the result when passing {@link #SELF_G_INDEX} would be <code>10</code>
   */
  public static long gIdxLeftGIndex(final long generalizedIndex) {
    return gIdxChildGIndex(generalizedIndex, 0, 1);
  }

  /**
   * Returns the generalized index of the right child of the node with specified generalized index
   * E.g. the result when passing {@link #SELF_G_INDEX} would be <code>11</code>
   */
  public static long gIdxRightGIndex(final long generalizedIndex) {
    return gIdxChildGIndex(generalizedIndex, 1, 1);
  }

  /**
   * More generic variant of methods {@link #gIdxLeftGIndex(long)} {@link #gIdxRightGIndex(long)}
   * Calculates the generalized index of a node's <code>childIdx</code> successor at depth <code>
   * childDepth</code> (depth relative to the original node). Note that <code>childIdx</code> is not
   * the generalized index but index number of child.
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxChildGIndex(SELF_G_INDEX, 0, 2) == 100</code>
   *   <li><code>gIdxChildGIndex(SELF_G_INDEX, 1, 2) == 101</code>
   *   <li><code>gIdxChildGIndex(SELF_G_INDEX, 2, 2) == 110</code>
   *   <li><code>gIdxChildGIndex(SELF_G_INDEX, 3, 2) == 111</code>
   *   <li><code>gIdxChildGIndex(SELF_G_INDEX, 4, 2) is invalid cause there are just 4 successors
   *   at depth 2</code>
   *   <li><code>gIdxChildGIndex(anyIndex, 0, 1) == gIdxLeftGIndex(anyIndex)</code>
   *   <li><code>gIdxChildGIndex(anyIndex, 1, 1) == gIdxRightGIndex(anyIndex)</code>
   * </ul>
   */
  public static long gIdxChildGIndex(
      final long generalizedIndex, final long childIdx, final int childDepth) {
    checkGIndex(generalizedIndex);
    assert childDepth >= 0 && childDepth <= MAX_DEPTH;
    assert childIdx >= 0 && childIdx < (1L << childDepth);
    assert gIdxGetDepth(generalizedIndex) + childDepth <= MAX_DEPTH;
    return (generalizedIndex << childDepth) | childIdx;
  }

  /**
   * The inverse of {@link #gIdxChildGIndex(long, long, int)} to convert the generalized index of a
   * descendant at a specific depth back to the index number of that child.
   *
   * @param childGeneralizedIndex the generalized index of the child
   * @param childDepth the depth of the child from the ancestor node to get an index number for
   * @return the zero-based index number of the child at {@code childGeneralizedIndex} in a list of
   *     children {@code childDepth}
   */
  public static int gIdxChildIndexFromGIndex(
      final long childGeneralizedIndex, final int childDepth) {
    checkGIndex(childGeneralizedIndex);
    assert childDepth >= 0 && childDepth <= MAX_DEPTH;
    final long rootGIndex = childGeneralizedIndex >>> childDepth;
    checkGIndex(rootGIndex);
    final long leftMostAtDepth = rootGIndex << childDepth;
    return (int) (childGeneralizedIndex - leftMostAtDepth);
  }

  /**
   * Compose absolute generalized index, where <code>childGeneralizedIndex</code> is relative to the
   * node at <code>parentGeneralizedIndex</code>
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxCompose(0b1111, 0b1000) == 0b1111000</code>
   *   <li><code>gIdxCompose(0b1000, 0b1111) == 0b1000111</code>
   * </ul>
   */
  public static long gIdxCompose(
      final long parentGeneralizedIndex, final long childGeneralizedIndex) {
    checkGIndex(parentGeneralizedIndex);
    checkGIndex(childGeneralizedIndex);
    assert gIdxGetDepth(parentGeneralizedIndex) + gIdxGetDepth(childGeneralizedIndex) <= MAX_DEPTH;

    long childAnchor = Long.highestOneBit(childGeneralizedIndex);
    int childDepth = Long.bitCount(childAnchor - 1);
    return (parentGeneralizedIndex << childDepth) | (childGeneralizedIndex ^ childAnchor);
  }

  /**
   * Compose absolute generalized index, over a list of indices, where <code>childGeneralizedIndices
   * </code> is relative to the nodes at <code>parentGeneralizedIndex</code>
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxComposeAll(0b1111, [0b1000, 0b1001]) == [0b1111000, 0b11111001]</code>
   *   <li><code>gIdxComposeAll(0b1000, [0b1111, 0b0101]) == [0b1000111, 0b10000101]</code>
   * </ul>
   */
  public static LongList gIdxComposeAll(
      final long parentGeneralizedIndex, final LongList childGeneralizedIndices) {
    return childGeneralizedIndices
        .longStream()
        .map(childIndex -> gIdxCompose(parentGeneralizedIndex, childIndex))
        .collect(LongArrayList::new, LongArrayList::add, LongList::addAll);
  }

  /**
   * Returns the generalized index (normally an index of non-existing node) of the leftmost possible
   * successor of this node
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxLeftmostFrom(0b1100) == 0b110000000...00L</code>
   *   <li><code>gIdxLeftmostFrom(0b1101) == 0b110100000...00L</code>
   * </ul>
   */
  public static long gIdxLeftmostFrom(final long fromGeneralizedIndex) {
    checkGIndex(fromGeneralizedIndex);
    long highestOneBit = Long.highestOneBit(fromGeneralizedIndex);
    if (highestOneBit < 0) {
      return fromGeneralizedIndex;
    } else {
      int nodeDepth = Long.bitCount(highestOneBit - 1);
      return fromGeneralizedIndex << (MAX_DEPTH - nodeDepth);
    }
  }

  /**
   * Returns the generalized index (normally an index of non-existing node) of the rightmost
   * possible successor of this node
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxRightmostFrom(0b1100) == 0b110011111...11L</code>
   *   <li><code>gIdxRightmostFrom(0b1101) == 0b110111111...11L</code>
   * </ul>
   */
  public static long gIdxRightmostFrom(final long fromGeneralizedIndex) {
    checkGIndex(fromGeneralizedIndex);
    long highestOneBit = Long.highestOneBit(fromGeneralizedIndex);
    if (highestOneBit < 0) {
      return fromGeneralizedIndex;
    } else {
      int nodeDepth = Long.bitCount(highestOneBit - 1);
      int shiftN = MAX_DEPTH - nodeDepth;
      return (fromGeneralizedIndex << shiftN) | ((1L << shiftN) - 1);
    }
  }

  /**
   * Returns the index number (not a generalized index) of a node at depth <code>childDepth</code>
   * which is a predecessor of or equal to the node at <code>generalizedIndex</code>
   *
   * <p>For example:
   *
   * <ul>
   *   <li><code>gIdxGetChildIndex(LEFTMOST_G_INDEX, anyDepth) == 0</code>
   *   <li><code>gIdxGetChildIndex(0b1100, 2) == 2</code>
   *   <li><code>gIdxGetChildIndex(0b1101, 2) == 2</code>
   *   <li><code>gIdxGetChildIndex(0b1110, 2) == 3</code>
   *   <li><code>gIdxGetChildIndex(0b1111, 2) == 3</code>
   *   <li><code>gIdxGetChildIndex(0b11, 2)</code> call would be invalid cause node with index 0b11
   *       is at depth 1
   * </ul>
   */
  public static int gIdxGetChildIndex(final long generalizedIndex, final int childDepth) {
    checkGIndex(generalizedIndex);
    assert childDepth >= 0 && childDepth <= MAX_DEPTH;

    long anchor = Long.highestOneBit(generalizedIndex);
    int indexBitCount = Long.bitCount(anchor - 1);
    assert indexBitCount >= childDepth;
    long generalizedIndexWithoutAnchor = generalizedIndex ^ anchor;
    return (int) (generalizedIndexWithoutAnchor >>> (indexBitCount - childDepth));
  }

  /**
   * Returns the generalized index of the node at <code>generalizedIndex</code> relative to its
   * predecessor at depth <code>childDepth</code> For example:
   *
   * <ul>
   *   <li><code>gIdxGetRelativeGIndex(0b1100, 2) == 0b10</code>
   *   <li><code>gIdxGetChildIndex(0b1101, 2) == 0b11</code>
   *   <li><code>gIdxGetChildIndex(0b1110, 2) == 0b10</code>
   *   <li><code>gIdxGetChildIndex(0b1111, 3) == SELF_G_INDEX</code>
   *   <li><code>gIdxGetChildIndex(0b11, 2)</code> call would be invalid cause node with index 0b11
   *       is at depth 1
   * </ul>
   */
  public static long gIdxGetRelativeGIndex(final long generalizedIndex, final int childDepth) {
    checkGIndex(generalizedIndex);
    assert childDepth >= 0 && childDepth <= MAX_DEPTH;

    long anchor = Long.highestOneBit(generalizedIndex);
    long pivot = anchor >>> childDepth;
    assert pivot != 0;
    return (generalizedIndex & (pivot - 1)) | pivot;
  }

  @VisibleForTesting
  static long gIdxGetParent(final long generalizedIndex) {
    checkGIndex(generalizedIndex);
    return generalizedIndex >>> 1;
  }

  private static void checkGIndex(final long index) {
    assert index != 0;
  }
}

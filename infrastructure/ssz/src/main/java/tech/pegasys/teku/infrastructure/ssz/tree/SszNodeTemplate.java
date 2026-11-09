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
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.SELF_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxIsSelf;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxRightGIndex;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

/**
 * Represents the tree structure for a fixed size SSZ type See {@link SszSuperNode} docs for more
 * details
 */
public class SszNodeTemplate {

  static final class Location {
    private final int offset;
    private final int length;
    private final boolean leaf;

    public Location(final int offset, final int length, final boolean leaf) {
      this.offset = offset;
      this.length = length;
      this.leaf = leaf;
    }

    public Location withAddedOffset(final int addOffset) {
      return new Location(getOffset() + addOffset, getLength(), isLeaf());
    }

    public int getOffset() {
      return offset;
    }

    public int getLength() {
      return length;
    }

    public boolean isLeaf() {
      return leaf;
    }
  }

  public static SszNodeTemplate createFromType(final SszSchema<?> sszSchema) {
    checkArgument(sszSchema.isFixedSize(), "Only fixed size types supported");

    return createFromTree(sszSchema.getDefaultTree());
  }

  // This should be CANONICAL binary tree
  private static SszNodeTemplate createFromTree(final TreeNode defaultTree) {
    Map<Long, Location> gIdxToLoc =
        binaryTraverse(
            GIndexUtil.SELF_G_INDEX,
            defaultTree,
            new BinaryVisitor<>() {
              @Override
              public Map<Long, Location> visitLeaf(final long gIndex, final LeafNode node) {
                Long2ObjectMap<Location> ret = new Long2ObjectOpenHashMap<Location>();
                ret.put(gIndex, new Location(0, node.getData().size(), true));
                return ret;
              }

              @Override
              public Map<Long, Location> visitBranch(
                  final long gIndex,
                  final TreeNode node,
                  final Map<Long, Location> leftVisitResult,
                  final Map<Long, Location> rightVisitResult) {
                Location leftChildLoc = leftVisitResult.get(gIdxLeftGIndex(gIndex));
                Location rightChildLoc = rightVisitResult.get(gIdxRightGIndex(gIndex));
                rightVisitResult.replaceAll(
                    (idx, loc) -> loc.withAddedOffset(leftChildLoc.getLength()));
                leftVisitResult.putAll(rightVisitResult);
                leftVisitResult.put(
                    gIndex,
                    new Location(0, leftChildLoc.getLength() + rightChildLoc.getLength(), false));
                return leftVisitResult;
              }
            });
    return new SszNodeTemplate(gIdxToLoc, defaultTree);
  }

  private static List<Bytes> nodeSsz(final TreeNode node) {
    List<Bytes> sszBytes = new ArrayList<>();
    TreeUtil.iterateLeavesData(node, LEFTMOST_G_INDEX, RIGHTMOST_G_INDEX, sszBytes::add);
    return sszBytes;
  }

  private final Map<Long, Location> gIdxToLoc;
  private final TreeNode defaultTree;
  private final Map<Long, SszNodeTemplate> subTemplatesCache = new ConcurrentHashMap<>();

  public SszNodeTemplate(final Map<Long, Location> gIdxToLoc, final TreeNode defaultTree) {
    this.gIdxToLoc = gIdxToLoc;
    this.defaultTree = defaultTree;
  }

  public Location getNodeSszLocation(final long generalizedIndex) {
    return gIdxToLoc.get(generalizedIndex);
  }

  public int getSszLength() {
    return gIdxToLoc.get(SELF_G_INDEX).getLength();
  }

  public SszNodeTemplate getSubTemplate(final long generalizedIndex) {
    return subTemplatesCache.computeIfAbsent(generalizedIndex, this::calcSubTemplate);
  }

  private SszNodeTemplate calcSubTemplate(final long generalizedIndex) {
    if (gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    TreeNode subTree = defaultTree.get(generalizedIndex);
    return createFromTree(subTree);
  }

  public void update(final long generalizedIndex, final TreeNode newNode, final MutableBytes dest) {
    update(generalizedIndex, nodeSsz(newNode), dest);
  }

  private void update(
      final long generalizedIndex, final List<Bytes> nodeSsz, final MutableBytes dest) {
    Location leafPos = getNodeSszLocation(generalizedIndex);
    int off = 0;
    for (int i = 0; i < nodeSsz.size(); i++) {
      Bytes newSszChunk = nodeSsz.get(i);
      newSszChunk.copyTo(dest, leafPos.getOffset() + off);
      off += newSszChunk.size();
    }
    checkArgument(off == leafPos.getLength());
  }

  public byte[] calculateHashTreeRootRaw(final Bytes ssz, final int offset, final Sha256 sha256) {
    return binaryTraverse(
        SELF_G_INDEX,
        defaultTree,
        new BinaryVisitor<>() {
          @Override
          public byte[] visitLeaf(final long gIndex, final LeafNode node) {
            final Location location = gIdxToLoc.get(gIndex);
            final byte[] result = new byte[32];
            final int srcOffset = offset + location.getOffset();
            final int length = location.getLength();
            for (int i = 0; i < length; i++) {
              result[i] = ssz.get(srcOffset + i);
            }
            return result;
          }

          @Override
          public byte[] visitBranch(
              final long gIndex,
              final TreeNode node,
              final byte[] leftVisitResult,
              final byte[] rightVisitResult) {
            return sha256.digest(leftVisitResult, rightVisitResult);
          }
        });
  }

  private static <T> T binaryTraverse(
      final long gIndex, final TreeNode node, final BinaryVisitor<T> visitor) {
    if (node instanceof final LeafNode leafnode) {
      return visitor.visitLeaf(gIndex, leafnode);
    } else if (node instanceof BranchNode branchNode) {
      return visitor.visitBranch(
          gIndex,
          branchNode,
          binaryTraverse(gIdxLeftGIndex(gIndex), branchNode.left(), visitor),
          binaryTraverse(gIdxRightGIndex(gIndex), branchNode.right(), visitor));
    } else {
      throw new IllegalArgumentException("Unexpected node type: " + node.getClass());
    }
  }

  private interface BinaryVisitor<T> {

    T visitLeaf(long gIndex, LeafNode node);

    T visitBranch(long gIndex, TreeNode node, T leftVisitResult, T rightVisitResult);
  }
}

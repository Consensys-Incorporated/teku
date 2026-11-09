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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.infrastructure.ssz.tree.SszNodeTemplate.Location;

/**
 * Stores consecutive elements of the same fixed size type as a single packed bytes of their leaves
 * (this representation exactly matches SSZ representation of elements sequence).
 *
 * <p>This node represents a subtree of binary merkle tree for sequence (list or vector) of elements
 * with maximum length of <code>2 ^ depth</code>. If the sequence has less than maximum elements
 * then <code>ssz</code> bytes store only existing elements (what again matches SSZ representation
 * of a list)
 *
 * <p>To address individual nodes inside elements and resolve their internal generalized indices the
 * node uses {@link SszNodeTemplate} which represents element type tree structure
 *
 * <p>This node favors memory efficiency over update performance and thus is the best choice for
 * rarely updated and space consuming structures (e.g. Eth2 <code>BeaconState.validators</code>
 * list)
 */
public class SszSuperNode implements TreeNode, LeafDataNode {
  private static final byte[] DEFAULT_HASH_BYTES =
      LeafNode.EMPTY_LEAF.hashTreeRoot().toArrayUnsafe();

  private final int depth;
  private final SszNodeTemplate elementTemplate;
  private final Bytes ssz;
  private volatile Bytes32 cachedHash;

  public SszSuperNode(final int depth, final SszNodeTemplate elementTemplate, final Bytes ssz) {
    this.depth = depth;
    this.elementTemplate = elementTemplate;
    this.ssz = ssz;
    checkArgument(ssz.size() % elementTemplate.getSszLength() == 0);
    checkArgument(getElementsCount() <= getMaxElements());
  }

  private int getMaxElements() {
    return 1 << depth;
  }

  private int getElementsCount() {
    return ssz.size() / elementTemplate.getSszLength();
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      final Sha256 sha256 = Hash.getSha256Instance();
      cachedHash = calcHashTreeRoot(sha256);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public Bytes32 hashTreeRoot(final Sha256 sha256) {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = calcHashTreeRoot(sha256);
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  private Bytes32 calcHashTreeRoot(final Sha256 sha256) {
    return Bytes32.wrap(hashTreeRootRaw(0, 0, sha256));
  }

  private byte[] hashTreeRootRaw(final int curDepth, final int offset, final Sha256 sha256) {
    if (curDepth == depth) {
      if (offset < ssz.size()) {
        return elementTemplate.calculateHashTreeRootRaw(ssz, offset, sha256);
      } else {
        assert offset <= elementTemplate.getSszLength() * (getMaxElements() - 1);
        return DEFAULT_HASH_BYTES;
      }
    } else {
      final byte[] leftRoot = hashTreeRootRaw(curDepth + 1, offset, sha256);
      final byte[] rightRoot =
          hashTreeRootRaw(
              curDepth + 1,
              offset + elementTemplate.getSszLength() * (1 << ((depth - curDepth) - 1)),
              sha256);
      return sha256.digest(leftRoot, rightRoot);
    }
  }

  @NotNull
  @Override
  public TreeNode get(final long generalizedIndex) {
    if (GIndexUtil.gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    int childIndex = GIndexUtil.gIdxGetChildIndex(generalizedIndex, depth);
    int childOffset = childIndex * elementTemplate.getSszLength();
    checkArgument(childOffset < ssz.size(), "Invalid index");
    long relativeGIndex = GIndexUtil.gIdxGetRelativeGIndex(generalizedIndex, depth);
    Location nodeLoc = elementTemplate.getNodeSszLocation(relativeGIndex);
    if (nodeLoc.isLeaf()) {
      return LeafNode.create(ssz.slice(childOffset + nodeLoc.getOffset(), nodeLoc.getLength()));
    } else if (GIndexUtil.gIdxIsSelf(relativeGIndex)) {
      return new SszSuperNode(
          0, elementTemplate, ssz.slice(childOffset, elementTemplate.getSszLength()));
    } else {
      SszNodeTemplate subTemplate = elementTemplate.getSubTemplate(relativeGIndex);
      return new SszSuperNode(
          0, subTemplate, ssz.slice(childOffset + nodeLoc.getOffset(), nodeLoc.getLength()));
    }
  }

  @Override
  public boolean iterate(
      final long thisGeneralizedIndex,
      final long startGeneralizedIndex,
      final TreeVisitor visitor) {
    if (GIndexUtil.gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.LEFT) {
      return true;
    } else {
      return visitor.visit(this, thisGeneralizedIndex);
    }
  }

  @Override
  public TreeNode updated(final TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    }
    long leftmostUpdateIndex = newNodes.getRelativeGIndex(newNodes.size() - 1);
    int leftmostChildIndex = GIndexUtil.gIdxGetChildIndex(leftmostUpdateIndex, depth);
    int newSszSize = (leftmostChildIndex + 1) * elementTemplate.getSszLength();
    Bytes updatedSizeSsz =
        newSszSize <= ssz.size()
            ? ssz
            : Bytes.wrap(ssz, Bytes.wrap(new byte[newSszSize - ssz.size()]));
    MutableBytes mutableCopy = updatedSizeSsz.mutableCopy();
    for (int i = 0; i < newNodes.size(); i++) {
      long updateGIndex = newNodes.getRelativeGIndex(i);
      int childIndex = GIndexUtil.gIdxGetChildIndex(updateGIndex, depth);
      long childGIndex = GIndexUtil.gIdxGetRelativeGIndex(updateGIndex, depth);
      int childOffset = childIndex * elementTemplate.getSszLength();
      MutableBytes childMutableSlice =
          mutableCopy.mutableSlice(childOffset, elementTemplate.getSszLength());
      elementTemplate.update(childGIndex, newNodes.getNode(i), childMutableSlice);
    }
    return new SszSuperNode(depth, elementTemplate, mutableCopy);
  }

  @Override
  public Bytes getData() {
    return ssz;
  }

  @Override
  public String toString() {
    int sszLength = elementTemplate.getSszLength();
    return "SszSuperNode{"
        + IntStream.range(0, getElementsCount())
            .mapToObj(i -> ssz.slice(i * sszLength, sszLength).toString())
            .collect(Collectors.joining(", "))
        + "}";
  }
}

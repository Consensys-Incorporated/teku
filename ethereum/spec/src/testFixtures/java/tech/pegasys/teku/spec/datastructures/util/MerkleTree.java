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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;

public class MerkleTree {

  private final List<List<Bytes32>> tree;
  private final List<Bytes32> zeroHashes;
  protected final int treeDepth; // Root does not count as depth, i.e. tree height is treeDepth + 1

  public MerkleTree(final int treeDepth) {
    checkArgument(treeDepth > 1, "MerkleTree: treeDepth must be greater than 1");
    this.treeDepth = treeDepth;
    this.tree = new ArrayList<>();
    for (int i = 0; i <= treeDepth; i++) {
      this.tree.add(new ArrayList<>());
    }
    this.zeroHashes = generateZeroHashes(treeDepth);
  }

  protected static List<Bytes32> generateZeroHashes(final int height) {
    final List<Bytes32> zeroHashes = new ArrayList<>();
    zeroHashes.add(Bytes32.ZERO);
    for (int i = 1; i < height; i++) {
      zeroHashes.add(i, Hash.sha256(zeroHashes.get(i - 1), zeroHashes.get(i - 1)));
    }
    return zeroHashes;
  }

  public void add(final Bytes32 leaf) {
    if (!tree.getFirst().isEmpty() && tree.getFirst().getLast().equals(zeroHashes.getFirst())) {
      tree.getFirst().removeLast();
    }
    int mutableStageSize = tree.getFirst().size();
    tree.getFirst().add(leaf);
    for (int depth = 0; depth <= treeDepth; depth++) {
      final List<Bytes32> stage = tree.get(depth);
      if (depth > 0) {
        // Remove elements that should be modified
        mutableStageSize /= 2;
        while (stage.size() != mutableStageSize) {
          stage.removeLast();
        }

        final List<Bytes32> previousStage = tree.get(depth - 1);
        final int previousStageSize = previousStage.size();
        stage.add(
            Hash.sha256(
                previousStage.get(previousStageSize - 2),
                previousStage.get(previousStageSize - 1)));
      }
      if (stage.size() % 2 == 1 && depth != treeDepth) {
        stage.add(zeroHashes.get(depth));
      }
    }
  }

  public int getNumberOfLeaves() {
    final int lastLeafIndex = tree.getFirst().size() - 1;
    if (tree.getFirst().get(lastLeafIndex).equals(Bytes32.ZERO)) {
      return tree.getFirst().size() - 1;
    }
    return tree.getFirst().size();
  }

  public List<Bytes32> getProof(final Bytes32 value) {
    final int index = tree.getFirst().indexOf(value);
    if (index == -1) {
      throw new IllegalArgumentException("Leaf value is missing from the MerkleTree");
    }
    return getProof(index);
  }

  public List<Bytes32> getProof(final int itemIndex) {
    int mutableIndex = itemIndex;
    final List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; i++) {

      // Get index of sibling node
      final int siblingIndex = mutableIndex % 2 == 1 ? mutableIndex - 1 : mutableIndex + 1;

      // If sibling is contained in the tree
      if (siblingIndex < tree.get(i).size()) {

        // Get the sibling from the tree
        proof.add(tree.get(i).get(siblingIndex));
      } else {

        // Get the zero hash at the appropriate
        // depth of the tree as sibling
        proof.add(zeroHashes.get(i));
      }

      mutableIndex /= 2;
    }
    proof.add(calcMixInValue());
    return proof;
  }

  private Bytes32 calcViewBoundaryRoot(final int depth, final int viewLimit) {
    if (depth == 0) {
      return zeroHashes.getFirst();
    }
    final int deeperDepth = depth - 1;
    final Bytes32 deeperRoot = calcViewBoundaryRoot(deeperDepth, viewLimit);
    // Check if given the viewLimit at the leaf layer, is root in left or right subtree
    if ((viewLimit & (1 << deeperDepth)) != 0) {
      // For the right subtree
      return Hash.sha256(tree.get(deeperDepth).get((viewLimit >> deeperDepth) - 1), deeperRoot);
    } else {
      // For the left subtree
      return Hash.sha256(deeperRoot, zeroHashes.get(deeperDepth));
    }
  }

  /**
   * @param value of the leaf
   * @param viewLimit number of leaves in the tree
   * @return proof (i.e. collection of siblings on the way to root for the given leaf)
   */
  public List<Bytes32> getProofWithViewBoundary(final Bytes32 value, final int viewLimit) {
    return getProofWithViewBoundary(tree.getFirst().indexOf(value), viewLimit);
  }

  /**
   * @param itemIndex of the leaf
   * @param viewLimit number of leaves in the tree
   * @return proof (i.e. collection of siblings on the way to root for the given leaf)
   */
  public List<Bytes32> getProofWithViewBoundary(final int itemIndex, final int viewLimit) {
    checkArgument(itemIndex < viewLimit, "MerkleTree: Index must be less than the view limit");
    int mutableIndex = itemIndex;
    final List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; i++) {
      // Get index of sibling node
      final int siblingIndex = mutableIndex % 2 == 1 ? mutableIndex - 1 : mutableIndex + 1;

      // Check how much of the tree at this level is strictly within the view limit.
      int limit = viewLimit >> i;

      checkArgument(
          limit <= tree.get(i).size(), "MerkleTree: Tree is too small for given limit at height");

      // If the sibling is equal to the limit,
      if (siblingIndex == limit) {
        // Go deeper to partially merkleize in zero-hashes.
        proof.add(calcViewBoundaryRoot(i, viewLimit));
      } else if (siblingIndex > limit) {
        // Beyond:
        // Just use a zero-hash as effective sibling.
        proof.add(zeroHashes.get(i));
      } else {
        // Within:
        // Return the tree node as-is without modifications
        proof.add(tree.get(i).get(siblingIndex));
      }
      mutableIndex >>>= 1;
    }
    proof.add(calcMixInValue(viewLimit));
    return proof;
  }

  public Bytes32 calcMixInValue(final int viewLimit) {
    return (Bytes32)
        Bytes.concatenate(Bytes.ofUnsignedLong(viewLimit, LITTLE_ENDIAN), Bytes.wrap(new byte[24]));
  }

  public Bytes32 calcMixInValue() {
    return calcMixInValue(getNumberOfLeaves());
  }

  public Bytes32 getRoot() {
    return Hash.sha256(tree.get(treeDepth).getFirst(), calcMixInValue());
  }

  @Override
  public String toString() {
    StringBuilder returnString = new StringBuilder();
    final int numLeaves = (int) Math.pow(2, treeDepth);
    int mutableHeight = 0;
    for (int i = treeDepth; i >= 0; i--) {
      final int stageFullSize = (int) Math.pow(2, mutableHeight);
      mutableHeight++;
      final int stageNonZeroSize = tree.get(i).size();
      List<Bytes32> stageItems = new ArrayList<>(tree.get(i));
      for (int j = stageNonZeroSize; j < stageFullSize; j++) {
        stageItems.add(zeroHashes.get(i));
      }
      returnString.append("\n").append(centerPrint(stageItems, numLeaves)).append(stageFullSize);
    }
    return "MerkleTree{" + "tree=" + returnString + ", treeDepth=" + treeDepth + '}';
  }

  private String centerPrint(final List<Bytes32> stageItems, final int numLeaves) {
    final String emptySpaceOnSide =
        numLeaves == stageItems.size()
            ? "                "
            : IntStream.range(0, (numLeaves - stageItems.size()))
                .mapToObj(i -> "    ")
                .collect(Collectors.joining("    "));

    final String stageString =
        stageItems.stream()
            .map(item -> item.toHexString().substring(63))
            .collect(Collectors.joining(emptySpaceOnSide));

    return emptySpaceOnSide.concat(stageString).concat(emptySpaceOnSide);
  }
}

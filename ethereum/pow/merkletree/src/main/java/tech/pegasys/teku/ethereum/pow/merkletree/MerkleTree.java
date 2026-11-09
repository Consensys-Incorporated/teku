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

package tech.pegasys.teku.ethereum.pow.merkletree;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;

abstract class MerkleTree {

  public abstract Bytes32 getRoot();

  protected abstract boolean isFull();

  protected abstract MerkleTree pushLeaf(Bytes32 leaf, int level);

  public abstract MerkleTree finalize(long depositsToFinalize, int level);

  /**
   * Returns the number of finalized deposits in the tree while populating result with the finalized
   * hashes.
   */
  public abstract long getFinalized(List<Bytes32> result);

  public static MerkleTree create(final List<Bytes32> leaves, final int depth) {
    if (leaves.isEmpty()) {
      return new ZeroMerkleTree(depth);
    }
    if (depth == 0) {
      return new LeafMerkleTree(leaves.get(0));
    }
    final int split = (int) Math.min(1L << (depth - 1), leaves.size());
    final MerkleTree left = create(leaves.subList(0, split), depth - 1);
    final MerkleTree right = create(leaves.subList(split, leaves.size()), depth - 1);
    return new BranchMerkleTree(left, right);
  }

  public static MerkleTree fromSnapshotParts(
      final List<Bytes32> finalized, final long deposits, final int level) {
    if (finalized.isEmpty()) {
      checkArgument(deposits == 0, "Finalized roots did not cover all included deposits");
      return new ZeroMerkleTree(level);
    }
    if (deposits == 1L << level) {
      return new FinalizedMerkleTree(deposits, finalized.get(0));
    }
    checkArgument(
        level > 0,
        "Deposit tree snapshot included more finalized deposits than could fit in the merkle tree depth");
    final long leftSubTreeDepositCount = 1L << (level - 1);
    final MerkleTree left;
    final MerkleTree right;
    if (deposits < leftSubTreeDepositCount) {
      left = fromSnapshotParts(finalized, deposits, level - 1);
      right = new ZeroMerkleTree(level - 1);
    } else {
      left = new FinalizedMerkleTree(leftSubTreeDepositCount, finalized.get(0));
      right =
          fromSnapshotParts(
              finalized.subList(1, finalized.size()),
              deposits - leftSubTreeDepositCount,
              level - 1);
    }
    return new BranchMerkleTree(left, right);
  }

  protected abstract MerkleTree addToProof(
      final List<Bytes32> proof, final long index, final int depth);

  public List<Bytes32> generateProof(final long index, final int depth) {
    final List<Bytes32> proof = new ArrayList<>();
    MerkleTree node = this;
    int remainingDepth = depth;
    while (remainingDepth > 0) {
      node = node.addToProof(proof, index, remainingDepth);
      remainingDepth--;
    }
    Collections.reverse(proof);
    return proof;
  }

  public abstract void forEachLeafInclusive(
      final long startIndex,
      final long endIndex,
      final int depth,
      final Consumer<Bytes32> consumer);
}

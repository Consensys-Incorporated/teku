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

import static tech.pegasys.teku.infrastructure.crypto.Hash.sha256;

import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;

class BranchMerkleTree extends MerkleTree {

  private MerkleTree left;
  private MerkleTree right;
  private Bytes32 root;

  public BranchMerkleTree(final MerkleTree left, final MerkleTree right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public Bytes32 getRoot() {
    if (root == null) {
      root = sha256(left.getRoot(), right.getRoot());
    }
    return root;
  }

  @Override
  protected boolean isFull() {
    return right.isFull();
  }

  @Override
  protected MerkleTree pushLeaf(final Bytes32 leaf, final int level) {
    if (!left.isFull()) {
      left = left.pushLeaf(leaf, level - 1);
    } else {
      right = right.pushLeaf(leaf, level - 1);
    }
    root = null;
    return this;
  }

  @Override
  public MerkleTree finalize(final long depositsToFinalize, final int level) {
    final long deposits = 1L << level;
    if (deposits <= depositsToFinalize) {
      return new FinalizedMerkleTree(deposits, getRoot());
    }
    final MerkleTree newLeft = left.finalize(depositsToFinalize, level - 1);
    final MerkleTree newRight;
    if (depositsToFinalize > deposits / 2) {
      final long remaining = depositsToFinalize - (deposits / 2);
      newRight = right.finalize(remaining, level - 1);
    } else {
      newRight = right;
    }
    return new BranchMerkleTree(newLeft, newRight);
  }

  @Override
  public long getFinalized(final List<Bytes32> result) {
    return left.getFinalized(result) + right.getFinalized(result);
  }

  @Override
  protected MerkleTree addToProof(final List<Bytes32> proof, final long index, final int depth) {
    final long ithBit = (index >> (depth - 1L)) & 0x01;
    if (ithBit == 1) {
      proof.add(left.getRoot());
      return right;
    } else {
      proof.add(right.getRoot());
      return left;
    }
  }

  @Override
  public void forEachLeafInclusive(
      final long startIndex,
      final long endIndex,
      final int depth,
      final Consumer<Bytes32> consumer) {
    // Total number of deposits under this node
    final long deposits = 1L << depth;
    // Index of last index in left node
    final long split = deposits / 2;
    if (startIndex < split) {
      left.forEachLeafInclusive(startIndex, Math.min(endIndex, split), depth - 1, consumer);
    }
    if (endIndex > split) {
      right.forEachLeafInclusive(
          startIndex > split ? startIndex - split : 0, endIndex - split, depth - 1, consumer);
    }
  }
}

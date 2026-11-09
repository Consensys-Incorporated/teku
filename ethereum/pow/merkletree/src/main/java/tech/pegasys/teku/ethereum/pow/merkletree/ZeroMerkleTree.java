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
import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;

import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;

class ZeroMerkleTree extends MerkleTree {

  private static final Bytes32[] ZERO_HASHES = new Bytes32[DEPOSIT_CONTRACT_TREE_DEPTH + 1];

  static {
    ZERO_HASHES[0] = Bytes32.ZERO;
    for (int i = 1; i < ZERO_HASHES.length; i++) {
      ZERO_HASHES[i] = Hash.sha256(ZERO_HASHES[i - 1], ZERO_HASHES[i - 1]);
    }
  }

  private final int depth;

  public ZeroMerkleTree(final int depth) {
    this.depth = depth;
  }

  @Override
  public Bytes32 getRoot() {
    return ZERO_HASHES[depth];
  }

  @Override
  protected boolean isFull() {
    return false;
  }

  @Override
  protected MerkleTree pushLeaf(final Bytes32 leaf, final int level) {
    return MerkleTree.create(List.of(leaf), level);
  }

  @Override
  public MerkleTree finalize(final long depositsToFinalize, final int level) {
    checkArgument(depositsToFinalize == 0, "Attempted to finalized more deposits than are present");
    return this;
  }

  @Override
  public long getFinalized(final List<Bytes32> result) {
    return 0;
  }

  @Override
  protected MerkleTree addToProof(final List<Bytes32> proof, final long index, final int depth) {
    throw new UnsupportedOperationException("Shouldn't need to add zero nodes to proof");
  }

  @Override
  public void forEachLeafInclusive(
      final long startIndex,
      final long endIndex,
      final int depth,
      final Consumer<Bytes32> consumer) {
    // Zero leaves have no deposits to iterate.
  }
}

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

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;

class FinalizedMerkleTree extends MerkleTree {

  private final long deposits;
  private final Bytes32 root;

  public FinalizedMerkleTree(final long deposits, final Bytes32 root) {
    this.deposits = deposits;
    this.root = root;
  }

  @Override
  public Bytes32 getRoot() {
    return root;
  }

  @Override
  protected boolean isFull() {
    return true;
  }

  @Override
  protected MerkleTree pushLeaf(final Bytes32 leaf, final int level) {
    throw new UnsupportedOperationException("Cannot push leaf to finalized tree");
  }

  @Override
  public MerkleTree finalize(final long depositsToFinalize, final int level) {
    return this;
  }

  @Override
  public long getFinalized(final List<Bytes32> result) {
    result.add(root);
    return deposits;
  }

  @Override
  protected MerkleTree addToProof(final List<Bytes32> proof, final long index, final int depth) {
    throw new UnsupportedOperationException("Shouldn't need to add finalized nodes to proof");
  }

  @Override
  public void forEachLeafInclusive(
      final long startIndex,
      final long endIndex,
      final int depth,
      final Consumer<Bytes32> consumer) {
    throw new UnsupportedOperationException("Cannot iterate finalized leafs");
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("deposits", deposits).add("root", root).toString();
  }
}

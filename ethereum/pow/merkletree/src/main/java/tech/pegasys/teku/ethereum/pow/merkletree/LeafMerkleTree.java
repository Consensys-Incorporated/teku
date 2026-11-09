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

import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;

class LeafMerkleTree extends MerkleTree {
  private final Bytes32 root;

  LeafMerkleTree(final Bytes32 root) {
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
    throw new UnsupportedOperationException("Cannot push leaf to a leaf node");
  }

  @Override
  public MerkleTree finalize(final long depositsToFinalize, final int level) {
    return new FinalizedMerkleTree(1, root);
  }

  @Override
  public long getFinalized(final List<Bytes32> result) {
    return 0;
  }

  @Override
  protected MerkleTree addToProof(final List<Bytes32> proof, final long index, final int depth) {
    throw new UnsupportedOperationException("Shouldn't need to add leaf nodes to proof");
  }

  @Override
  public void forEachLeafInclusive(
      final long startIndex,
      final long endIndex,
      final int depth,
      final Consumer<Bytes32> consumer) {
    checkArgument(depth == 0, "Unexpected depth when iterating leaf node: %s", depth);
    consumer.accept(root);
  }
}

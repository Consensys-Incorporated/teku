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
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes32;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;

public class DepositTree {
  private MerkleTree tree;
  private long totalDepositCount;
  private UInt64 finalizedDepositCount;
  private Optional<BlockHashAndHeight> finalizedExecutionBlock;

  public DepositTree() {
    tree = MerkleTree.create(emptyList(), DEPOSIT_CONTRACT_TREE_DEPTH);
    totalDepositCount = 0;
    finalizedDepositCount = UInt64.ZERO;
    finalizedExecutionBlock = Optional.empty();
  }

  public DepositTree(final DepositTreeSnapshot snapshot) {
    this.tree =
        MerkleTree.fromSnapshotParts(
            snapshot.getFinalized(), snapshot.getDepositCount(), DEPOSIT_CONTRACT_TREE_DEPTH);
    checkArgument(
        calculateDepositRoot(tree.getRoot(), snapshot.getDepositCount())
            .equals(snapshot.getDepositRoot()),
        "Incorrect Deposit Tree snapshot %s, deposit root doesn't match",
        snapshot);
    this.totalDepositCount = snapshot.getDepositCount();
    this.finalizedDepositCount = UInt64.valueOf(snapshot.getDepositCount());
    this.finalizedExecutionBlock =
        Optional.of(
            new BlockHashAndHeight(
                snapshot.getExecutionBlockHash(), snapshot.getExecutionBlockHeight()));
  }

  public Optional<DepositTreeSnapshot> getSnapshot() {
    if (finalizedExecutionBlock.isEmpty()) {
      return Optional.empty();
    }
    final List<Bytes32> finalized = new ArrayList<>();
    final long depositCount = tree.getFinalized(finalized);
    return Optional.of(
        new DepositTreeSnapshot(
            finalized,
            calculateDepositRoot(finalized, depositCount),
            depositCount,
            finalizedExecutionBlock.get().getBlockHash(),
            finalizedExecutionBlock.get().getBlockHeight()));
  }

  private Bytes32 calculateDepositRoot(final List<Bytes32> finalized, final long depositCount) {
    final Bytes32 treeRoot =
        MerkleTree.fromSnapshotParts(finalized, depositCount, DEPOSIT_CONTRACT_TREE_DEPTH)
            .getRoot();
    return calculateDepositRoot(treeRoot, depositCount);
  }

  private Bytes32 calculateDepositRoot(final Bytes32 treeRoot, final long depositCount) {
    return Hash.sha256(
        treeRoot, Bytes32.rightPad(Bytes.ofUnsignedLong(depositCount, ByteOrder.LITTLE_ENDIAN)));
  }

  public static DepositTree fromSnapshot(final DepositTreeSnapshot snapshot) {
    return new DepositTree(snapshot);
  }

  public void finalize(final Eth1Data eth1Data, final UInt64 blockHeight) {
    checkArgument(
        eth1Data.getDepositCount().isLessThanOrEqualTo(totalDepositCount),
        "Merkle tree does not contain all deposits to be finalized");
    if (eth1Data.getDepositCount().equals(UInt64.ZERO)) {
      return;
    }
    finalizedExecutionBlock =
        Optional.of(new BlockHashAndHeight(eth1Data.getBlockHash(), blockHeight));
    finalizedDepositCount = eth1Data.getDepositCount();
    tree = tree.finalize(finalizedDepositCount.longValue(), DEPOSIT_CONTRACT_TREE_DEPTH);
  }

  public List<Bytes32> getProof(final long index) {
    checkArgument(
        finalizedDepositCount.isLessThanOrEqualTo(index),
        "Cannot get proof for finalized deposits");
    final List<Bytes32> proof = tree.generateProof(index, DEPOSIT_CONTRACT_TREE_DEPTH);
    proof.add(uintToBytes32(totalDepositCount));
    return proof;
  }

  public Bytes32 getRoot() {
    return Hash.sha256(tree.getRoot(), uintToBytes32(totalDepositCount));
  }

  public void pushLeaf(final Bytes32 leaf) {
    totalDepositCount++;
    tree = tree.pushLeaf(leaf, DEPOSIT_CONTRACT_TREE_DEPTH);
  }

  public long getDepositCount() {
    return totalDepositCount;
  }

  public DepositTree getTreeAtDepositIndex(final long lastDepositIndex) {
    checkArgument(
        finalizedDepositCount.isLessThanOrEqualTo(lastDepositIndex),
        "Cannot recreate tree before finalized deposit count");
    final DepositTree treeForProof =
        getSnapshot().map(DepositTree::fromSnapshot).orElseGet(DepositTree::new);
    if (lastDepositIndex > 0) {
      tree.forEachLeafInclusive(
          treeForProof.getDepositCount(),
          lastDepositIndex,
          DEPOSIT_CONTRACT_TREE_DEPTH,
          treeForProof::pushLeaf);
    }
    checkState(
        treeForProof.getDepositCount() == lastDepositIndex,
        "Expected tree to have %s deposits but actually had %s",
        lastDepositIndex,
        treeForProof.getDepositCount());
    return treeForProof;
  }

  public boolean isFinalizedWithExecutionBlock(final Bytes32 blockHash) {
    return this.finalizedExecutionBlock
        .filter(block -> block.blockHash.equals(blockHash))
        .isPresent();
  }

  private static class BlockHashAndHeight {
    private final Bytes32 blockHash;
    private final UInt64 blockHeight;

    public BlockHashAndHeight(final Bytes32 blockHash, final UInt64 blockHeight) {
      this.blockHash = blockHash;
      this.blockHeight = blockHeight;
    }

    public Bytes32 getBlockHash() {
      return blockHash;
    }

    public UInt64 getBlockHeight() {
      return blockHeight;
    }
  }
}

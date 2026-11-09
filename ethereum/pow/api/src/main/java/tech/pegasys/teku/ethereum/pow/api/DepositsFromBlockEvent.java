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

package tech.pegasys.teku.ethereum.pow.api;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositsFromBlockEvent {
  private final UInt64 blockNumber;
  private final Bytes32 blockHash;
  private final List<Deposit> deposits;
  private final UInt64 blockTimestamp;

  protected DepositsFromBlockEvent(
      final UInt64 blockNumber,
      final Bytes32 blockHash,
      final UInt64 blockTimestamp,
      final List<Deposit> deposits) {
    assertDepositsValid(deposits);
    this.blockTimestamp = blockTimestamp;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.deposits = deposits;
  }

  protected void assertDepositsValid(final List<Deposit> deposits) {
    checkArgument(!deposits.isEmpty(), "Attempting to notify no events in a block");
    if (deposits.size() <= 1) {
      return;
    }

    UInt64 previousIndex = deposits.get(0).getMerkle_tree_index();
    for (int i = 1; i < deposits.size(); i++) {
      UInt64 currentIndex = deposits.get(i).getMerkle_tree_index();
      if (!previousIndex.plus(1).equals(currentIndex)) {
        final String error =
            String.format(
                "Deposits must be ordered and contiguous. Deposit at index %s does not follow prior deposit at index %s",
                currentIndex, previousIndex);
        throw new InvalidDepositEventsException(error);
      }
      previousIndex = currentIndex;
    }
  }

  public static DepositsFromBlockEvent create(
      final UInt64 blockNumber,
      final Bytes32 blockHash,
      final UInt64 blockTimestamp,
      final Stream<Deposit> deposits) {
    final List<Deposit> sortedDeposits =
        deposits.sorted(Comparator.comparing(Deposit::getMerkle_tree_index)).toList();
    return new DepositsFromBlockEvent(blockNumber, blockHash, blockTimestamp, sortedDeposits);
  }

  public UInt64 getFirstDepositIndex() {
    return deposits.getFirst().getMerkle_tree_index();
  }

  public UInt64 getLastDepositIndex() {
    return deposits.getLast().getMerkle_tree_index();
  }

  public UInt64 getBlockNumber() {
    return blockNumber;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  public UInt64 getBlockTimestamp() {
    return blockTimestamp;
  }

  public List<Deposit> getDeposits() {
    return deposits;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DepositsFromBlockEvent that = (DepositsFromBlockEvent) o;
    return Objects.equals(blockNumber, that.blockNumber)
        && Objects.equals(blockHash, that.blockHash)
        && Objects.equals(deposits, that.deposits)
        && Objects.equals(blockTimestamp, that.blockTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockNumber, blockHash, deposits, blockTimestamp);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockNumber", blockNumber)
        .add("blockHash", blockHash)
        .add("deposits", deposits)
        .add("blockTimestamp", blockTimestamp)
        .toString();
  }
}

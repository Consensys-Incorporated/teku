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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MinGenesisTimeBlockEvent {

  private final UInt64 timestamp;
  private final UInt64 blockNumber;
  private final Bytes32 blockHash;

  public MinGenesisTimeBlockEvent(
      final UInt64 timestamp, final UInt64 blockNumber, final Bytes32 blockHash) {
    this.timestamp = timestamp;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public UInt64 getBlockNumber() {
    return blockNumber;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  @Override
  public String toString() {
    return "MinGenesisTimeBlockEvent{"
        + "timestamp="
        + timestamp
        + ", blockNumber="
        + blockNumber
        + ", blockHash="
        + blockHash
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MinGenesisTimeBlockEvent that = (MinGenesisTimeBlockEvent) o;
    return Objects.equals(timestamp, that.timestamp)
        && Objects.equals(blockNumber, that.blockNumber)
        && Objects.equals(blockHash, that.blockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, blockNumber, blockHash);
  }
}

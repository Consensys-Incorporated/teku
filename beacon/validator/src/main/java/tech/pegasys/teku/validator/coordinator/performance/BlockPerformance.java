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

package tech.pegasys.teku.validator.coordinator.performance;

import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.getPercentage;

import com.google.common.base.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockPerformance {
  final UInt64 epoch;
  final int numberOfExpectedBlocks;
  final int numberOfIncludedBlocks;
  final int numberOfProducedBlocks;

  public BlockPerformance(
      final UInt64 epoch,
      final int numberOfExpectedBlocks,
      final int numberOfIncludedBlocks,
      final int numberOfProducedBlocks) {
    this.epoch = epoch;
    this.numberOfExpectedBlocks = numberOfExpectedBlocks;
    this.numberOfIncludedBlocks = numberOfIncludedBlocks;
    this.numberOfProducedBlocks = numberOfProducedBlocks;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BlockPerformance)) {
      return false;
    }
    BlockPerformance that = (BlockPerformance) o;
    return numberOfExpectedBlocks == that.numberOfExpectedBlocks
        && numberOfIncludedBlocks == that.numberOfIncludedBlocks
        && numberOfProducedBlocks == that.numberOfProducedBlocks
        && epoch.equals(that.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        epoch, numberOfExpectedBlocks, numberOfIncludedBlocks, numberOfProducedBlocks);
  }

  @Override
  public String toString() {
    return String.format(
        "Block performance: epoch %s, expected %s, produced %s, included %s (%s%%)",
        epoch,
        numberOfExpectedBlocks,
        numberOfProducedBlocks,
        numberOfIncludedBlocks,
        getPercentage(numberOfIncludedBlocks, numberOfProducedBlocks));
  }
}

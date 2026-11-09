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

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteePerformance {
  private final UInt64 epoch;
  private final int numberOfExpectedMessages;
  private final int numberOfProducedMessages;
  private final int numberOfCorrectMessages;
  private final int numberOfIncludedMessages;

  public SyncCommitteePerformance(
      final UInt64 epoch,
      final int numberOfExpectedMessages,
      final int numberOfProducedMessages,
      final int numberOfCorrectMessages,
      final int numberOfIncludedMessages) {
    this.epoch = epoch;
    this.numberOfExpectedMessages = numberOfExpectedMessages;
    this.numberOfProducedMessages = numberOfProducedMessages;
    this.numberOfCorrectMessages = numberOfCorrectMessages;
    this.numberOfIncludedMessages = numberOfIncludedMessages;
  }

  public int getNumberOfExpectedMessages() {
    return numberOfExpectedMessages;
  }

  public int getNumberOfProducedMessages() {
    return numberOfProducedMessages;
  }

  public int getNumberOfCorrectMessages() {
    return numberOfCorrectMessages;
  }

  public int getNumberOfIncludedMessages() {
    return numberOfIncludedMessages;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncCommitteePerformance that = (SyncCommitteePerformance) o;
    return numberOfExpectedMessages == that.numberOfExpectedMessages
        && numberOfProducedMessages == that.numberOfProducedMessages
        && numberOfCorrectMessages == that.numberOfCorrectMessages
        && numberOfIncludedMessages == that.numberOfIncludedMessages
        && epoch.equals(that.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        epoch,
        numberOfExpectedMessages,
        numberOfProducedMessages,
        numberOfCorrectMessages,
        numberOfIncludedMessages);
  }

  @Override
  public String toString() {
    return String.format(
        "Sync committee performance: "
            + "epoch %s, expected %s, produced %s, correct %s, included %s (%s%%)",
        epoch,
        numberOfExpectedMessages,
        numberOfProducedMessages,
        numberOfCorrectMessages,
        numberOfIncludedMessages,
        getPercentage(numberOfIncludedMessages, numberOfProducedMessages));
  }
}

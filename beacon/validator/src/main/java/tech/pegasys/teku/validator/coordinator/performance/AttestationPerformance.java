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

public class AttestationPerformance {
  final UInt64 epoch;
  final int numberOfExpectedAttestations;
  final int numberOfProducedAttestations;
  final int numberOfIncludedAttestations;
  final int inclusionDistanceMax;
  final int inclusionDistanceMin;
  final double inclusionDistanceAverage;
  final int correctTargetCount;
  final int correctHeadBlockCount;

  public AttestationPerformance(
      final UInt64 epoch,
      final int numberOfExpectedAttestations,
      final int numberOfProducedAttestations,
      final int numberOfIncludedAttestations,
      final int inclusionDistanceMax,
      final int inclusionDistanceMin,
      final double inclusionDistanceAverage,
      final int correctTargetCount,
      final int correctHeadBlockCount) {
    this.epoch = epoch;
    this.numberOfExpectedAttestations = numberOfExpectedAttestations;
    this.numberOfProducedAttestations = numberOfProducedAttestations;
    this.numberOfIncludedAttestations = numberOfIncludedAttestations;
    this.inclusionDistanceMax = inclusionDistanceMax;
    this.inclusionDistanceMin = inclusionDistanceMin;
    this.inclusionDistanceAverage = inclusionDistanceAverage;
    this.correctTargetCount = correctTargetCount;
    this.correctHeadBlockCount = correctHeadBlockCount;
  }

  public static AttestationPerformance empty(
      final UInt64 epoch, final int numberOfExpectedAttestations) {
    return new AttestationPerformance(epoch, numberOfExpectedAttestations, 0, 0, 0, 0, 0, 0, 0);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttestationPerformance)) {
      return false;
    }
    AttestationPerformance that = (AttestationPerformance) o;
    return numberOfExpectedAttestations == that.numberOfExpectedAttestations
        && numberOfProducedAttestations == that.numberOfProducedAttestations
        && numberOfIncludedAttestations == that.numberOfIncludedAttestations
        && inclusionDistanceMax == that.inclusionDistanceMax
        && inclusionDistanceMin == that.inclusionDistanceMin
        && Double.compare(that.inclusionDistanceAverage, inclusionDistanceAverage) == 0
        && Double.compare(that.correctTargetCount, correctTargetCount) == 0
        && Double.compare(that.correctHeadBlockCount, correctHeadBlockCount) == 0
        && epoch.equals(that.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        epoch,
        numberOfExpectedAttestations,
        numberOfProducedAttestations,
        numberOfIncludedAttestations,
        inclusionDistanceMax,
        inclusionDistanceMin,
        inclusionDistanceAverage,
        correctTargetCount,
        correctHeadBlockCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Attestation performance: ");
    sb.append(
        String.format(
            "epoch %s, expected %s, produced %s, included %s (%s%%), ",
            epoch,
            numberOfExpectedAttestations,
            numberOfProducedAttestations,
            numberOfIncludedAttestations,
            getPercentage(numberOfIncludedAttestations, numberOfProducedAttestations)));

    if (numberOfIncludedAttestations != 0) {
      sb.append(
          String.format(
              "distance %s / %.2f / %s, ",
              inclusionDistanceMin, inclusionDistanceAverage, inclusionDistanceMax));
    }

    sb.append(
        String.format(
            "correct target %s (%s%%), correct head %s (%s%%)",
            correctTargetCount,
            getPercentage(correctTargetCount, numberOfProducedAttestations),
            correctHeadBlockCount,
            getPercentage(correctHeadBlockCount, numberOfProducedAttestations)));

    return sb.toString();
  }
}

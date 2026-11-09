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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public class TotalBalances {

  private final UInt64 currentEpochActiveValidators;
  private final UInt64 previousEpochActiveValidators;
  private final UInt64 currentEpochSourceAttesters;
  private final UInt64 currentEpochTargetAttesters;
  private final UInt64 currentEpochHeadAttesters;
  private final UInt64 previousEpochSourceAttesters;
  private final UInt64 previousEpochTargetAttesters;
  private final UInt64 previousEpochHeadAttesters;
  private final UInt64 effectiveBalanceIncrement;

  public TotalBalances(
      final SpecConfig specConfig,
      final UInt64 currentEpochActiveValidators,
      final UInt64 previousEpochActiveValidators,
      final UInt64 currentEpochSourceAttesters,
      final UInt64 currentEpochTargetAttesters,
      final UInt64 currentEpochHeadAttesters,
      final UInt64 previousEpochSourceAttesters,
      final UInt64 previousEpochTargetAttesters,
      final UInt64 previousEpochHeadAttesters) {
    this.effectiveBalanceIncrement = specConfig.getEffectiveBalanceIncrement();
    this.currentEpochActiveValidators = currentEpochActiveValidators;
    this.previousEpochActiveValidators = previousEpochActiveValidators;
    this.currentEpochSourceAttesters = currentEpochSourceAttesters;
    this.currentEpochTargetAttesters = currentEpochTargetAttesters;
    this.currentEpochHeadAttesters = currentEpochHeadAttesters;
    this.previousEpochSourceAttesters = previousEpochSourceAttesters;
    this.previousEpochTargetAttesters = previousEpochTargetAttesters;
    this.previousEpochHeadAttesters = previousEpochHeadAttesters;
  }

  /**
   * @return The sum of effective balances of all active validators from the current epoch.
   */
  public UInt64 getCurrentEpochActiveValidators() {
    return currentEpochActiveValidators.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all active validators from the previous epoch.
   */
  public UInt64 getPreviousEpochActiveValidators() {
    return previousEpochActiveValidators.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getCurrentEpochSourceAttesters() {
    return currentEpochSourceAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getCurrentEpochTargetAttesters() {
    return currentEpochTargetAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct head.
   */
  public UInt64 getCurrentEpochHeadAttesters() {
    return currentEpochHeadAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getPreviousEpochSourceAttesters() {
    return previousEpochSourceAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getPreviousEpochTargetAttesters() {
    return previousEpochTargetAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct head block at their assigned slot.
   */
  public UInt64 getPreviousEpochHeadAttesters() {
    return previousEpochHeadAttesters.max(effectiveBalanceIncrement);
  }

  UInt64 getRawCurrentEpochActiveValidators() {
    return currentEpochActiveValidators;
  }

  UInt64 getRawPreviousEpochActiveValidators() {
    return previousEpochActiveValidators;
  }

  UInt64 getRawCurrentEpochSourceAttesters() {
    return currentEpochSourceAttesters;
  }

  UInt64 getRawCurrentEpochTargetAttesters() {
    return currentEpochTargetAttesters;
  }

  UInt64 getRawCurrentEpochHeadAttesters() {
    return currentEpochHeadAttesters;
  }

  UInt64 getRawPreviousEpochSourceAttesters() {
    return previousEpochSourceAttesters;
  }

  UInt64 getRawPreviousEpochTargetAttesters() {
    return previousEpochTargetAttesters;
  }

  UInt64 getRawPreviousEpochHeadAttesters() {
    return previousEpochHeadAttesters;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TotalBalances that = (TotalBalances) o;
    return Objects.equals(currentEpochActiveValidators, that.currentEpochActiveValidators)
        && Objects.equals(previousEpochActiveValidators, that.previousEpochActiveValidators)
        && Objects.equals(currentEpochSourceAttesters, that.currentEpochSourceAttesters)
        && Objects.equals(currentEpochTargetAttesters, that.currentEpochTargetAttesters)
        && Objects.equals(currentEpochHeadAttesters, that.currentEpochHeadAttesters)
        && Objects.equals(previousEpochSourceAttesters, that.previousEpochSourceAttesters)
        && Objects.equals(previousEpochTargetAttesters, that.previousEpochTargetAttesters)
        && Objects.equals(previousEpochHeadAttesters, that.previousEpochHeadAttesters)
        && Objects.equals(effectiveBalanceIncrement, that.effectiveBalanceIncrement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        currentEpochActiveValidators,
        previousEpochActiveValidators,
        currentEpochSourceAttesters,
        currentEpochTargetAttesters,
        currentEpochHeadAttesters,
        previousEpochSourceAttesters,
        previousEpochTargetAttesters,
        previousEpochHeadAttesters,
        effectiveBalanceIncrement);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentEpochActiveValidators", currentEpochActiveValidators)
        .add("previousEpochActiveValidators", previousEpochActiveValidators)
        .add("currentEpochSourceAttesters", currentEpochSourceAttesters)
        .add("currentEpochTargetAttesters", currentEpochTargetAttesters)
        .add("currentEpochHeadAttesters", currentEpochHeadAttesters)
        .add("previousEpochSourceAttesters", previousEpochSourceAttesters)
        .add("previousEpochTargetAttesters", previousEpochTargetAttesters)
        .add("previousEpochHeadAttesters", previousEpochHeadAttesters)
        .add("effectiveBalanceIncrement", effectiveBalanceIncrement)
        .toString();
  }
}

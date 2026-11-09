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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorStatus {
  private final UInt64 currentEpochEffectiveBalance;
  final UInt64 withdrawableEpoch;
  private final boolean slashed;
  private final boolean withdrawableInCurrentEpoch;
  private final boolean activeInCurrentEpoch;
  private final boolean activeInPreviousEpoch;
  private final boolean activeInNextEpoch;

  private boolean currentEpochSourceAttester = false;
  private boolean currentEpochTargetAttester = false;
  private boolean currentEpochHeadAttester = false;
  private boolean previousEpochSourceAttester = false;
  private boolean previousEpochTargetAttester = false;
  private boolean previousEpochHeadAttester = false;

  private Optional<InclusionInfo> inclusionInfo = Optional.empty();

  public ValidatorStatus(
      final boolean slashed,
      final boolean withdrawableInCurrentEpoch,
      final UInt64 currentEpochEffectiveBalance,
      final UInt64 withdrawableEpoch,
      final boolean activeInCurrentEpoch,
      final boolean activeInPreviousEpoch,
      final boolean activeInNextEpoch) {
    this.slashed = slashed;
    this.withdrawableInCurrentEpoch = withdrawableInCurrentEpoch;
    this.currentEpochEffectiveBalance = currentEpochEffectiveBalance;
    this.withdrawableEpoch = withdrawableEpoch;
    this.activeInCurrentEpoch = activeInCurrentEpoch;
    this.activeInPreviousEpoch = activeInPreviousEpoch;
    this.activeInNextEpoch = activeInNextEpoch;
  }

  public boolean isEligibleValidator() {
    return activeInPreviousEpoch || (slashed && !withdrawableInCurrentEpoch);
  }

  public boolean isSlashed() {
    return slashed;
  }

  public boolean isNotSlashed() {
    return !slashed;
  }

  /**
   * @return True if the validators withdrawable epoch is less than or equal to the current epoch.
   */
  public boolean isWithdrawableInCurrentEpoch() {
    return withdrawableInCurrentEpoch;
  }

  /**
   * @return True if the validator is "active" in this epoch. In other words the current epoch is in
   *     the range between the validator's activation epoch (inclusive) and exit epoch (exclusive).
   */
  public boolean isActiveInCurrentEpoch() {
    return activeInCurrentEpoch;
  }

  /**
   * @return True if the validator is "active" in the previous epoch. In other words the previous
   *     epoch is in the range between the validator's activation epoch (inclusive) and exit epoch
   *     (exclusive).
   */
  public boolean isActiveInPreviousEpoch() {
    return activeInPreviousEpoch;
  }

  /**
   * @return True if the validator is "active" in the next epoch. In other words the next epoch is
   *     in the range between the validator's activation epoch (inclusive) and exit epoch
   *     (exclusive).
   */
  public boolean isActiveInNextEpoch() {
    return activeInNextEpoch;
  }

  public UInt64 getCurrentEpochEffectiveBalance() {
    return currentEpochEffectiveBalance;
  }

  /**
   * @return True if the validator correctly attested to the correct source (justified checkpoint)
   *     in the current epoch.
   */
  public boolean isCurrentEpochSourceAttester() {
    return currentEpochSourceAttester;
  }

  /**
   * @return True if the validator correctly attested to the correct target (epoch boundary block)
   *     in the current epoch.
   */
  public boolean isCurrentEpochTargetAttester() {
    return currentEpochTargetAttester;
  }

  /**
   * @return True if the validator correctly attested to the correct head in the current epoch.
   */
  public boolean isCurrentEpochHeadAttester() {
    return currentEpochHeadAttester;
  }

  /**
   * @return True if the validator correctly attested to the correct source (justified checkpoint)
   *     in the previous epoch.
   */
  public boolean isPreviousEpochSourceAttester() {
    return previousEpochSourceAttester;
  }

  /**
   * @return True if the validator correctly attested to the correct target (epoch boundary block)
   *     in the previous epoch.
   */
  public boolean isPreviousEpochTargetAttester() {
    return previousEpochTargetAttester;
  }

  /**
   * @return True if the validator attested to the correct head block at the assigned slot in the
   *     previous epoch.
   */
  public boolean isPreviousEpochHeadAttester() {
    return previousEpochHeadAttester;
  }

  /**
   * @return If available, return information on the distance in slots between the time an
   *     attestation was produced, and when it was included in a block.
   */
  public Optional<InclusionInfo> getInclusionInfo() {
    return inclusionInfo;
  }

  public ValidatorStatus updateCurrentEpochSourceAttester(final boolean currentEpochAttester) {
    this.currentEpochSourceAttester |= currentEpochAttester;
    return this;
  }

  public ValidatorStatus updateCurrentEpochTargetAttester(
      final boolean currentEpochTargetAttester) {
    this.currentEpochTargetAttester |= currentEpochTargetAttester;
    return this;
  }

  public ValidatorStatus updateCurrentEpochHeadAttester(final boolean currentEpochHeadAttester) {
    this.currentEpochHeadAttester |= currentEpochHeadAttester;
    return this;
  }

  public ValidatorStatus updatePreviousEpochSourceAttester(final boolean previousEpochAttester) {
    this.previousEpochSourceAttester |= previousEpochAttester;
    return this;
  }

  public ValidatorStatus updatePreviousEpochTargetAttester(
      final boolean previousEpochTargetAttester) {
    this.previousEpochTargetAttester |= previousEpochTargetAttester;
    return this;
  }

  public ValidatorStatus updatePreviousEpochHeadAttester(final boolean previousEpochHeadAttester) {
    this.previousEpochHeadAttester |= previousEpochHeadAttester;
    return this;
  }

  public ValidatorStatus updateInclusionInfo(final Optional<InclusionInfo> inclusionInfo) {
    if (inclusionInfo.isEmpty()) {
      return this;
    }
    if (this.inclusionInfo.isEmpty()) {
      this.inclusionInfo = inclusionInfo;
    } else {
      // Both are present, take the one with the smallest distance
      if (inclusionInfo.get().getDelay().isLessThan(this.inclusionInfo.get().getDelay())) {
        this.inclusionInfo = inclusionInfo;
      }
    }
    return this;
  }

  public UInt64 getWithdrawableEpoch() {
    return withdrawableEpoch;
  }
}

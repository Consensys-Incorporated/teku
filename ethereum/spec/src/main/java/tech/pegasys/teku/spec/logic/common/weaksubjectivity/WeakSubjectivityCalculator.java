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

package tech.pegasys.teku.spec.logic.common.weaksubjectivity;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

/**
 * Computes weak-subjectivity period values according to the <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/weak-subjectivity.md">Phase0
 * -- Weak Subjectivity Guide</a>.
 */
public class WeakSubjectivityCalculator {

  public static final UInt64 SAFETY_DECAY = UInt64.valueOf(10);

  protected final SpecConfig specConfig;
  private final BeaconStateAccessors beaconStateAccessors;
  private final MiscHelpers miscHelpers;

  public WeakSubjectivityCalculator(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  /**
   * is_within_weak_subjectivity_period
   *
   * <p>Determines whether the weak subjectivity period computed for the state extends through the
   * current epoch.
   */
  public boolean isWithinWeakSubjectivityPeriod(
      final BeaconState wsState, final UInt64 currentSlot) {
    final UInt64 wsPeriod = computeWeakSubjectivityPeriod(wsState);
    final UInt64 wsStateEpoch = miscHelpers.computeEpochAtSlot(wsState.getSlot());
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(currentSlot);

    return currentEpoch.isLessThanOrEqualTo(wsStateEpoch.plus(wsPeriod));
  }

  /**
   * compute_weak_subjectivity_period
   *
   * <p>Returns the weak subjectivity period for the state
   */
  public UInt64 computeWeakSubjectivityPeriod(final BeaconState state) {
    final int activeValidatorCount =
        beaconStateAccessors
            .getActiveValidatorIndices(state, beaconStateAccessors.getCurrentEpoch(state))
            .size();
    final UInt64 totalActiveValidatorBalance = beaconStateAccessors.getTotalActiveBalance(state);
    return computeWeakSubjectivityPeriod(
        activeValidatorCount, totalActiveValidatorBalance, SAFETY_DECAY);
  }

  @VisibleForTesting
  @SuppressWarnings("JavaCase") // Mathematicians...
  UInt64 computeWeakSubjectivityPeriod(
      final int activeValidatorCount,
      final UInt64 totalValidatorBalance,
      final UInt64 safetyDecay) {
    final UInt64 N = UInt64.valueOf(activeValidatorCount);
    final UInt64 t = totalValidatorBalance.dividedBy(N).dividedBy(EthConstants.ETH_TO_GWEI);
    final UInt64 T = specConfig.getMaxEffectiveBalance().dividedBy(EthConstants.ETH_TO_GWEI);
    final UInt64 delta = beaconStateAccessors.getValidatorChurnLimit(activeValidatorCount);
    final UInt64 Delta =
        UInt64.valueOf(specConfig.getMaxDeposits()).times(specConfig.getSlotsPerEpoch());
    final UInt64 D = safetyDecay;

    final UInt64 wsPeriod;
    final UInt64 maxBalanceMultiplier = D.times(3).plus(200);
    final UInt64 scaledMaxBalance = T.times(maxBalanceMultiplier);
    final UInt64 scaledAverageBalance = t.times(D.times(12).plus(200));
    if (scaledMaxBalance.isLessThan(scaledAverageBalance)) {
      final UInt64 churnDivisor = delta.times(600).times(t.times(2).plus(T));
      final UInt64 epochsForValidatorChurnSet =
          N.times(scaledAverageBalance.minus(scaledMaxBalance)).dividedBy(churnDivisor);
      final UInt64 epochsForBalanceTopUps =
          N.times(maxBalanceMultiplier).dividedBy(Delta.times(600));

      wsPeriod = epochsForValidatorChurnSet.max(epochsForBalanceTopUps);
    } else {
      final UInt64 divisor = Delta.times(200).times(T.minus(t));
      wsPeriod = N.times(D).times(t).times(3).dividedBy(divisor);
    }

    return wsPeriod.plus(specConfig.getMinValidatorWithdrawabilityDelay());
  }
}

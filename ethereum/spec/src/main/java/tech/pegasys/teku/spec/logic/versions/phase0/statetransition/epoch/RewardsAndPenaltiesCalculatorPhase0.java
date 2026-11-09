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

package tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch;

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.InclusionInfo;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;

public class RewardsAndPenaltiesCalculatorPhase0 extends RewardsAndPenaltiesCalculator {

  public RewardsAndPenaltiesCalculatorPhase0(
      final SpecConfig specConfig,
      final BeaconState state,
      final ValidatorStatuses validatorStatuses,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors, state, validatorStatuses);
  }

  /** Return attestation reward/penalty deltas for each validator */
  @Override
  public RewardAndPenaltyDeltas getDeltas() throws IllegalArgumentException {
    return getDeltas(this::applyAllDeltas);
  }

  public RewardAndPenaltyDeltas getDeltas(final Step step) throws IllegalArgumentException {
    final RewardAndPenaltyDeltas deltas =
        RewardAndPenaltyDeltas.aggregated(validatorStatuses.getValidatorCount());
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();
    final List<ValidatorStatus> statuses = validatorStatuses.getStatuses();
    final UInt64 finalityDelay = getFinalityDelay();

    final UInt64 totalActiveBalanceSquareRoot =
        squareRootOrZero(totalBalances.getCurrentEpochActiveValidators());

    for (int index = 0; index < statuses.size(); index++) {
      final ValidatorStatus validator = statuses.get(index);
      if (!validator.isEligibleValidator()) {
        continue;
      }

      final UInt64 baseReward = getBaseReward(validator, totalActiveBalanceSquareRoot);
      final RewardAndPenalty delta = deltas.getDelta(index);
      step.apply(deltas, totalBalances, finalityDelay, validator, baseReward, delta);
    }
    return deltas;
  }

  @Override
  public RewardAndPenaltyDeltas getDetailedDeltas() throws IllegalArgumentException {
    throw new UnsupportedOperationException(
        "Can't calculate detailed RewardAndPenaltyDeltas pre-Altair");
  }

  private void applyAllDeltas(
      final RewardAndPenaltyDeltas deltas,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final RewardAndPenalty delta) {
    applySourceDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyTargetDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyHeadDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyInclusionDelayDelta(validator, baseReward, delta, deltas);
    applyInactivityPenaltyDelta(validator, baseReward, finalityDelay, delta);
  }

  public void applySourceDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final RewardAndPenalty delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochSourceAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochSourceAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        RewardComponent.SOURCE,
        delta);
  }

  public void applyTargetDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final RewardAndPenalty delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochTargetAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochTargetAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        RewardComponent.TARGET,
        delta);
  }

  public void applyHeadDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final RewardAndPenalty delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochHeadAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochHeadAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        RewardComponent.HEAD,
        delta);
  }

  public void applyInclusionDelayDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final RewardAndPenalty delta,
      final RewardAndPenaltyDeltas deltas) {
    if (validator.isPreviousEpochSourceAttester() && !validator.isSlashed()) {
      final InclusionInfo inclusionInfo =
          validator
              .getInclusionInfo()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Validator was active in previous epoch but has no inclusion information."));
      final UInt64 proposerReward = getProposerReward(baseReward);
      final UInt64 maxAttesterReward = baseReward.minus(proposerReward);
      delta.reward(
          RewardComponent.INCLUSION_DELAY, maxAttesterReward.dividedBy(inclusionInfo.getDelay()));

      deltas
          .getDelta(inclusionInfo.getProposerIndex())
          .reward(RewardComponent.INCLUSION_DELAY, getProposerReward(baseReward));
    }
  }

  public void applyInactivityPenaltyDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final UInt64 finalityDelay,
      final RewardAndPenalty delta) {
    if (isInactivityLeak(finalityDelay)) {
      // If validator is performing optimally this cancels all rewards for a neutral balance
      delta.penalize(
          RewardComponent.INACTIVITY,
          specConfig
              .getBaseRewardsPerEpoch()
              .times(baseReward)
              .minus(getProposerReward(baseReward)));

      if (validator.isSlashed() || !validator.isPreviousEpochTargetAttester()) {
        delta.penalize(
            RewardComponent.INACTIVITY,
            validator
                .getCurrentEpochEffectiveBalance()
                .times(finalityDelay)
                .dividedBy(specConfig.getInactivityPenaltyQuotient()));
      }
    }
  }

  private UInt64 getProposerReward(final UInt64 baseReward) {
    return baseReward.dividedBy(specConfig.getProposerRewardQuotient());
  }

  public void applyAttestationComponentDelta(
      final boolean indexInUnslashedAttestingIndices,
      final UInt64 attestingBalance,
      final TotalBalances totalBalances,
      final UInt64 baseReward,
      final UInt64 finalityDelay,
      final RewardComponent component,
      final RewardAndPenalty delta) {
    final UInt64 totalBalance = totalBalances.getCurrentEpochActiveValidators();
    if (indexInUnslashedAttestingIndices) {
      if (finalityDelay.isGreaterThan(specConfig.getMinEpochsToInactivityPenalty())) {
        // Since full base reward will be canceled out by inactivity penalty deltas,
        // optimal participation receives full base reward compensation here.
        delta.reward(component, baseReward);
      } else {
        final UInt64 rewardNumerator =
            baseReward.times(attestingBalance.dividedBy(specConfig.getEffectiveBalanceIncrement()));
        delta.reward(
            component,
            rewardNumerator.dividedBy(
                totalBalance.dividedBy(specConfig.getEffectiveBalanceIncrement())));
      }
    } else {
      delta.penalize(component, baseReward);
    }
  }

  private UInt64 getBaseReward(
      final ValidatorStatus validator, final UInt64 totalActiveBalanceSquareRoot) {
    if (totalActiveBalanceSquareRoot.isZero()) {
      return UInt64.ZERO;
    }
    return validator
        .getCurrentEpochEffectiveBalance()
        .times(specConfig.getBaseRewardFactor())
        .dividedBy(totalActiveBalanceSquareRoot)
        .dividedBy(specConfig.getBaseRewardsPerEpoch());
  }

  private UInt64 squareRootOrZero(final UInt64 totalActiveBalance) {
    return totalActiveBalance.isZero()
        ? UInt64.ZERO
        : MathHelpers.integerSquareRoot(totalActiveBalance);
  }

  public interface Step {

    void apply(
        final RewardAndPenaltyDeltas deltas,
        final TotalBalances totalBalances,
        final UInt64 finalityDelay,
        final ValidatorStatus validator,
        final UInt64 baseReward,
        final RewardAndPenalty delta);
  }
}

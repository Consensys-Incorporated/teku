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

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;

public class ProgressiveTotalBalancesAltair implements ProgressiveTotalBalancesUpdates {

  private final MiscHelpersAltair miscHelpers;

  private UInt64 currentEpochActiveValidators;
  private UInt64 previousEpochActiveValidators;
  private UInt64 currentEpochSourceAttesters;
  private UInt64 currentEpochTargetAttesters;
  private UInt64 currentEpochHeadAttesters;
  private UInt64 previousEpochSourceAttesters;
  private UInt64 previousEpochTargetAttesters;
  private UInt64 previousEpochHeadAttesters;

  public ProgressiveTotalBalancesAltair(
      final MiscHelpersAltair miscHelpers, final TotalBalances totalBalances) {
    this.miscHelpers = miscHelpers;
    this.currentEpochActiveValidators = totalBalances.getRawCurrentEpochActiveValidators();
    this.previousEpochActiveValidators = totalBalances.getRawPreviousEpochActiveValidators();
    this.currentEpochSourceAttesters = totalBalances.getRawCurrentEpochSourceAttesters();
    this.currentEpochTargetAttesters = totalBalances.getRawCurrentEpochTargetAttesters();
    this.currentEpochHeadAttesters = totalBalances.getRawCurrentEpochHeadAttesters();
    this.previousEpochSourceAttesters = totalBalances.getRawPreviousEpochSourceAttesters();
    this.previousEpochTargetAttesters = totalBalances.getRawPreviousEpochTargetAttesters();
    this.previousEpochHeadAttesters = totalBalances.getRawPreviousEpochHeadAttesters();
  }

  private ProgressiveTotalBalancesAltair(
      final MiscHelpersAltair miscHelpers,
      final UInt64 currentEpochActiveValidators,
      final UInt64 previousEpochActiveValidators,
      final UInt64 currentEpochSourceAttesters,
      final UInt64 currentEpochTargetAttesters,
      final UInt64 currentEpochHeadAttesters,
      final UInt64 previousEpochSourceAttesters,
      final UInt64 previousEpochTargetAttesters,
      final UInt64 previousEpochHeadAttesters) {
    this.miscHelpers = miscHelpers;
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
   * To be called when processing an attestation results in setting a new flag for a validator.
   *
   * <p>Updates the current or previous epoch attesters totals to include the new validator
   * effective balance.
   */
  @Override
  public void onAttestation(
      final Validator validator,
      final boolean currentEpoch,
      final boolean newSourceAttester,
      final boolean newTargetAttester,
      final boolean newHeadAttester) {
    if (validator.isSlashed()) {
      return;
    }
    final UInt64 effectiveBalance = validator.getEffectiveBalance();
    if (currentEpoch) {
      if (newSourceAttester) {
        currentEpochSourceAttesters = currentEpochSourceAttesters.plus(effectiveBalance);
      }
      if (newTargetAttester) {
        currentEpochTargetAttesters = currentEpochTargetAttesters.plus(effectiveBalance);
      }
      if (newHeadAttester) {
        currentEpochHeadAttesters = currentEpochHeadAttesters.plus(effectiveBalance);
      }
    } else {
      if (newSourceAttester) {
        previousEpochSourceAttesters = previousEpochSourceAttesters.plus(effectiveBalance);
      }
      if (newTargetAttester) {
        previousEpochTargetAttesters = previousEpochTargetAttesters.plus(effectiveBalance);
      }
      if (newHeadAttester) {
        previousEpochHeadAttesters = previousEpochHeadAttesters.plus(effectiveBalance);
      }
    }
  }

  /**
   * Reduce the current and previous epoch attesters values based on the participation flags of the
   * slashed validator.
   */
  @Override
  public void onSlashing(final BeaconState state, final int validatorIndex) {
    final BeaconStateAltair altairState = BeaconStateAltair.required(state);
    final byte currentParticipationFlags =
        altairState.getCurrentEpochParticipation().get(validatorIndex).get();
    final byte previousParticipationFlags =
        altairState.getPreviousEpochParticipation().get(validatorIndex).get();
    final UInt64 effectiveBalance =
        altairState.getValidators().get(validatorIndex).getEffectiveBalance();
    if (miscHelpers.hasFlag(
        previousParticipationFlags, ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX)) {
      previousEpochSourceAttesters = previousEpochSourceAttesters.minus(effectiveBalance);
    }
    if (miscHelpers.hasFlag(
        previousParticipationFlags, ParticipationFlags.TIMELY_TARGET_FLAG_INDEX)) {
      previousEpochTargetAttesters = previousEpochTargetAttesters.minus(effectiveBalance);
    }
    if (miscHelpers.hasFlag(
        previousParticipationFlags, ParticipationFlags.TIMELY_HEAD_FLAG_INDEX)) {
      previousEpochHeadAttesters = previousEpochHeadAttesters.minus(effectiveBalance);
    }

    if (miscHelpers.hasFlag(
        currentParticipationFlags, ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX)) {
      currentEpochSourceAttesters = currentEpochSourceAttesters.minus(effectiveBalance);
    }
    if (miscHelpers.hasFlag(
        currentParticipationFlags, ParticipationFlags.TIMELY_TARGET_FLAG_INDEX)) {
      currentEpochTargetAttesters = currentEpochTargetAttesters.minus(effectiveBalance);
    }
    if (miscHelpers.hasFlag(currentParticipationFlags, ParticipationFlags.TIMELY_HEAD_FLAG_INDEX)) {
      currentEpochHeadAttesters = currentEpochHeadAttesters.minus(effectiveBalance);
    }
  }

  /**
   * To be called at the start of processing an epoch transition to prepare the balances to start
   * recording values for the next epoch.
   *
   * <p>Previous epoch values are set to the current epoch values, and current epoch values are set
   * to zero, except for currentEpochActiveValidators which is updated based on the validators
   * activating and exiting in the next epoch.
   *
   * @param validatorStatuses the validator statuses prepared as part of the epoch transition.
   */
  @Override
  public void onEpochTransition(final List<ValidatorStatus> validatorStatuses) {
    previousEpochActiveValidators = currentEpochActiveValidators;

    previousEpochSourceAttesters = currentEpochSourceAttesters;
    previousEpochTargetAttesters = currentEpochTargetAttesters;
    previousEpochHeadAttesters = currentEpochHeadAttesters;

    currentEpochSourceAttesters = UInt64.ZERO;
    currentEpochTargetAttesters = UInt64.ZERO;
    currentEpochHeadAttesters = UInt64.ZERO;

    handleActivatingAndExitingValidators(validatorStatuses);
  }

  private void handleActivatingAndExitingValidators(final List<ValidatorStatus> validatorStatuses) {
    for (ValidatorStatus status : validatorStatuses) {
      if (status.isActiveInCurrentEpoch() && !status.isActiveInNextEpoch()) {
        // Exiting validator
        currentEpochActiveValidators =
            currentEpochActiveValidators.minus(status.getCurrentEpochEffectiveBalance());
      } else if (!status.isActiveInCurrentEpoch() && status.isActiveInNextEpoch()) {
        // Activating validator
        currentEpochActiveValidators =
            currentEpochActiveValidators.plus(status.getCurrentEpochEffectiveBalance());
      }
    }
  }

  /**
   * Called during epoch transition when a validator's effective balance changes.
   *
   * <p>Note that effective balance changes occur during epoch transition, so when this is called
   * we're actually adjusting current values to be suitable for next and previous for current.
   */
  @Override
  public void onEffectiveBalanceChange(
      final ValidatorStatus status, final UInt64 newEffectiveBalance) {
    final UInt64 oldEffectiveBalance = status.getCurrentEpochEffectiveBalance();
    // Only need to adjust the current balance if active in the next (becoming current) epoch
    if (status.isActiveInNextEpoch()) {
      currentEpochActiveValidators =
          currentEpochActiveValidators.plus(newEffectiveBalance).minus(oldEffectiveBalance);
    }
    if (!status.isActiveInCurrentEpoch()) {
      // No need to adjust previous balance if not active in the current (becoming previous) epoch
      return;
    }
    // The current and previous epoch totals both use effective balances from the current epoch,
    // the difference is just which validators are included.
    // While this makes the previous value slightly inaccurate, it's only used for metrics and the
    // previous effective balance of validators isn't available from the state.
    previousEpochActiveValidators =
        previousEpochActiveValidators.plus(newEffectiveBalance).minus(oldEffectiveBalance);
    if (status.isSlashed()) {
      return;
    }
    // Again, current epoch is becoming the previous, so if attesting in current, update previous
    if (status.isCurrentEpochSourceAttester()) {
      previousEpochSourceAttesters =
          previousEpochSourceAttesters.plus(newEffectiveBalance).minus(oldEffectiveBalance);
    }
    if (status.isCurrentEpochTargetAttester()) {
      previousEpochTargetAttesters =
          previousEpochTargetAttesters.plus(newEffectiveBalance).minus(oldEffectiveBalance);
    }
    if (status.isCurrentEpochHeadAttester()) {
      previousEpochHeadAttesters =
          previousEpochHeadAttesters.plus(newEffectiveBalance).minus(oldEffectiveBalance);
    }
  }

  @Override
  public ProgressiveTotalBalancesUpdates copy() {
    return new ProgressiveTotalBalancesAltair(
        miscHelpers,
        currentEpochActiveValidators,
        previousEpochActiveValidators,
        currentEpochSourceAttesters,
        currentEpochTargetAttesters,
        currentEpochHeadAttesters,
        previousEpochSourceAttesters,
        previousEpochTargetAttesters,
        previousEpochHeadAttesters);
  }

  @Override
  public Optional<TotalBalances> getTotalBalances(final SpecConfig specConfig) {
    return Optional.of(
        new TotalBalances(
            specConfig,
            currentEpochActiveValidators,
            previousEpochActiveValidators,
            currentEpochSourceAttesters,
            currentEpochTargetAttesters,
            currentEpochHeadAttesters,
            previousEpochSourceAttesters,
            previousEpochTargetAttesters,
            previousEpochHeadAttesters));
  }
}

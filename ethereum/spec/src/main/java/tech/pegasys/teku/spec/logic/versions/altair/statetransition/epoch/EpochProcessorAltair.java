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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ProgressiveTotalBalancesAltair;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class EpochProcessorAltair extends AbstractEpochProcessor {

  private volatile SszList<SszByte> zeroParticipationFlags = null;

  private final SpecConfigAltair specConfigAltair;
  protected final MiscHelpersAltair miscHelpersAltair;
  protected final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  public EpochProcessorAltair(
      final SpecConfigAltair specConfig,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.specConfigAltair = specConfig;
    this.miscHelpersAltair = miscHelpers;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState genericState,
      final ValidatorStatuses validatorStatuses,
      final Function<RewardsAndPenaltiesCalculator, RewardAndPenaltyDeltas> calculatorFunction) {

    final BeaconStateAltair state = BeaconStateAltair.required(genericState);
    final RewardsAndPenaltiesCalculatorAltair calculator =
        new RewardsAndPenaltiesCalculatorAltair(
            specConfigAltair,
            state,
            validatorStatuses,
            miscHelpersAltair,
            beaconStateAccessorsAltair);

    return calculatorFunction.apply(calculator);
  }

  /**
   * Corresponds to process_participation_flag_updates in beacon-chain spec
   *
   * @param genericState The state to process
   * @see <a
   *     href="https://github.com/ethereum/consensus-specs/blob/master/specs/altair/beacon-chain.md#participation-flags-updates">Altair
   *     Participation Flags updates</a>
   */
  @Override
  public void processParticipationUpdates(final MutableBeaconState genericState) {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);

    state.setPreviousEpochParticipation(state.getCurrentEpochParticipation());

    Optional<SszList<SszByte>> emptyParticipationFlags = getZeroParticipationFlags(state);
    if (emptyParticipationFlags.isEmpty()) {
      state.getCurrentEpochParticipation().clear();
      state.getCurrentEpochParticipation().setAll(SszByte.ZERO, state.getValidators().size());
    } else {
      state.setCurrentEpochParticipation(emptyParticipationFlags.get());
    }
  }

  @Override
  public void processHistoricalSummariesUpdate(final MutableBeaconState state) {}

  @Override
  public void processSyncCommitteeUpdates(final MutableBeaconState genericState) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(genericState).increment();
    if (nextEpoch.mod(specConfigAltair.getEpochsPerSyncCommitteePeriod()).isZero()) {
      final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
      state.setCurrentSyncCommittee(state.getNextSyncCommittee());
      state.setNextSyncCommittee(beaconStateAccessorsAltair.getNextSyncCommittee(state));
    }
  }

  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    // Nothing to do
  }

  @Override
  public void processPendingDeposits(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processPendingConsolidations(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processProposerLookahead(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processBuilderPendingPayments(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processPtcWindow(final MutableBeaconState state) {
    // Nothing to do
  }

  /**
   * Replaces the progressive total balances in the state transition caches with an altair one if
   * not already in use. This handles both upgrading on milestone transition and switching from the
   * default no-op instance once initial data is available.
   */
  @Override
  public void initProgressiveTotalBalancesIfRequired(
      final BeaconState state, final TotalBalances totalBalances) {
    final TransitionCaches transitionCaches = BeaconStateCache.getTransitionCaches(state);
    if (!(transitionCaches.getProgressiveTotalBalances()
        instanceof ProgressiveTotalBalancesAltair)) {
      transitionCaches.setProgressiveTotalBalances(
          new ProgressiveTotalBalancesAltair(miscHelpersAltair, totalBalances));
    }
  }

  @Override
  public void processInactivityUpdates(
      final MutableBeaconState baseState, final ValidatorStatuses validatorStatuses) {
    if (beaconStateAccessors.getCurrentEpoch(baseState).equals(SpecConfig.GENESIS_EPOCH)) {
      return;
    }
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(baseState);
    final SszMutableUInt64List inactivityScores = state.getInactivityScores();
    final List<ValidatorStatus> statuses = validatorStatuses.getStatuses();
    final boolean isInInactivityLeak = beaconStateAccessors.isInactivityLeak(state);
    for (int i = 0; i < statuses.size(); i++) {
      final ValidatorStatus validatorStatus = statuses.get(i);
      if (!validatorStatus.isEligibleValidator()) {
        continue;
      }

      // Increase inactivity score of inactive validators
      final UInt64 currentScore = inactivityScores.getElement(i);
      UInt64 newScore;
      if (validatorStatus.isNotSlashed() && validatorStatus.isPreviousEpochTargetAttester()) {
        newScore = currentScore.minusMinZero(1);
      } else {
        newScore = currentScore.plus(specConfigAltair.getInactivityScoreBias());
      }
      // Decrease the score of all validators for forgiveness when not during a leak
      if (!isInInactivityLeak) {
        newScore = newScore.minusMinZero(specConfigAltair.getInactivityScoreRecoveryRate());
      }
      if (!currentScore.equals(newScore)) {
        inactivityScores.setElement(i, newScore);
      }
    }
  }

  @Override
  protected int getProportionalSlashingMultiplier() {
    return specConfigAltair.getProportionalSlashingMultiplierAltair();
  }

  private Optional<SszList<SszByte>> getZeroParticipationFlags(final BeaconStateAltair state) {
    SszList<SszByte> flags = zeroParticipationFlags;
    SszMutableList<SszByte> mutableFlags;
    if (flags == null) {
      mutableFlags = state.getCurrentEpochParticipation().getSchema().of().createWritableCopy();
      mutableFlags.setAll(SszByte.ZERO, state.getValidators().size());
      flags = mutableFlags.commitChanges();
      zeroParticipationFlags = flags;
    } else {
      if (flags.size() > state.getValidators().size()) {
        return Optional.empty();
      }
      if (flags.size() < state.getValidators().size()) {
        // need to take a writable copy to update the list if the participation list is too small
        mutableFlags = flags.createWritableCopy();
        while (mutableFlags.size() < state.getValidators().size()) {
          mutableFlags.append(SszByte.ZERO);
        }
        flags = mutableFlags.commitChanges();
        zeroParticipationFlags = flags;
      }
    }
    return Optional.of(flags);
  }
}

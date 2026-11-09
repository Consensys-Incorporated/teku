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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;

public interface EpochProcessor {

  /**
   * Calculates the penalties and rewards at a specific state for a set of validators. The result of
   * the calculation will contain exclusively objects of type {@link AggregatedRewardAndPenalty}.
   *
   * @param state the beacon state that will be used
   * @param validatorStatuses a list of validator status
   * @return a {@link RewardAndPenaltyDeltas} object with the result of rewards and penalties
   *     calculation for each eligible validator.
   * @see tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory
   * @see ValidatorStatus#isEligibleValidator()
   * @see tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator
   */
  default RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState state, final ValidatorStatuses validatorStatuses) {
    return getRewardAndPenaltyDeltas(
        state, validatorStatuses, RewardsAndPenaltiesCalculator::getDeltas);
  }

  /**
   * Calculates the penalties and rewards at a specific state for a set of validators. The result of
   * the calculation will contain exclusively objects of type {@link AggregatedRewardAndPenalty} or
   * {@link DetailedRewardAndPenalty} depending on the function chosen in the
   * <i>calculatorFunction</i> parameter.
   *
   * @param state the beacon state that will be used
   * @param validatorStatuses a list of validator status
   * @param calculatorFunction a mapper function defining what method from the {@link
   *     RewardsAndPenaltiesCalculator} should be used when collecting results.
   * @return a {@link RewardAndPenaltyDeltas} object with the result of rewards and penalties
   *     calculation for each eligible validator.
   * @see tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory
   * @see ValidatorStatus#isEligibleValidator()
   * @see tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator
   */
  RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      BeaconState state,
      ValidatorStatuses validatorStatuses,
      Function<RewardsAndPenaltiesCalculator, RewardAndPenaltyDeltas> calculatorFunction);

  BeaconState processEpoch(BeaconState preState) throws EpochProcessingException;

  default void initProgressiveTotalBalancesIfRequired(
      final BeaconState state, final TotalBalances totalBalances) {}

  BlockCheckpoints calculateBlockCheckpoints(BeaconState state);

  void processJustificationAndFinalization(MutableBeaconState state, TotalBalances totalBalances)
      throws EpochProcessingException;

  void processInactivityUpdates(MutableBeaconState state, ValidatorStatuses validatorStatuses);

  void processRewardsAndPenalties(MutableBeaconState state, ValidatorStatuses validatorStatuses)
      throws EpochProcessingException;

  void processRegistryUpdates(MutableBeaconState state, List<ValidatorStatus> statuses)
      throws EpochProcessingException;

  void processSlashings(MutableBeaconState state, ValidatorStatuses validatorStatuses);

  void processParticipationUpdates(MutableBeaconState genericState);

  void processEth1DataReset(MutableBeaconState state);

  void processEffectiveBalanceUpdates(MutableBeaconState state, List<ValidatorStatus> statuses);

  void processSlashingsReset(MutableBeaconState state);

  void processRandaoMixesReset(MutableBeaconState state);

  void processHistoricalRootsUpdate(MutableBeaconState state);

  void processHistoricalSummariesUpdate(MutableBeaconState state);

  void processSyncCommitteeUpdates(MutableBeaconState state);

  void applyPendingDeposits(MutableBeaconState state, PendingDeposit deposit);

  void processPendingDeposits(MutableBeaconState state);

  void processPendingConsolidations(MutableBeaconState state);

  void processProposerLookahead(MutableBeaconState state);

  void processBuilderPendingPayments(MutableBeaconState state);

  void processPtcWindow(final MutableBeaconState state);
}

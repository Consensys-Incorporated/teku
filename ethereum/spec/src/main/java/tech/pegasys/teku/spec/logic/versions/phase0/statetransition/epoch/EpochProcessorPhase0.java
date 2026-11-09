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

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class EpochProcessorPhase0 extends AbstractEpochProcessor {

  public EpochProcessorPhase0(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
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
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState state,
      final ValidatorStatuses validatorStatuses,
      final Function<RewardsAndPenaltiesCalculator, RewardAndPenaltyDeltas> calculatorFunction) {
    final RewardsAndPenaltiesCalculatorPhase0 calculator =
        new RewardsAndPenaltiesCalculatorPhase0(
            specConfig, state, validatorStatuses, miscHelpers, beaconStateAccessors);

    return calculatorFunction.apply(calculator);
  }

  @Override
  public void processParticipationUpdates(final MutableBeaconState genericState) {
    // Rotate current/previous epoch attestations
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);
    state.getPreviousEpochAttestations().setAll(state.getCurrentEpochAttestations());
    state.getCurrentEpochAttestations().clear();
  }

  @Override
  public void processHistoricalSummariesUpdate(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processSyncCommitteeUpdates(final MutableBeaconState state) {
    // Nothing to do
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
}

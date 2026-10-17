/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class EpochProcessorHeze extends EpochProcessorGloas {

  private final SpecConfigGloas specConfigGloas;

  public EpochProcessorHeze(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitionsGloas schemaDefinitions,
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
    this.specConfigGloas = specConfig;
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState state, final ValidatorStatuses validatorStatuses) {
    return getRewardAndPenaltyDeltas(
        state, validatorStatuses, RewardsAndPenaltiesCalculator::getDeltas);
  }

  /** EIP-8198: rewards and penalties are scaled to the previous epoch's slot duration. */
  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState state,
      final ValidatorStatuses validatorStatuses,
      final Function<RewardsAndPenaltiesCalculator, RewardAndPenaltyDeltas> calculatorFunction) {
    return calculatorFunction.apply(
        new RewardsAndPenaltiesCalculatorHeze(
            specConfigGloas,
            BeaconStateBellatrix.required(state),
            validatorStatuses,
            miscHelpersAltair,
            BeaconStateAccessorsHeze.required(beaconStateAccessorsAltair)));
  }

  /** {@code process_eth1_data_reset} was removed in Heze (EIP-8015). */
  @Override
  public void processEth1DataReset(final MutableBeaconState state) {}

  /**
   * apply_pending_deposit
   *
   * <p>EIP-8365: deposits that would create a new validator with BLS withdrawal credentials are
   * skipped without a refund, including deposits submitted before Heze. Top-ups to existing
   * validators are unaffected.
   */
  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    if (validatorsUtil.getValidatorIndex(state, deposit.getPublicKey()).isEmpty()
        && hasBlsWithdrawalCredentials(deposit)) {
      return;
    }
    super.applyPendingDeposits(state, deposit);
  }

  private boolean hasBlsWithdrawalCredentials(final PendingDeposit deposit) {
    return deposit.getWithdrawalCredentials().get(0) == WithdrawalPrefixes.BLS_WITHDRAWAL_BYTE;
  }
}

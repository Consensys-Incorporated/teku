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

package tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.logic.versions.pq.statetransition.epoch.EpochProcessorPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/** On this branch Heze builds on PQ: PQ's epoch processing with EIP-8365's deposit rule. */
public class EpochProcessorHeze extends EpochProcessorPq {

  public EpochProcessorHeze(
      final SpecConfigPq specConfig,
      final PredicatesPq predicates,
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final BeaconStateMutatorsPq beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitionsPq schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
  }

  /**
   * apply_pending_deposit
   *
   * <p>EIP-8365: deposits that would create a new validator with BLS withdrawal credentials are
   * skipped without a refund, including deposits submitted before Heze. Top-ups to existing
   * validators are unaffected.
   */
  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    if (getPendingDepositValidatorIndex(state, deposit).isEmpty()
        && hasBlsWithdrawalCredentials(deposit)) {
      return;
    }
    super.applyPendingDeposits(state, deposit);
  }

  private boolean hasBlsWithdrawalCredentials(final PendingDeposit deposit) {
    return deposit.getWithdrawalCredentials().get(0) == WithdrawalPrefixes.BLS_WITHDRAWAL_BYTE;
  }
}

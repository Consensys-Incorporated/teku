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
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ProgressiveTotalBalancesUpdates {
  ProgressiveTotalBalancesUpdates NOOP =
      new ProgressiveTotalBalancesUpdates() {

        @Override
        public void onAttestation(
            final Validator validator,
            final boolean currentEpoch,
            final boolean newSourceAttester,
            final boolean newTargetAttester,
            final boolean newHeadAttester) {}

        @Override
        public void onSlashing(final BeaconState state, final int slashedValidatorIndex) {}

        @Override
        public void onEpochTransition(final List<ValidatorStatus> validatorStatuses) {}

        @Override
        public void onEffectiveBalanceChange(
            final ValidatorStatus status, final UInt64 newEffectiveBalance) {}

        @Override
        public ProgressiveTotalBalancesUpdates copy() {
          return this;
        }

        @Override
        public Optional<TotalBalances> getTotalBalances(final SpecConfig specConfig) {
          return Optional.empty();
        }
      };

  void onAttestation(
      Validator validator,
      boolean currentEpoch,
      boolean newSourceAttester,
      boolean newTargetAttester,
      boolean newHeadAttester);

  void onSlashing(BeaconState state, int slashedValidatorIndex);

  void onEpochTransition(List<ValidatorStatus> validatorStatuses);

  void onEffectiveBalanceChange(ValidatorStatus status, UInt64 newEffectiveBalance);

  ProgressiveTotalBalancesUpdates copy();

  Optional<TotalBalances> getTotalBalances(SpecConfig specConfig);
}

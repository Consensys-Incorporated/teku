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

package tech.pegasys.teku.api.response;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorStatusUtil {
  public static ValidatorStatus getValidatorStatus(
      final BeaconState state,
      final Integer validatorIndex,
      final UInt64 epoch,
      final UInt64 farFutureEpoch) {
    return getValidatorStatus(epoch, state.getValidators().get(validatorIndex), farFutureEpoch);
  }

  public static ValidatorStatus getValidatorStatus(
      final UInt64 epoch, final Validator validator, final UInt64 farFutureEpoch) {
    // pending
    if (validator.getActivationEpoch().isGreaterThan(epoch)) {
      return validator.getActivationEligibilityEpoch().equals(farFutureEpoch)
          ? ValidatorStatus.pending_initialized
          : ValidatorStatus.pending_queued;
    }
    // active
    if (validator.getActivationEpoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getExitEpoch())) {
      if (validator.getExitEpoch().equals(farFutureEpoch)) {
        return ValidatorStatus.active_ongoing;
      }
      return validator.isSlashed()
          ? ValidatorStatus.active_slashed
          : ValidatorStatus.active_exiting;
    }

    // exited
    if (validator.getExitEpoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getWithdrawableEpoch())) {
      return validator.isSlashed()
          ? ValidatorStatus.exited_slashed
          : ValidatorStatus.exited_unslashed;
    }

    // withdrawal
    if (validator.getWithdrawableEpoch().isLessThanOrEqualTo(epoch)) {
      return validator.getEffectiveBalance().isGreaterThan(UInt64.ZERO)
          ? ValidatorStatus.withdrawal_possible
          : ValidatorStatus.withdrawal_done;
    }
    throw new IllegalStateException("Unable to determine validator status");
  }
}

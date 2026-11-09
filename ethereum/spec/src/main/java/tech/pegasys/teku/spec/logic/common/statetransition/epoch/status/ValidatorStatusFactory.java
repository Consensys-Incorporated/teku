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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ValidatorStatusFactory {

  ValidatorStatuses createValidatorStatuses(BeaconState state);

  ValidatorStatus createValidatorStatus(
      final Validator validator, final UInt64 previousEpoch, final UInt64 currentEpoch);

  /**
   * Creates a new ValidatorStatuses object with the existing list of statuses and the new statuses
   * from the given list. This is cheaper than creating a new one using createValidatorStatus method
   * because it will not recompute any data for validators already mapped in this object.
   *
   * @param validatorStatuses existing ValidatorStatuses object
   * @param validatorStatusesToAppend new statuses to append to the exiting ValidatorStatuses object
   * @return a new instance of ValidatorStatuses with both pre-existing and new validator statuses
   */
  ValidatorStatuses recreateValidatorStatuses(
      final ValidatorStatuses validatorStatuses,
      final List<ValidatorStatus> validatorStatusesToAppend);
}

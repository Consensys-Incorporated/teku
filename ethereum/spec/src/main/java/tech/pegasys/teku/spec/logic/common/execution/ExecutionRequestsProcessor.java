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

package tech.pegasys.teku.spec.logic.common.execution;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;

/** Used for processing requests from {@link ExecutionRequests} */
public interface ExecutionRequestsProcessor {

  void processDepositRequests(MutableBeaconState state, List<DepositRequest> depositRequests);

  void processWithdrawalRequests(
      MutableBeaconState state,
      List<WithdrawalRequest> withdrawalRequests,
      Supplier<ValidatorExitContext> validatorExitContextSupplier);

  void processConsolidationRequests(
      MutableBeaconState state, List<ConsolidationRequest> consolidationRequests);

  void processBuilderDepositRequests(
      MutableBeaconState state, List<BuilderDepositRequest> builderDepositRequests);

  void processBuilderExitRequests(
      MutableBeaconState state, List<BuilderExitRequest> builderExitRequests);

  default void processValidatorPqRegistrationRequests(
      final MutableBeaconState state,
      final List<ValidatorPqRegistrationRequest> validatorPqRegistrationRequests) {
    // NO-OP (until EIP-8483)
  }

  default void processBuilderPqRegistrationRequests(
      final MutableBeaconState state,
      final List<BuilderPqRegistrationRequest> builderPqRegistrationRequests) {
    // NO-OP (until EIP-8483)
  }
}

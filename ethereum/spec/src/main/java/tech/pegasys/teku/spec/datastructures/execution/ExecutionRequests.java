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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsEip8483;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.BuilderPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsPq;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ValidatorPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.VoluntaryExitRequest;

public interface ExecutionRequests extends SszContainer {

  List<DepositRequest> getDeposits();

  List<WithdrawalRequest> getWithdrawals();

  List<ConsolidationRequest> getConsolidations();

  /** Builder deposit requests (Gloas+). Empty for earlier milestones. */
  default List<BuilderDepositRequest> getBuilderDeposits() {
    return List.of();
  }

  /** Builder exit requests (Gloas+). Empty for earlier milestones. */
  default List<BuilderExitRequest> getBuilderExits() {
    return List.of();
  }

  /** Validator PQ registration requests (EIP-8483+). Empty for earlier milestones. */
  default List<ValidatorPqRegistrationRequest> getValidatorPqRegistrations() {
    return List.of();
  }

  /** Builder PQ registration requests (EIP-8483+). Empty for earlier milestones. */
  default List<BuilderPqRegistrationRequest> getBuilderPqRegistrations() {
    return List.of();
  }

  /** Validator preregistration requests (PQ+). Empty for earlier milestones. */
  default List<ValidatorPreregistrationRequest> getValidatorPreregistrations() {
    return List.of();
  }

  /** Builder preregistration requests (PQ+). Empty for earlier milestones. */
  default List<BuilderPreregistrationRequest> getBuilderPreregistrations() {
    return List.of();
  }

  /** Voluntary exit requests (PQ+). Empty for earlier milestones. */
  default List<VoluntaryExitRequest> getVoluntaryExits() {
    return List.of();
  }

  default Optional<ExecutionRequestsGloas> toVersionGloas() {
    return Optional.empty();
  }

  default Optional<ExecutionRequestsEip8483> toVersionEip8483() {
    return Optional.empty();
  }

  default Optional<ExecutionRequestsPq> toVersionPq() {
    return Optional.empty();
  }
}

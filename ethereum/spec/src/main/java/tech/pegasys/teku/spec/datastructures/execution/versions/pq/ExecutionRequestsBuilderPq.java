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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;

public class ExecutionRequestsBuilderPq implements ExecutionRequestsBuilder {

  private final ExecutionRequestsSchemaPq executionRequestsSchema;

  private List<DepositRequest> deposits = List.of();
  private List<WithdrawalRequest> withdrawals = List.of();
  private List<ConsolidationRequest> consolidations = List.of();
  private List<BuilderDepositRequest> builderDeposits = List.of();
  private List<BuilderExitRequest> builderExits = List.of();
  private List<ValidatorPreregistrationRequest> validatorPreregistrations = List.of();
  private List<BuilderPreregistrationRequest> builderPreregistrations = List.of();
  private List<VoluntaryExitRequest> voluntaryExits = List.of();

  public ExecutionRequestsBuilderPq(final ExecutionRequestsSchemaPq executionRequestsSchema) {
    this.executionRequestsSchema = executionRequestsSchema;
  }

  @Override
  public ExecutionRequestsBuilder deposits(final List<DepositRequest> deposits) {
    this.deposits = deposits;
    return this;
  }

  @Override
  public ExecutionRequestsBuilder withdrawals(final List<WithdrawalRequest> withdrawals) {
    this.withdrawals = withdrawals;
    return this;
  }

  @Override
  public ExecutionRequestsBuilder consolidations(final List<ConsolidationRequest> consolidations) {
    this.consolidations = consolidations;
    return this;
  }

  @Override
  public ExecutionRequestsBuilder builderDeposits(
      final Supplier<List<BuilderDepositRequest>> builderDeposits) {
    this.builderDeposits = builderDeposits.get();
    return this;
  }

  @Override
  public ExecutionRequestsBuilder builderExits(
      final Supplier<List<BuilderExitRequest>> builderExits) {
    this.builderExits = builderExits.get();
    return this;
  }

  /** PQ removed EIP-8483's PQ registration requests, so any are invalid rather than ignored. */
  @Override
  public ExecutionRequestsBuilder validatorPqRegistrations(
      final Supplier<List<ValidatorPqRegistrationRequest>> validatorPqRegistrations) {
    rejectRemovedRequests(validatorPqRegistrations, ValidatorPqRegistrationRequest.REQUEST_TYPE);
    return this;
  }

  /** PQ removed EIP-8483's PQ registration requests, so any are invalid rather than ignored. */
  @Override
  public ExecutionRequestsBuilder builderPqRegistrations(
      final Supplier<List<BuilderPqRegistrationRequest>> builderPqRegistrations) {
    rejectRemovedRequests(builderPqRegistrations, BuilderPqRegistrationRequest.REQUEST_TYPE);
    return this;
  }

  @Override
  public ExecutionRequestsBuilder validatorPreregistrations(
      final Supplier<List<ValidatorPreregistrationRequest>> validatorPreregistrations) {
    this.validatorPreregistrations = validatorPreregistrations.get();
    return this;
  }

  @Override
  public ExecutionRequestsBuilder builderPreregistrations(
      final Supplier<List<BuilderPreregistrationRequest>> builderPreregistrations) {
    this.builderPreregistrations = builderPreregistrations.get();
    return this;
  }

  @Override
  public ExecutionRequestsBuilder voluntaryExits(
      final Supplier<List<VoluntaryExitRequest>> voluntaryExits) {
    this.voluntaryExits = voluntaryExits.get();
    return this;
  }

  private static void rejectRemovedRequests(
      final Supplier<? extends List<?>> requests, final byte requestType) {
    checkArgument(
        isEmpty(requests), "Invalid execution request type: %s (removed in PQ)", requestType);
  }

  // A supplier that cannot be read (the PQ schema has no list for the type) also holds requests
  private static boolean isEmpty(final Supplier<? extends List<?>> requests) {
    try {
      return requests.get().isEmpty();
    } catch (final UnsupportedOperationException e) {
      return false;
    }
  }

  @Override
  public ExecutionRequestsPq build() {
    return new ExecutionRequestsPq(
        executionRequestsSchema,
        deposits,
        withdrawals,
        consolidations,
        builderDeposits,
        builderExits,
        validatorPreregistrations,
        builderPreregistrations,
        voluntaryExits);
  }
}

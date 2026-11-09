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

package tech.pegasys.teku.spec.datastructures.execution.versions.gloas;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;

public class ExecutionRequestsBuilderGloas implements ExecutionRequestsBuilder {

  private final ExecutionRequestsSchemaGloas executionRequestsSchema;

  private List<DepositRequest> deposits = List.of();
  private List<WithdrawalRequest> withdrawals = List.of();
  private List<ConsolidationRequest> consolidations = List.of();
  private List<BuilderDepositRequest> builderDeposits = List.of();
  private List<BuilderExitRequest> builderExits = List.of();

  public ExecutionRequestsBuilderGloas(final ExecutionRequestsSchemaGloas executionRequestsSchema) {
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

  @Override
  public ExecutionRequestsGloas build() {
    return new ExecutionRequestsGloas(
        executionRequestsSchema,
        deposits,
        withdrawals,
        consolidations,
        builderDeposits,
        builderExits);
  }
}

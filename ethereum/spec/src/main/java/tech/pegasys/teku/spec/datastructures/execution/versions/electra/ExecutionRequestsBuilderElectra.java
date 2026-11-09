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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import java.util.List;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;

public class ExecutionRequestsBuilderElectra implements ExecutionRequestsBuilder {

  private final ExecutionRequestsSchemaElectra executionRequestsSchema;

  private List<DepositRequest> deposits = List.of();
  private List<WithdrawalRequest> withdrawals = List.of();
  private List<ConsolidationRequest> consolidations = List.of();

  public ExecutionRequestsBuilderElectra(
      final ExecutionRequestsSchemaElectra executionRequestsSchema) {
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
  public ExecutionRequestsElectra build() {
    return new ExecutionRequestsElectra(
        executionRequestsSchema, deposits, withdrawals, consolidations);
  }
}

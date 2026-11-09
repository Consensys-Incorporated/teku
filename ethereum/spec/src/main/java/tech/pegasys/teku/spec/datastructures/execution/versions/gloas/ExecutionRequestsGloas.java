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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;

public class ExecutionRequestsGloas
    extends Container5<
        ExecutionRequestsGloas,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>,
        SszList<BuilderDepositRequest>,
        SszList<BuilderExitRequest>>
    implements ExecutionRequests {

  public static ExecutionRequestsGloas required(final ExecutionRequests executionRequests) {
    checkArgument(
        executionRequests instanceof ExecutionRequestsGloas,
        "Expected ExecutionRequestsGloas but was %s",
        executionRequests.getClass());
    return (ExecutionRequestsGloas) executionRequests;
  }

  ExecutionRequestsGloas(
      final ExecutionRequestsSchemaGloas schema,
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations,
      final List<BuilderDepositRequest> builderDeposits,
      final List<BuilderExitRequest> builderExits) {
    super(
        schema,
        schema.getDepositRequestsSchema().createFromElements(deposits),
        schema.getWithdrawalRequestsSchema().createFromElements(withdrawals),
        schema.getConsolidationRequestsSchema().createFromElements(consolidations),
        schema.getBuilderDepositRequestsSchema().createFromElements(builderDeposits),
        schema.getBuilderExitRequestsSchema().createFromElements(builderExits));
  }

  ExecutionRequestsGloas(final ExecutionRequestsSchemaGloas type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public List<DepositRequest> getDeposits() {
    return getField0().stream().toList();
  }

  @Override
  public List<WithdrawalRequest> getWithdrawals() {
    return getField1().stream().toList();
  }

  @Override
  public List<ConsolidationRequest> getConsolidations() {
    return getField2().stream().toList();
  }

  public List<BuilderDepositRequest> getBuilderDeposits() {
    return getField3().stream().toList();
  }

  public List<BuilderExitRequest> getBuilderExits() {
    return getField4().stream().toList();
  }

  @Override
  public Optional<ExecutionRequestsGloas> toVersionGloas() {
    return Optional.of(this);
  }

  @Override
  public ExecutionRequestsSchemaGloas getSchema() {
    return (ExecutionRequestsSchemaGloas) super.getSchema();
  }
}

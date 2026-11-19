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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container8;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;

/**
 * The PQ execution requests: EIP-8483's PQ registrations are gone (gaps at 5 and 6), replaced by
 * preregistrations and voluntary exit requests at 7 to 9.
 */
public class ExecutionRequestsPq
    extends Container8<
        ExecutionRequestsPq,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>,
        SszList<BuilderDepositRequest>,
        SszList<BuilderExitRequest>,
        SszList<ValidatorPreregistrationRequest>,
        SszList<BuilderPreregistrationRequest>,
        SszList<VoluntaryExitRequest>>
    implements ExecutionRequests {

  public static ExecutionRequestsPq required(final ExecutionRequests executionRequests) {
    checkArgument(
        executionRequests instanceof ExecutionRequestsPq,
        "Expected ExecutionRequestsPq but was %s",
        executionRequests.getClass());
    return (ExecutionRequestsPq) executionRequests;
  }

  ExecutionRequestsPq(
      final ExecutionRequestsSchemaPq schema,
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations,
      final List<BuilderDepositRequest> builderDeposits,
      final List<BuilderExitRequest> builderExits,
      final List<ValidatorPreregistrationRequest> validatorPreregistrations,
      final List<BuilderPreregistrationRequest> builderPreregistrations,
      final List<VoluntaryExitRequest> voluntaryExits) {
    super(
        schema,
        schema.getDepositRequestsSchema().createFromElements(deposits),
        schema.getWithdrawalRequestsSchema().createFromElements(withdrawals),
        schema.getConsolidationRequestsSchema().createFromElements(consolidations),
        schema.getBuilderDepositRequestsSchema().createFromElements(builderDeposits),
        schema.getBuilderExitRequestsSchema().createFromElements(builderExits),
        schema
            .getValidatorPreregistrationRequestsSchema()
            .createFromElements(validatorPreregistrations),
        schema
            .getBuilderPreregistrationRequestsSchema()
            .createFromElements(builderPreregistrations),
        schema.getVoluntaryExitRequestsSchema().createFromElements(voluntaryExits));
  }

  ExecutionRequestsPq(final ExecutionRequestsSchemaPq type, final TreeNode backingNode) {
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

  @Override
  public List<BuilderDepositRequest> getBuilderDeposits() {
    return getField3().stream().toList();
  }

  @Override
  public List<BuilderExitRequest> getBuilderExits() {
    return getField4().stream().toList();
  }

  @Override
  public List<ValidatorPreregistrationRequest> getValidatorPreregistrations() {
    return getField5().stream().toList();
  }

  @Override
  public List<BuilderPreregistrationRequest> getBuilderPreregistrations() {
    return getField6().stream().toList();
  }

  @Override
  public List<VoluntaryExitRequest> getVoluntaryExits() {
    return getField7().stream().toList();
  }

  @Override
  public Optional<ExecutionRequestsPq> toVersionPq() {
    return Optional.of(this);
  }

  @Override
  public ExecutionRequestsSchemaPq getSchema() {
    return (ExecutionRequestsSchemaPq) super.getSchema();
  }
}

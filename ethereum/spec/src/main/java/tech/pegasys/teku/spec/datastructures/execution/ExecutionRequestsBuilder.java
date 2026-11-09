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
import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;

public interface ExecutionRequestsBuilder {

  ExecutionRequestsBuilder deposits(List<DepositRequest> deposits);

  ExecutionRequestsBuilder withdrawals(List<WithdrawalRequest> withdrawals);

  ExecutionRequestsBuilder consolidations(List<ConsolidationRequest> consolidations);

  default ExecutionRequestsBuilder builderDeposits(
      final Supplier<List<BuilderDepositRequest>> builderDeposits) {
    // NO-OP (until Gloas)
    return this;
  }

  default ExecutionRequestsBuilder builderExits(
      final Supplier<List<BuilderExitRequest>> builderExits) {
    // NO-OP (until Gloas)
    return this;
  }

  ExecutionRequests build();
}

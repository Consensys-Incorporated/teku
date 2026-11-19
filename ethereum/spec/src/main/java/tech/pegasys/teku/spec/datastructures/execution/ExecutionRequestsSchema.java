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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.BuilderPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ValidatorPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.VoluntaryExitRequest;

public interface ExecutionRequestsSchema<T extends ExecutionRequests>
    extends SszContainerSchema<T> {

  SszListSchema<DepositRequest, ?> getDepositRequestsSchema();

  SszListSchema<WithdrawalRequest, ?> getWithdrawalRequestsSchema();

  SszListSchema<ConsolidationRequest, ?> getConsolidationRequestsSchema();

  default SszListSchema<BuilderDepositRequest, ?> getBuilderDepositRequestsSchema() {
    throw new UnsupportedOperationException(
        "Builder deposit requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<BuilderExitRequest, ?> getBuilderExitRequestsSchema() {
    throw new UnsupportedOperationException(
        "Builder exit requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<ValidatorPqRegistrationRequest, ?>
      getValidatorPqRegistrationRequestsSchema() {
    throw new UnsupportedOperationException(
        "Validator PQ registration requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<BuilderPqRegistrationRequest, ?> getBuilderPqRegistrationRequestsSchema() {
    throw new UnsupportedOperationException(
        "Builder PQ registration requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<ValidatorPreregistrationRequest, ?>
      getValidatorPreregistrationRequestsSchema() {
    throw new UnsupportedOperationException(
        "Validator preregistration requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<BuilderPreregistrationRequest, ?>
      getBuilderPreregistrationRequestsSchema() {
    throw new UnsupportedOperationException(
        "Builder preregistration requests are not supported by " + getClass().getSimpleName());
  }

  default SszListSchema<VoluntaryExitRequest, ?> getVoluntaryExitRequestsSchema() {
    throw new UnsupportedOperationException(
        "Voluntary exit requests are not supported by " + getClass().getSimpleName());
  }

  ExecutionRequestsBuilder createBuilder();

  /**
   * Re-creates {@code source} with this schema, e.g. to carry Gloas-typed requests across the
   * EIP-8483 fork boundary where the ExecutionRequests type changes.
   *
   * @throws IllegalArgumentException if {@code source} holds requests this schema cannot represent
   */
  default ExecutionRequests convertFrom(final ExecutionRequests source) {
    if (source.getSchema().equals(this)) {
      return source;
    }
    final ExecutionRequests converted =
        createBuilder()
            .deposits(source.getDeposits())
            .withdrawals(source.getWithdrawals())
            .consolidations(source.getConsolidations())
            .builderDeposits(source::getBuilderDeposits)
            .builderExits(source::getBuilderExits)
            .validatorPqRegistrations(source::getValidatorPqRegistrations)
            .builderPqRegistrations(source::getBuilderPqRegistrations)
            .validatorPreregistrations(source::getValidatorPreregistrations)
            .builderPreregistrations(source::getBuilderPreregistrations)
            .voluntaryExits(source::getVoluntaryExits)
            .build();
    checkArgument(
        converted.getBuilderDeposits().size() == source.getBuilderDeposits().size()
            && converted.getBuilderExits().size() == source.getBuilderExits().size()
            && converted.getValidatorPqRegistrations().size()
                == source.getValidatorPqRegistrations().size()
            && converted.getBuilderPqRegistrations().size()
                == source.getBuilderPqRegistrations().size()
            && converted.getValidatorPreregistrations().size()
                == source.getValidatorPreregistrations().size()
            && converted.getBuilderPreregistrations().size()
                == source.getBuilderPreregistrations().size()
            && converted.getVoluntaryExits().size() == source.getVoluntaryExits().size(),
        "Execution requests cannot be represented by %s",
        getClass().getSimpleName());
    return converted;
  }
}

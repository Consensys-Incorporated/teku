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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
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

/*
 Implement the rules for decoding and hashing execution requests according to https://eips.ethereum.org/EIPS/eip-7685
*/
public class ExecutionRequestsDataCodec {

  private final ExecutionRequestsSchema<?> executionRequestsSchema;

  public ExecutionRequestsDataCodec(final ExecutionRequestsSchema<?> executionRequestsSchema) {
    this.executionRequestsSchema = executionRequestsSchema;
  }

  /**
   * Decodes the execution requests received from the EL.
   *
   * @param executionRequests list of encoded execution requests from the EL
   * @return an ExecutionRequests object with the requests
   */
  public ExecutionRequests decode(final List<Bytes> executionRequests) {
    final ExecutionRequestsBuilder executionRequestsBuilder =
        executionRequestsSchema.createBuilder();

    byte previousRequestType = -1;
    for (final Bytes request : executionRequests) {
      if (request.isEmpty()) {
        throw new IllegalArgumentException("Execution request data must not be empty");
      }

      final byte requestType = request.get(0);
      if (requestType <= previousRequestType) {
        throw new IllegalArgumentException(
            "Execution requests are not in strictly ascending order");
      }

      final Bytes requestData = request.slice(1);
      if (requestData.isEmpty()) {
        throw new IllegalArgumentException("Empty data for request type " + requestType);
      }

      switch (requestType) {
        case DepositRequest.REQUEST_TYPE ->
            executionRequestsBuilder.deposits(
                executionRequestsSchema
                    .getDepositRequestsSchema()
                    .sszDeserialize(requestData)
                    .asList());
        case WithdrawalRequest.REQUEST_TYPE ->
            executionRequestsBuilder.withdrawals(
                executionRequestsSchema
                    .getWithdrawalRequestsSchema()
                    .sszDeserialize(requestData)
                    .asList());
        case ConsolidationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.consolidations(
                executionRequestsSchema
                    .getConsolidationRequestsSchema()
                    .sszDeserialize(requestData)
                    .asList());
        case BuilderDepositRequest.REQUEST_TYPE ->
            executionRequestsBuilder.builderDeposits(
                () ->
                    executionRequestsSchema
                        .getBuilderDepositRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case BuilderExitRequest.REQUEST_TYPE ->
            executionRequestsBuilder.builderExits(
                () ->
                    executionRequestsSchema
                        .getBuilderExitRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case ValidatorPqRegistrationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.validatorPqRegistrations(
                () ->
                    executionRequestsSchema
                        .getValidatorPqRegistrationRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case BuilderPqRegistrationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.builderPqRegistrations(
                () ->
                    executionRequestsSchema
                        .getBuilderPqRegistrationRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case ValidatorPreregistrationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.validatorPreregistrations(
                () ->
                    executionRequestsSchema
                        .getValidatorPreregistrationRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case BuilderPreregistrationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.builderPreregistrations(
                () ->
                    executionRequestsSchema
                        .getBuilderPreregistrationRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        case VoluntaryExitRequest.REQUEST_TYPE ->
            executionRequestsBuilder.voluntaryExits(
                () ->
                    executionRequestsSchema
                        .getVoluntaryExitRequestsSchema()
                        .sszDeserialize(requestData)
                        .asList());
        default ->
            throw new IllegalArgumentException("Invalid execution request type: " + requestType);
      }
      previousRequestType = requestType;
    }

    return executionRequestsBuilder.build();
  }

  /**
   * Encodes the provided ExecutionRequests object to send the requests to the EL for validation.
   *
   * @param executionRequests the execution requests in the BeaconBlock
   * @return list of encoded execution requests
   */
  public List<Bytes> encode(final ExecutionRequests executionRequests) {
    final List<Bytes> executionRequestsData = new ArrayList<>();
    final List<DepositRequest> deposits = executionRequests.getDeposits();
    if (!deposits.isEmpty()) {
      final Bytes depositRequestsData =
          executionRequestsSchema
              .getDepositRequestsSchema()
              .createFromElements(deposits)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(DepositRequest.REQUEST_TYPE_PREFIX, depositRequestsData));
    }
    final List<WithdrawalRequest> withdrawals = executionRequests.getWithdrawals();
    if (!withdrawals.isEmpty()) {
      final Bytes withdrawalsRequestsData =
          executionRequestsSchema
              .getWithdrawalRequestsSchema()
              .createFromElements(withdrawals)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(WithdrawalRequest.REQUEST_TYPE_PREFIX, withdrawalsRequestsData));
    }
    final List<ConsolidationRequest> consolidations = executionRequests.getConsolidations();
    if (!consolidations.isEmpty()) {
      final Bytes consolidationRequestsData =
          executionRequestsSchema
              .getConsolidationRequestsSchema()
              .createFromElements(consolidations)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(ConsolidationRequest.REQUEST_TYPE_PREFIX, consolidationRequestsData));
    }
    final List<BuilderDepositRequest> builderDeposits = executionRequests.getBuilderDeposits();
    if (!builderDeposits.isEmpty()) {
      final Bytes builderDepositRequestsData =
          executionRequestsSchema
              .getBuilderDepositRequestsSchema()
              .createFromElements(builderDeposits)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(BuilderDepositRequest.REQUEST_TYPE_PREFIX, builderDepositRequestsData));
    }
    final List<BuilderExitRequest> builderExits = executionRequests.getBuilderExits();
    if (!builderExits.isEmpty()) {
      final Bytes builderExitRequestsData =
          executionRequestsSchema
              .getBuilderExitRequestsSchema()
              .createFromElements(builderExits)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(BuilderExitRequest.REQUEST_TYPE_PREFIX, builderExitRequestsData));
    }
    final List<ValidatorPqRegistrationRequest> validatorPqRegistrations =
        executionRequests.getValidatorPqRegistrations();
    if (!validatorPqRegistrations.isEmpty()) {
      final Bytes validatorPqRegistrationRequestsData =
          executionRequestsSchema
              .getValidatorPqRegistrationRequestsSchema()
              .createFromElements(validatorPqRegistrations)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(
              ValidatorPqRegistrationRequest.REQUEST_TYPE_PREFIX,
              validatorPqRegistrationRequestsData));
    }
    final List<BuilderPqRegistrationRequest> builderPqRegistrations =
        executionRequests.getBuilderPqRegistrations();
    if (!builderPqRegistrations.isEmpty()) {
      final Bytes builderPqRegistrationRequestsData =
          executionRequestsSchema
              .getBuilderPqRegistrationRequestsSchema()
              .createFromElements(builderPqRegistrations)
              .sszSerialize();
      executionRequestsData.add(
          Bytes.concatenate(
              BuilderPqRegistrationRequest.REQUEST_TYPE_PREFIX, builderPqRegistrationRequestsData));
    }
    final List<ValidatorPreregistrationRequest> validatorPreregistrations =
        executionRequests.getValidatorPreregistrations();
    if (!validatorPreregistrations.isEmpty()) {
      executionRequestsData.add(
          Bytes.concatenate(
              ValidatorPreregistrationRequest.REQUEST_TYPE_PREFIX,
              executionRequestsSchema
                  .getValidatorPreregistrationRequestsSchema()
                  .createFromElements(validatorPreregistrations)
                  .sszSerialize()));
    }
    final List<BuilderPreregistrationRequest> builderPreregistrations =
        executionRequests.getBuilderPreregistrations();
    if (!builderPreregistrations.isEmpty()) {
      executionRequestsData.add(
          Bytes.concatenate(
              BuilderPreregistrationRequest.REQUEST_TYPE_PREFIX,
              executionRequestsSchema
                  .getBuilderPreregistrationRequestsSchema()
                  .createFromElements(builderPreregistrations)
                  .sszSerialize()));
    }
    final List<VoluntaryExitRequest> voluntaryExits = executionRequests.getVoluntaryExits();
    if (!voluntaryExits.isEmpty()) {
      executionRequestsData.add(
          Bytes.concatenate(
              VoluntaryExitRequest.REQUEST_TYPE_PREFIX,
              executionRequestsSchema
                  .getVoluntaryExitRequestsSchema()
                  .createFromElements(voluntaryExits)
                  .sszSerialize()));
    }
    return executionRequestsData;
  }
}

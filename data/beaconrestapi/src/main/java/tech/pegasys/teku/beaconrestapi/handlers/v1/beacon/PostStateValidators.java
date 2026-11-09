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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorDataBuilder.STATE_VALIDATORS_RESPONSE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorRequestBodyType.STATE_VALIDATOR_REQUEST_TYPE;
import static tech.pegasys.teku.ethereum.json.types.beacon.StatusParameter.getApplicableValidatorStatuses;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorRequestBodyType;
import tech.pegasys.teku.ethereum.json.types.beacon.StatusParameter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class PostStateValidators extends RestApiEndpoint {
  private final ChainDataProvider chainDataProvider;

  public PostStateValidators(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  PostStateValidators(final ChainDataProvider provider) {
    super(
        EndpointMetadata.post(GetStateValidators.ROUTE)
            .operationId("postStateValidators")
            .summary("Get validators from state")
            .description(
                "Returns filterable list of validators with their balance, status and index.")
            .pathParam(PARAMETER_STATE_ID)
            .requestBodyType(STATE_VALIDATOR_REQUEST_TYPE)
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", STATE_VALIDATORS_RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<StateValidatorRequestBodyType> requestBody;

    try {
      requestBody = request.getOptionalRequestBody();
    } catch (RuntimeException e) {
      final Throwable throwable = Throwables.getRootCause(e);
      if (throwable instanceof JsonParseException) {
        request.respondError(SC_BAD_REQUEST, throwable.getMessage());
      } else {
        throw e;
      }
      return;
    }

    final List<String> validators =
        requestBody.map(StateValidatorRequestBodyType::getIds).orElse(List.of());
    final List<StatusParameter> statusParameters =
        requestBody.map(StateValidatorRequestBodyType::getStatuses).orElse(List.of());

    final Set<ValidatorStatus> statusFilter = getApplicableValidatorStatuses(statusParameters);

    SafeFuture<Optional<ObjectAndMetaData<List<StateValidatorData>>>> future =
        chainDataProvider.getStateValidators(
            request.getPathParameter(PARAMETER_STATE_ID), validators, statusFilter);

    request.respondAsync(
        future.thenApply(
            maybeData ->
                maybeData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

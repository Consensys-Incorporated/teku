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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ID_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.api.StateValidatorBalanceData;

public class GetStateValidatorBalances extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/validator_balances";

  static final SerializableTypeDefinition<ObjectAndMetaData<SszList<StateValidatorBalanceData>>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<SszList<StateValidatorBalanceData>>>object()
              .name("GetStateValidatorBalancesResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField(
                  "data",
                  StateValidatorBalanceData.SSZ_LIST_SCHEMA.getJsonTypeDefinition(),
                  ObjectAndMetaData::getData)
              .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateValidatorBalances(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateValidatorBalances(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateValidatorBalances")
            .summary("Get validator balances from state")
            .description("Returns filterable list of validator balances.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .queryListParam(ID_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE, EthereumTypes.sszResponseType())
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<String> validators = request.getQueryParameterList(ID_PARAMETER);

    final SafeFuture<Optional<ObjectAndMetaData<SszList<StateValidatorBalanceData>>>> future =
        chainDataProvider.getStateValidatorBalances(
            request.getPathParameter(PARAMETER_STATE_ID), validators);

    request.respondAsync(
        future.thenApply(
            maybeDataList ->
                maybeDataList
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}

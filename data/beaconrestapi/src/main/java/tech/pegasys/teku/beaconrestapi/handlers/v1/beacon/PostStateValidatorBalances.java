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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.api.StateValidatorBalanceData;

public class PostStateValidatorBalances extends RestApiEndpoint {
  private final ChainDataProvider chainDataProvider;

  public PostStateValidatorBalances(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  PostStateValidatorBalances(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.post(GetStateValidatorBalances.ROUTE)
            .operationId("postStateValidatorBalances")
            .summary("Get validator balances from state")
            .description("Returns filterable list of validator balances.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .optionalRequestBody()
            .requestBodyType(DeserializableTypeDefinition.listOf(STRING_TYPE))
            .response(
                SC_OK,
                "Request successful",
                GetStateValidatorBalances.RESPONSE_TYPE,
                EthereumTypes.sszResponseType())
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<List<String>> validators = request.getOptionalRequestBody();

    final SafeFuture<Optional<ObjectAndMetaData<SszList<StateValidatorBalanceData>>>> future =
        chainDataProvider.getStateValidatorBalances(
            request.getPathParameter(PARAMETER_STATE_ID), validators.orElse(List.of()));

    request.respondAsync(
        future.thenApply(
            maybeDataList ->
                maybeDataList
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}

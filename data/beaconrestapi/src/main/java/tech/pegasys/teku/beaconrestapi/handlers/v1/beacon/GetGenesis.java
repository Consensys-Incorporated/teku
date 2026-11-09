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

import static tech.pegasys.teku.ethereum.json.types.beacon.GetGenesisApiDataBuilder.GET_GENESIS_API_DATA_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.ethereum.json.types.beacon.GetGenesisApiData;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;

public class GetGenesis extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/genesis";
  private final ChainDataProvider chainDataProvider;

  public GetGenesis(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetGenesis(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGenesis")
            .summary("Get chain genesis details")
            .description(
                "Retrieve details of the chain's genesis which can be used to identify chain.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", GET_GENESIS_API_DATA_TYPE)
            .response(SC_NOT_FOUND, "Chain genesis info is not yet known")
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  private Optional<GenesisData> getGenesisData() {
    if (!chainDataProvider.isStoreAvailable()) {
      return Optional.empty();
    }
    return Optional.of(chainDataProvider.getGenesisStateData());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<GenesisData> maybeData = getGenesisData();
    if (maybeData.isEmpty()) {
      request.respondWithCode(SC_NOT_FOUND);
      return;
    }
    final GenesisData data = maybeData.get();
    request.respondOk(
        new GetGenesisApiData(
            data.getGenesisTime(),
            data.getGenesisValidatorsRoot(),
            chainDataProvider.getGenesisForkVersion()));
  }
}

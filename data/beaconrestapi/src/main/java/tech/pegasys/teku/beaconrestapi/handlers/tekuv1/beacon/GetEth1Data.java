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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

public class GetEth1Data extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/beacon/pool/eth1data";

  private static final SerializableTypeDefinition<Eth1Data> ETH1DATA_RESPONSE_TYPE =
      SerializableTypeDefinition.<Eth1Data>object()
          .name("GetEth1DataResponse")
          .withField("data", Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(), Function.identity())
          .build();

  private final ChainDataProvider chainDataProvider;
  private final Eth1DataProvider eth1DataProvider;

  public GetEth1Data(final DataProvider dataProvider, final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEth1Data")
            .summary("Get new Eth1Data")
            .description(
                "Eth1Data that would be used in a new block created based on the current head.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", ETH1DATA_RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = dataProvider.getChainDataProvider();
    this.eth1DataProvider = eth1DataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getBeaconStateAtHead()
            .thenApply(
                maybeStateAndMetadata -> {
                  if (maybeStateAndMetadata.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  } else {
                    return AsyncApiResponse.respondOk(
                        eth1DataProvider.getEth1Vote(maybeStateAndMetadata.get()));
                  }
                }));
  }
}

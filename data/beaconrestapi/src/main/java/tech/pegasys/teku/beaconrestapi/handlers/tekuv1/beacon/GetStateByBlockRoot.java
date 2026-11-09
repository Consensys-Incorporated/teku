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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateByBlockRoot extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/beacon/blocks/{block_id}/state";
  private final ChainDataProvider chainDataProvider;

  public GetStateByBlockRoot(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getChainDataProvider(), spec);
  }

  public GetStateByBlockRoot(final ChainDataProvider chainDataProvider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateByBlockRoot")
            .summary("Get SSZ State By Block id")
            .description(
                "Download the state SSZ object for given identifier - by block root, keyword, or slot.")
            .tags(TAG_TEKU)
            .pathParam(PARAMETER_BLOCK_ID)
            .defaultResponseType(OCTET_STREAM)
            .response(
                SC_OK,
                "Request successful",
                sszResponseType(
                    beaconState ->
                        spec.getForkSchedule()
                            .getSpecMilestoneAtSlot(((BeaconState) beaconState).getSlot())))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);

    final String blockId = request.getPathParameter(PARAMETER_BLOCK_ID);
    SafeFuture<Optional<BeaconState>> future = chainDataProvider.getBeaconStateByBlockId(blockId);

    request.respondAsync(
        future.thenApply(
            result -> {
              if (result.isEmpty()) {
                return AsyncApiResponse.respondWithError(
                    SC_NOT_FOUND, "State by block root not found: " + blockId);
              }

              return AsyncApiResponse.respondOk(result.get());
            }));
  }
}

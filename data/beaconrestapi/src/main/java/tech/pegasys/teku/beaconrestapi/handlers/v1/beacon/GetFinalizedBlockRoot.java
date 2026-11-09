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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ROOT_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetFinalizedBlockRoot extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/checkpoint/finalized_blocks/{slot}/root";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<Bytes32> RESPONSE_TYPE =
      SerializableTypeDefinition.<Bytes32>object()
          .name("GetHashTreeRootResponse")
          .withField("data", ROOT_TYPE, Function.identity())
          .build();

  public GetFinalizedBlockRoot(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetFinalizedBlockRoot(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getFinalizedBlockRoot")
            .summary("Get finalized block root")
            .description(
                "Retrieves hashTreeRoot of finalized Beacon Block.\n\n"
                    + "Responds with 404 if block at a slot is either unavailable or not yet finalized.")
            .tags(TAG_EXPERIMENTAL)
            .pathParam(SLOT_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);
    final SafeFuture<Optional<Bytes32>> futureFinalizedBlockRoot =
        chainDataProvider.getFinalizedBlockRoot(slot);
    request.respondAsync(
        futureFinalizedBlockRoot.thenApply(
            maybeFinalizedBlockRoot ->
                maybeFinalizedBlockRoot
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}

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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetFinalizedStateSlotBefore extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/beacon/state/finalized/slot/before/{slot}";
  private final ChainDataProvider chainDataProvider;
  private static final SerializableTypeDefinition<UInt64> UINT64_RESPONSE =
      SerializableTypeDefinition.<UInt64>object()
          .name("Slot")
          .withField("data", UINT64_TYPE, Function.identity())
          .build();

  public GetFinalizedStateSlotBefore(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetFinalizedStateSlotBefore(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetFinalizedStateSlotBefore")
            .summary("Get the closest stored state index")
            .description("Get the State slot closest to the specified slot.")
            .tags(TAG_TEKU)
            .pathParam(SLOT_PARAMETER.withDescription("At or before the specified slot"))
            .response(SC_OK, "Request successful", UINT64_RESPONSE)
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 beforeSlot = request.getPathParameter(SLOT_PARAMETER);
    final SafeFuture<Optional<UInt64>> future = chainDataProvider.getFinalizedStateSlot(beforeSlot);

    request.respondAsync(
        future.thenApply(
            maybeSlot ->
                maybeSlot
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

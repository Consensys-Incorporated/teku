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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetState extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/debug/beacon/states/{state_id}";
  private final ChainDataProvider chainDataProvider;

  public GetState(
      final DataProvider dataProvider, final Spec spec, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), spec, schemaCache);
  }

  @SuppressWarnings("unchecked")
  public GetState(
      final ChainDataProvider chainDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getState")
            .summary("Get state")
            .description(
                """
                    Returns full BeaconState object for given state_id.

                    Use Accept header to select `application/octet-stream` if SSZ response type is required.

                    __NOTE__: Only phase0 beacon state will be returned in JSON, use `/eth/v2/beacon/states/{state_id}` for altair.
                    """)
            .tags(TAG_DEBUG)
            .pathParam(PARAMETER_STATE_ID)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<BeaconState>object()
                    .name("GetStateResponse")
                    .withField(
                        "data",
                        (DeserializableTypeDefinition<BeaconState>)
                            schemaCache
                                .getSchemaDefinition(SpecMilestone.PHASE0)
                                .getBeaconStateSchema()
                                .getJsonTypeDefinition(),
                        Function.identity())
                    .build(),
                sszResponseType(
                    beaconState ->
                        spec.getForkSchedule().getSpecMilestoneAtSlot(beaconState.getSlot())))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<StateAndMetaData>> future =
        chainDataProvider.getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID));

    request.respondAsync(
        future.thenApply(
            maybeStateAndMetaData ->
                maybeStateAndMetaData
                    .map(
                        stateAndMetaData -> {
                          if (JSON.equals(request.getResponseContentType(SC_OK))
                              && (stateAndMetaData.getMilestone() != SpecMilestone.PHASE0)) {
                            throw new BadRequestException("SpecMilestone not PHASE0");
                          }
                          return AsyncApiResponse.respondOk(stateAndMetaData.getData());
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

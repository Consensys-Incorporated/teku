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

package tech.pegasys.teku.beaconrestapi.handlers.v2.debug;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetState extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/debug/beacon/states/{state_id}";
  private final ChainDataProvider chainDataProvider;

  public GetState(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetState(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateV2")
            .summary("Get full BeaconState object")
            .description(
                "Returns full BeaconState object for given state_id.\n\n"
                    + "Use Accept header to select `application/octet-stream` if SSZ response type is required.")
            .tags(TAG_DEBUG)
            .pathParam(PARAMETER_STATE_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
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
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              stateAndMetaData.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(stateAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<StateAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return SerializableTypeDefinition.<StateAndMetaData>object()
        .name("GetStateV2Response")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField(
            "data",
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "BeaconState",
                SchemaDefinitions::getBeaconStateSchema,
                (beaconState, milestone) ->
                    schemaDefinitionCache.milestoneAtSlot(beaconState.getSlot()).equals(milestone)),
            ObjectAndMetaData::getData)
        .build();
  }
}

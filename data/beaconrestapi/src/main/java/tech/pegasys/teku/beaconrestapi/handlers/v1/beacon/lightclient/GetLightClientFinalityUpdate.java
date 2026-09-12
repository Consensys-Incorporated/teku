/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient;

import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetLightClientFinalityUpdate extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/light_client/finality_update";
  private final ChainDataProvider chainDataProvider;
  private final SchemaDefinitionCache schemaDefinitionCache;

  public GetLightClientFinalityUpdate(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetLightClientFinalityUpdate(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getLightClientFinalityUpdate")
            .summary("Get the latest known `LightClientFinalityUpdate`")
            .description(
                "Requests the latest `LightClientFinalityUpdate` known by the server. Depending on the `Accept` header it can be returned either as JSON or SSZ-serialized bytes.")
            .tags(TAG_BEACON)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType(
                    (final LightClientFinalityUpdate finalityUpdate) ->
                        milestoneAtFinalityUpdateSlot(schemaDefinitionCache, finalityUpdate)),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
    this.schemaDefinitionCache = schemaDefinitionCache;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<LightClientFinalityUpdate> maybeFinalityUpdate =
        chainDataProvider.getLatestLightClientFinalityUpdate();

    if (maybeFinalityUpdate.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Light client finality update is not available");
      return;
    }

    final LightClientFinalityUpdate finalityUpdate = maybeFinalityUpdate.get();
    request.header(
        HEADER_CONSENSUS_VERSION,
        milestoneAtFinalityUpdateSlot(schemaDefinitionCache, finalityUpdate).lowerCaseName());
    request.respondOk(finalityUpdate);
  }

  private static SerializableTypeDefinition<LightClientFinalityUpdate> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<LightClientFinalityUpdate> lightClientFinalityUpdateType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache,
            "LightClientFinalityUpdate",
            List.of(
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (finalityUpdate, milestone) ->
                        milestoneAtFinalityUpdateSlot(schemaDefinitionCache, finalityUpdate)
                            .equals(milestone),
                    SpecMilestone.ALTAIR,
                    schemaDefinitions ->
                        SchemaDefinitionsAltair.required(schemaDefinitions)
                            .getLightClientFinalityUpdateSchema())));

    return SerializableTypeDefinition.<LightClientFinalityUpdate>object()
        .name("GetLightClientFinalityUpdateResponse")
        .withField(
            "version",
            MILESTONE_TYPE,
            finalityUpdate -> milestoneAtFinalityUpdateSlot(schemaDefinitionCache, finalityUpdate))
        .withField("data", lightClientFinalityUpdateType, Function.identity())
        .build();
  }

  private static SpecMilestone milestoneAtFinalityUpdateSlot(
      final SchemaDefinitionCache schemaDefinitionCache,
      final LightClientFinalityUpdate finalityUpdate) {
    return schemaDefinitionCache.milestoneAtSlot(
        finalityUpdate.getAttestedHeader().getBeacon().getSlot());
  }
}

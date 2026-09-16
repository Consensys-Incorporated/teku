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
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetLightClientOptimisticUpdate extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/light_client/optimistic_update";
  private final ChainDataProvider chainDataProvider;
  private final SchemaDefinitionCache schemaDefinitionCache;

  public GetLightClientOptimisticUpdate(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetLightClientOptimisticUpdate(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getLightClientOptimisticUpdate")
            .summary("Get the latest known `LightClientOptimisticUpdate`")
            .description(
                "Requests the latest `LightClientOptimisticUpdate` known by the server. Depending on the `Accept` header it can be returned either as JSON or SSZ-serialized bytes.")
            .tags(TAG_BEACON)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .build());
    this.schemaDefinitionCache = schemaDefinitionCache;
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<LightClientOptimisticUpdate> maybeOptimisticUpdate =
        chainDataProvider.getLatestLightClientOptimisticUpdate();

    if (maybeOptimisticUpdate.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Light client optimistic update is not available");
      return;
    }

    final LightClientOptimisticUpdate optimisticUpdate = maybeOptimisticUpdate.get();
    final SpecMilestone milestone =
        milestoneAtOptimisticSlot(schemaDefinitionCache, optimisticUpdate);
    request.header(HEADER_CONSENSUS_VERSION, milestone.lowerCaseName());
    request.respondOk(new ObjectAndMetaData<>(optimisticUpdate, milestone, false, false, false));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<LightClientOptimisticUpdate>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<LightClientOptimisticUpdate> lightClientOptimisticUpdateType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache,
            "LightClientOptimisticUpdate",
            List.of(
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (optimisticUpdate, milestone) ->
                        milestoneAtOptimisticSlot(schemaDefinitionCache, optimisticUpdate)
                                .equals(milestone)
                            && milestone.isGreaterThan(SpecMilestone.PHASE0),
                    SpecMilestone.ALTAIR,
                    schemaDefinitions ->
                        SchemaDefinitionsAltair.required(schemaDefinitions)
                            .getLightClientOptimisticUpdateSchema())));

    return SerializableTypeDefinition.<ObjectAndMetaData<LightClientOptimisticUpdate>>object()
        .name("GetLightClientOptimisticUpdateResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", lightClientOptimisticUpdateType, ObjectAndMetaData::getData)
        .build();
  }

  private static SpecMilestone milestoneAtOptimisticSlot(
      final SchemaDefinitionCache schemaDefinitionCache,
      final LightClientOptimisticUpdate optimisticUpdate) {
    return schemaDefinitionCache.milestoneAtSlot(
        optimisticUpdate.getAttestedHeader().getBeacon().getSlot());
  }
}

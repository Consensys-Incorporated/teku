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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOCK_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetLightClientBootstrap extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/light_client/bootstrap/{block_root}";
  private final ChainDataProvider chainDataProvider;

  public GetLightClientBootstrap(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetLightClientBootstrap(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getLightClientBootstrap")
            .summary("Get `LightClientBootstrap` structure for a requested block root.")
            .description(
                "Requests the LightClientBootstrap structure corresponding to a given post-Altair beacon block root. Depending on the `Accept` header it can be returned either as JSON or SSZ-serialized bytes.")
            .tags(TAG_BEACON)
            .pathParam(BLOCK_ROOT_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Bytes32 blockRoot = Bytes32.fromHexString(request.getPathParameter(BLOCK_ROOT_PARAMETER));
    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        chainDataProvider.getLightClientBoostrap(blockRoot);

    request.respondAsync(
        future.thenApply(
            maybeLightClientBootstrap ->
                maybeLightClientBootstrap
                    .map(
                        bootstrapAndMetadata -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              bootstrapAndMetadata.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(bootstrapAndMetadata);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<LightClientBootstrap>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<LightClientBootstrap> lightClientBootstrapType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache,
            "LightClientBootstrap",
            List.of(
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (bootstrap, milestone) ->
                        milestoneAtBootstrapSlot(schemaDefinitionCache, bootstrap).equals(milestone)
                            && milestone.isGreaterThan(SpecMilestone.PHASE0)
                            && milestone.isLessThan(SpecMilestone.ELECTRA),
                    SpecMilestone.ALTAIR,
                    schemaDefinitions ->
                        SchemaDefinitionsAltair.required(schemaDefinitions)
                            .getLightClientBootstrapSchema()),
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (bootstrap, milestone) ->
                        milestoneAtBootstrapSlot(schemaDefinitionCache, bootstrap).equals(milestone)
                            && milestone.isGreaterThan(SpecMilestone.DENEB),
                    SpecMilestone.ELECTRA,
                    schemaDefinitions ->
                        SchemaDefinitionsElectra.required(schemaDefinitions)
                            .getLightClientBootstrapSchema())));

    return SerializableTypeDefinition.<ObjectAndMetaData<LightClientBootstrap>>object()
        .name("GetLightClientBootstrapResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", lightClientBootstrapType, ObjectAndMetaData::getData)
        .build();
  }

  private static SpecMilestone milestoneAtBootstrapSlot(
      final SchemaDefinitionCache schemaDefinitionCache, final LightClientBootstrap bootstrap) {
    return schemaDefinitionCache.milestoneAtSlot(
        bootstrap.getLightClientHeader().getBeacon().getSlot());
  }
}

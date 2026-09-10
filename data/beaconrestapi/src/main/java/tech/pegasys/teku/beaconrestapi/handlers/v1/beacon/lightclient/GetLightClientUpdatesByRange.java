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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COUNT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.START_PERIOD_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.metadata.LightClientUpdateWithContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetLightClientUpdatesByRange extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/light_client/updates";

  private final ChainDataProvider chainDataProvider;

  public GetLightClientUpdatesByRange(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetLightClientUpdatesByRange(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getLightClientUpdatesByRange")
            .summary("Get `LightClientUpdate` instances in a requested sync committee period range")
            .description(
                "Requests the `LightClientUpdate` instances in the sync committee period range `[start_period, start_period + count)`, leading up to the current head sync committee period as selected by fork choice. Depending on the `Accept` header they can be returned either as JSON or SSZ-serialized bytes.")
            .tags(TAG_BEACON)
            .queryParamRequired(START_PERIOD_PARAMETER)
            .queryParamRequired(COUNT_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                getJsonResponseType(schemaDefinitionCache),
                getSszResponseType())
            .withNotAcceptableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 startPeriod = request.getQueryParameter(START_PERIOD_PARAMETER);
    final int count =
        request.getQueryParameter(COUNT_PARAMETER).min(MAX_REQUEST_LIGHT_CLIENT_UPDATES).intValue();

    request.respondOk(chainDataProvider.getBestLightClientUpdates(startPeriod, count));
  }

  private static SerializableTypeDefinition<List<LightClientUpdateWithContext>> getJsonResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<LightClientUpdate> lightClientUpdateType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache,
            "LightClientUpdate",
            List.of(
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (update, milestone) ->
                        milestoneAtAttestedSlot(schemaDefinitionCache, update).equals(milestone),
                    SpecMilestone.ALTAIR,
                    schemaDefinitions ->
                        SchemaDefinitionsAltair.required(schemaDefinitions)
                            .getLightClientUpdateSchema())));

    return SerializableTypeDefinition.listOf(
        SerializableTypeDefinition.<LightClientUpdateWithContext>object()
            .withField(
                "version",
                MILESTONE_TYPE,
                updateWithContext ->
                    milestoneAtAttestedSlot(schemaDefinitionCache, updateWithContext.update()))
            .withField("data", lightClientUpdateType, LightClientUpdateWithContext::update)
            .build());
  }

  private static SpecMilestone milestoneAtAttestedSlot(
      final SchemaDefinitionCache schemaDefinitionCache, final LightClientUpdate update) {
    return schemaDefinitionCache.milestoneAtSlot(update.getAttestedHeader().getBeacon().getSlot());
  }

  private static ResponseContentTypeDefinition<List<LightClientUpdateWithContext>>
      getSszResponseType() {
    OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<
            List<LightClientUpdateWithContext>>
        serializer =
            (data, out) -> {
              for (final LightClientUpdateWithContext updateWithContext : data) {
                final Bytes payload = updateWithContext.update().sszSerialize();
                out.write(
                    Bytes.ofUnsignedLong(Bytes4.SIZE + payload.size(), ByteOrder.LITTLE_ENDIAN)
                        .toArrayUnsafe());
                out.write(updateWithContext.context().getWrappedBytes().toArrayUnsafe());
                out.write(payload.toArrayUnsafe());
              }
            };

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}

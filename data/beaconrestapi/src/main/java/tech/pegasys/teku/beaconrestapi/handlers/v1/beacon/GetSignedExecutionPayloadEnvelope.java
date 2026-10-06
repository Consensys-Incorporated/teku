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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.executionPayloadAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetSignedExecutionPayloadEnvelope extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/execution_payload_envelopes/{block_id}";

  private final ChainDataProvider chainDataProvider;

  public GetSignedExecutionPayloadEnvelope(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetSignedExecutionPayloadEnvelope(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSignedExecutionPayloadEnvelope")
            .summary("Get signed execution payload envelope")
            .description("Retrieves signed execution payload envelope for a given block id.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                executionPayloadAndMetaDataSszResponseType())
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ExecutionPayloadAndMetaData>> future =
        chainDataProvider.getExecutionPayloadEnvelope(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeExecutionPayloadEnvelopeAndMetaData ->
                maybeExecutionPayloadEnvelopeAndMetaData
                    .map(
                        executionPayloadEnvelopeAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              executionPayloadEnvelopeAndMetaData.milestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(executionPayloadEnvelopeAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ExecutionPayloadAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<SignedExecutionPayloadEnvelope> signedEnvelopeType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache,
            "SignedExecutionPayloadEnvelope",
            List.of(
                new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
                    (signedExecutionPayloadEnvelope, milestone) ->
                        schemaDefinitionCache
                            .milestoneAtSlot(signedExecutionPayloadEnvelope.getSlot())
                            .equals(milestone),
                    SpecMilestone.GLOAS,
                    schemaDefinitions ->
                        SchemaDefinitionsGloas.required(schemaDefinitions)
                            .getSignedExecutionPayloadEnvelopeSchema())));

    return SerializableTypeDefinition.<ExecutionPayloadAndMetaData>object()
        .name("GetSignedExecutionPayloadEnvelopeResponse")
        .withField("version", MILESTONE_TYPE, ExecutionPayloadAndMetaData::milestone)
        .withField(
            EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::executionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::finalized)
        .withField("data", signedEnvelopeType, ExecutionPayloadAndMetaData::data)
        .build();
  }
}

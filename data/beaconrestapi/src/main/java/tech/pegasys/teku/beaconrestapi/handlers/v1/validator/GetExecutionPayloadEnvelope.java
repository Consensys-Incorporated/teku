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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BEACON_BLOCK_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetExecutionPayloadEnvelope extends RestApiEndpoint {
  public static final String ROUTE =
      "/eth/v1/validator/execution_payload_envelopes/{slot}/{beacon_block_root}";

  private final ValidatorDataProvider validatorDataProvider;
  private final SchemaDefinitionCache schemaDefinitionCache;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "The slot for which the execution payload envelope is requested.");

  private static final ParameterMetadata<Bytes32> BEACON_BLOCK_ROOT_PARAM =
      BEACON_BLOCK_ROOT_PARAMETER.withDescription(
          "The beacon block root for the execution payload envelope.");

  public GetExecutionPayloadEnvelope(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetExecutionPayloadEnvelope(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getExecutionPayloadEnvelope")
            .summary("Get execution payload envelope")
            .description(
                """
                Retrieves the cached execution payload envelope for the current proposer's slot, to be
                signed and published via `publishExecutionPayloadEnvelope`.

                The beacon node only caches the envelope for the current slot's locally built payload.
                Requests for older slots or slots where no envelope has been cached will return a 404
                error. This endpoint does not fetch payloads from the execution layer.

                Depending on `Accept` header, the response can be returned either as JSON or as bytes serialized by SSZ.""")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(SLOT_PARAM)
            .pathParam(BEACON_BLOCK_ROOT_PARAM)
            .response(
                SC_OK,
                "Success response",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withBadRequestResponse(
                Optional.of("Invalid request - the slot or beacon_block_root is invalid"))
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .withInternalErrorResponse()
            .withServiceUnavailableResponse()
            .build());
    this.validatorDataProvider = validatorDataProvider;
    this.schemaDefinitionCache = schemaDefinitionCache;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(SLOT_PARAM);
    final Bytes32 beaconBlockRoot = request.getPathParameter(BEACON_BLOCK_ROOT_PARAM);

    final SafeFuture<Optional<ExecutionPayloadEnvelope>> future =
        validatorDataProvider.createUnsignedExecutionPayload(slot, beaconBlockRoot);

    request.respondAsync(
        future.thenApply(
            maybeEnvelope ->
                maybeEnvelope
                    .map(
                        envelope -> {
                          final SpecMilestone milestone =
                              schemaDefinitionCache.milestoneAtSlot(slot);
                          request.header(HEADER_CONSENSUS_VERSION, milestone.lowerCaseName());
                          return AsyncApiResponse.respondOk(
                              new ObjectAndMetaData<>(envelope, milestone, false, true, false));
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<ExecutionPayloadEnvelope>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final ExecutionPayloadEnvelopeSchema envelopeSchema =
        SchemaDefinitionsGloas.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
            .getExecutionPayloadEnvelopeSchema();

    return SerializableTypeDefinition.<ObjectAndMetaData<ExecutionPayloadEnvelope>>object()
        .name("GetExecutionPayloadEnvelopeResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", envelopeSchema.getJsonTypeDefinition(), ObjectAndMetaData::getData)
        .build();
  }
}

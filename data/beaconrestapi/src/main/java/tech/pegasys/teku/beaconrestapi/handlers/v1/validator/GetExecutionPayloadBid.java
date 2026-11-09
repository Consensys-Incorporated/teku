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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BUILDER_INDEX;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetExecutionPayloadBid extends RestApiEndpoint {
  public static final String ROUTE =
      "/eth/v1/validator/execution_payload_bids/{slot}/{builder_index}";

  @SuppressWarnings("unused")
  private final ValidatorDataProvider provider;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "`uint64` Slot for which the execution payload bid is requested. Must be current slot or next slot.");

  private static final ParameterMetadata<UInt64> BUILDER_INDEX_PARAM =
      PARAMETER_BUILDER_INDEX.withDescription(
          "`uint64` Index of the builder from which the execution payload bid is requested.");

  public GetExecutionPayloadBid(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetExecutionPayloadBid(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getExecutionPayloadBid")
            .summary("Get execution payload bid")
            .description(
                "Retrieves execution payload bid for a given slot and builder. Depending on `Accept` header, it can be returned either as json or as bytes serialized by SSZ.")
            .tags(TAG_VALIDATOR)
            .pathParam(SLOT_PARAM)
            .pathParam(BUILDER_INDEX_PARAM)
            .response(SC_OK, "Successful response", getResponseType(schemaDefinitionCache))
            .withBadRequestResponse(
                Optional.of("Invalid request - the slot or builder_index is invalid"))
            .response(
                SC_NOT_FOUND,
                "Execution payload bid not available for the requested slot and builder",
                HTTP_ERROR_RESPONSE_TYPE)
            .response(
                SC_NOT_ACCEPTABLE,
                "Not acceptable - the Accept header is not supported",
                HTTP_ERROR_RESPONSE_TYPE)
            .withInternalErrorResponse()
            .withNotImplementedResponse()
            .build());
    this.provider = provider;
  }

  private static SerializableTypeDefinition<ExecutionPayloadBid> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return SerializableTypeDefinition.object(ExecutionPayloadBid.class)
        .name("ProduceExecutionPayloadBidResponse")
        .withField(
            "data",
            schemaDefinitionCache
                .getSchemaDefinition(SpecMilestone.GLOAS)
                .toVersionGloas()
                .orElseThrow()
                .getExecutionPayloadBidSchema()
                .getJsonTypeDefinition(),
            Function.identity())
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondError(SC_NOT_IMPLEMENTED, "Not implemented");
  }
}

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

package tech.pegasys.teku.beaconrestapi.handlers.v1.builder;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BUILDER;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class GetExpectedWithdrawals extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/builder/states/{state_id}/expected_withdrawals";

  private static final String PROPOSAL_SLOT = "proposal_slot";
  private static final ParameterMetadata<UInt64> PROPOSAL_SLOT_PARAMETER =
      new ParameterMetadata<>(
          PROPOSAL_SLOT,
          CoreTypes.UINT64_TYPE.withDescription(
              "The slot of the block to be proposed. Defaults to the child slot of the state."));
  private final ChainDataProvider chainDataProvider;

  public GetExpectedWithdrawals(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  protected GetExpectedWithdrawals(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNextWithdrawals")
            .summary(
                "Get the withdrawals that are to be included for the block built on the specified state.")
            .description(
                "Get the withdrawals computed from the specified state, that will be included in the block \n"
                    + "    that gets built on the specified state.")
            .tags(TAG_BUILDER)
            .pathParam(PARAMETER_STATE_ID)
            .deprecated(true)
            .queryParam(PROPOSAL_SLOT_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotFoundResponse()
            .withNotImplementedResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<List<Withdrawal>>>> future =
        chainDataProvider.getExpectedWithdrawals(
            request.getPathParameter(PARAMETER_STATE_ID),
            request.getOptionalQueryParameter(PROPOSAL_SLOT_PARAMETER));
    request.respondAsync(
        future.thenApply(
            maybeObjectAndMetadata ->
                maybeObjectAndMetadata
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<Withdrawal>>> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    SerializableTypeDefinition<Withdrawal> withdrawalSerializableTypeDefinition =
        SchemaDefinitionsCapella.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.CAPELLA))
            .getWithdrawalSchema()
            .getJsonTypeDefinition();
    return SerializableTypeDefinition.<ObjectAndMetaData<List<Withdrawal>>>object()
        .name("GetExpectedWithdrawalsResponse")
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("data", listOf(withdrawalSerializableTypeDefinition), ObjectAndMetaData::getData)
        .build();
  }
}

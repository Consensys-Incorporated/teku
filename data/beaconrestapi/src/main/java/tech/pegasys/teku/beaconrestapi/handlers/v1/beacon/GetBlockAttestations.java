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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

public class GetBlockAttestations extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blocks/{block_id}/attestations";
  private final ChainDataProvider chainDataProvider;

  public GetBlockAttestations(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getChainDataProvider(), spec);
  }

  public GetBlockAttestations(final ChainDataProvider chainDataProvider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockAttestations")
            .summary("Get block attestations")
            .description("Retrieves attestations included in requested block.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", getResponseType(spec))
            .withNotFoundResponse()
            .withChainDataResponses()
            .deprecated(true)
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<List<Attestation>>>> future =
        chainDataProvider.getBlockAttestations(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeObjectAndMetaData ->
                maybeObjectAndMetaData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<Attestation>>> getResponseType(
      final Spec spec) {
    final AttestationSchema<?> dataSchema =
        spec.getGenesisSchemaDefinitions().getAttestationSchema();

    return SerializableTypeDefinition.<ObjectAndMetaData<List<Attestation>>>object()
        .name("GetBlockAttestationsResponse")
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField(
            "data",
            listOf(dataSchema.castTypeToAttestationSchema().getJsonTypeDefinition()),
            ObjectAndMetaData::getData)
        .build();
  }
}

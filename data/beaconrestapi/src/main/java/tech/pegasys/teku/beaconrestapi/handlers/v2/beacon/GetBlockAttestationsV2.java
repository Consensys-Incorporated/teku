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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetBlockAttestationsV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/beacon/blocks/{block_id}/attestations";
  private final ChainDataProvider chainDataProvider;

  public GetBlockAttestationsV2(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetBlockAttestationsV2(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockAttestationsV2")
            .summary("Get block attestations")
            .description("Retrieves attestations included in requested block.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<List<Attestation>>>> future =
        chainDataProvider.getBlockAttestations(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeAttestationsAndMetadata ->
                maybeAttestationsAndMetadata
                    .map(
                        attestationsAndMetadata -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              attestationsAndMetadata.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(attestationsAndMetadata);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  @SuppressWarnings("unchecked")
  private static SerializableTypeDefinition<ObjectAndMetaData<List<Attestation>>> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final SerializableOneOfTypeDefinition<List<Attestation>> oneOfTypeDefinition =
        new SerializableOneOfTypeDefinitionBuilder<List<Attestation>>()
            .withType(
                electraAttestationsPredicate(),
                listOf(BeaconRestApiTypes.electraAttestationTypeDef(schemaDefinitionCache)))
            .withType(
                phase0AttestationsPredicate(),
                listOf(BeaconRestApiTypes.phase0AttestationTypeDef(schemaDefinitionCache)))
            .build();

    return SerializableTypeDefinition.<ObjectAndMetaData<List<Attestation>>>object()
        .name("GetBlockAttestationsResponseV2")
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", oneOfTypeDefinition, ObjectAndMetaData::getData)
        .build();
  }

  private static Predicate<List<Attestation>> phase0AttestationsPredicate() {
    // Before Electra attestations do not require committee bits
    return attestations -> attestations.isEmpty() || !attestations.get(0).requiresCommitteeBits();
  }

  private static Predicate<List<Attestation>> electraAttestationsPredicate() {
    // Only once we are in Electra attestations will have committee bits
    return attestations -> !attestations.isEmpty() && attestations.get(0).requiresCommitteeBits();
  }
}

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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class PostPublishExecutionPayloadBid extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/execution_payload_bids";

  private final ValidatorDataProvider validatorDataProvider;

  public PostPublishExecutionPayloadBid(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostPublishExecutionPayloadBid(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SignedExecutionPayloadBid requestBody = request.getRequestBody();

    request.respondAsync(
        validatorDataProvider
            .publishSignedExecutionPayloadBid(requestBody)
            .thenApply(__ -> AsyncApiResponse.respondWithCode(SC_OK))
            .exceptionally(
                error -> {
                  final Throwable cause =
                      error instanceof CompletionException ? error.getCause() : error;
                  if (cause instanceof IllegalArgumentException) {
                    return AsyncApiResponse.respondWithError(SC_BAD_REQUEST, cause.getMessage());
                  }
                  throw new RuntimeException(cause);
                }));
  }

  private static EndpointMetadata createMetadata(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishExecutionPayloadBid")
        .summary("Publish a signed execution payload bid")
        .description(
            "Instructs the beacon node to broadcast a signed execution payload bid to the beacon "
                + "network. The beacon node performs gossip validation before broadcasting.")
        .tags(TAG_BEACON)
        .requestBodyType(
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "SignedExecutionPayloadBid",
                __ ->
                    SchemaDefinitionsGloas.required(
                            schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                        .getSignedExecutionPayloadBidSchema(),
                (bid, milestone) -> milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)),
            context ->
                headerBasedSelector(
                    context.getHeaders(),
                    schemaDefinitionCache,
                    __ ->
                        SchemaDefinitionsGloas.required(
                                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                            .getSignedExecutionPayloadBidSchema()),
            bytes ->
                SchemaDefinitionsGloas.required(
                        schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                    .getSignedExecutionPayloadBidSchema()
                    .sszDeserialize(bytes))
        .header(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the signed execution payload bid being submitted."))
        .response(
            SC_OK, "Signed execution payload bid has been successfully validated and broadcast.")
        .withBadRequestResponse(Optional.of("Invalid execution payload bid."))
        .build();
  }
}

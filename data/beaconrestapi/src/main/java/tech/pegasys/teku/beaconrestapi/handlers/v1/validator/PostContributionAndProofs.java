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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProofSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class PostContributionAndProofs extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/contribution_and_proofs";
  private final ValidatorDataProvider provider;

  public PostContributionAndProofs(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostContributionAndProofs(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("publishContributionAndProofs")
            .summary("Publish multiple contribution and proofs")
            .description(
                "Verifies given sync committee contribution and proofs and publishes on appropriate gossipsub topics.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(getRequestType(schemaDefinitionCache)))
            .response(SC_OK, "Successful response")
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Void> future = provider.sendContributionAndProofs(request.getRequestBody());
    request.respondAsync(future.thenApply(v -> AsyncApiResponse.respondWithCode(SC_OK)));
  }

  private static DeserializableTypeDefinition<SignedContributionAndProof> getRequestType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SignedContributionAndProofSchema typeDefinition =
        SchemaDefinitionsAltair.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ALTAIR))
            .getSignedContributionAndProofSchema();

    return typeDefinition.getJsonTypeDefinition();
  }
}

/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostInclusionList extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/inclusion_list";
  private final ValidatorDataProvider validatorDataProvider;

  public PostInclusionList(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostInclusionList(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("publishInclusionList")
            .summary("Publish an inclusion list")
            .description(
                "Verifies given inclusion list and publishes it on appropriate gossipsub topic.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .headerRequired(
                ETH_CONSENSUS_VERSION_TYPE.withDescription(
                    "The active consensus version to which the inclusion list being submitted belongs."))
            .requestBodyType(getRequestType(schemaDefinitionCache))
            .response(SC_OK, "Request successful")
            .withBadRequestResponse(Optional.of("Invalid inclusion list"))
            .withInternalErrorResponse()
            .build());
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SignedInclusionList signedInclusionList = request.getRequestBody();

    final SafeFuture<List<SubmitDataError>> future =
        validatorDataProvider.sendSignedInclusionList(List.of(signedInclusionList));
    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              return AsyncApiResponse.respondWithError(
                  SC_BAD_REQUEST, PARTIAL_PUBLISH_FAILURE_MESSAGE);
            }));
  }

  private static DeserializableTypeDefinition<SignedInclusionList> getRequestType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return withDataWrapper(
        "PublishInclusionListRequest",
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.HEZE)
            .toVersionHeze()
            .orElseThrow()
            .getSignedInclusionListSchema()
            .getJsonTypeDefinition());
  }
}

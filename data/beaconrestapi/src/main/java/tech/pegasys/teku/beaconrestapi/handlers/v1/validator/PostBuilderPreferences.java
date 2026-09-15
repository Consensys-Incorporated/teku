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

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.schemas.ApiSchemas;

public class PostBuilderPreferences extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/builder_preferences";

  private final ValidatorDataProvider provider;

  public PostBuilderPreferences(final DataProvider dataProvider) {
    this(dataProvider.getValidatorDataProvider());
  }

  public PostBuilderPreferences(final ValidatorDataProvider provider) {
    super(createMetadata());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        provider
            .submitBuilderPreferences(request.getRequestBody())
            .thenApply(
                errors -> {
                  if (errors.isEmpty()) {
                    return AsyncApiResponse.respondWithCode(SC_OK);
                  }
                  return AsyncApiResponse.respondWithObject(
                      SC_BAD_REQUEST,
                      ErrorListBadRequest.convert(PARTIAL_PUBLISH_FAILURE_MESSAGE, errors));
                }));
  }

  private static EndpointMetadata createMetadata() {
    return EndpointMetadata.post(ROUTE)
        .operationId("submitBuilderPreferences")
        .summary("Submit builder preferences")
        .description(
            """
               Submits per-builder preferences for one or more proposers. The request body is a flat list of
               `BuilderPreferencesEntry` objects, each naming the proposer in `proposer_pubkey`; the beacon
               node submits each to the builder-API `submitBuilderPreferences` endpoint at the entry's `url`.
               Applicable from the Gloas fork onwards.

               Notes:
               - Each entry targets one builder `url`, to which the beacon node makes one submission.
               - Entries are identified by `proposer_pubkey`, so several proposers MAY submit to the same `url`.
               - The beacon node routes each entry by its `url` and submits to that builder's
                 `submitBuilderPreferences` for the entry's `proposer_pubkey`, forwarding the `auth` and
                 `max_execution_payment`.
               - Validators MAY submit in the epoch prior to proposing (from `state.proposer_lookahead`), so
                 builders hold the preferences before the bid request arrives.""")
        .tags(TAG_VALIDATOR)
        .requestBodyType(
            ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.getJsonTypeDefinition(),
            ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA::sszDeserialize)
        .response(SC_OK, "Every entry was submitted and its builder accepted it")
        .response(
            SC_BAD_REQUEST,
            "Errors with one or more builder preferences entries",
            ErrorListBadRequest.getJsonTypeDefinition())
        .withUnsupportedMediaTypeResponse()
        .withInternalErrorResponse()
        .withServiceUnavailableResponse()
        .build();
  }
}

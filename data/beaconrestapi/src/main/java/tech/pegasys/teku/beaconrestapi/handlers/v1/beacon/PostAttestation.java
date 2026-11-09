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

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAttestation extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final ValidatorDataProvider provider;

  public PostAttestation(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostAttestation(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitPoolAttestations")
            .summary("Submit Attestation objects to node")
            .description(
                "Submit signed attestations to the beacon node to be validated and submitted if valid.\n\n"
                    + "This endpoint does not protected against slashing.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(
                    schemaDefinitionCache
                        .getSchemaDefinition(SpecMilestone.PHASE0)
                        .getAttestationSchema()
                        .getJsonTypeDefinition()))
            .response(SC_OK, "Attestations are stored in pool and broadcast on appropriate subnet")
            .response(
                SC_BAD_REQUEST,
                "Errors with one or more attestations",
                ErrorListBadRequest.getJsonTypeDefinition())
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<Attestation> attestations = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future = provider.submitAttestations(attestations);

    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              final ErrorListBadRequest data =
                  ErrorListBadRequest.convert(PARTIAL_PUBLISH_FAILURE_MESSAGE, errors);
              return AsyncApiResponse.respondWithObject(SC_BAD_REQUEST, data);
            }));
  }
}

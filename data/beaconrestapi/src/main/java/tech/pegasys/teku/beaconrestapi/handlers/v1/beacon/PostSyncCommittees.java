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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessageSchema;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostSyncCommittees extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/sync_committees";
  private final ValidatorDataProvider provider;

  public PostSyncCommittees(final DataProvider provider) {
    this(provider.getValidatorDataProvider());
  }

  public PostSyncCommittees(final ValidatorDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitPoolSyncCommitteeSignatures")
            .summary("Submit sync committee signatures to node")
            .description(
                """
                      Submits sync committee message objects to the node.

                      Sync committee messages are not present in phase0, but are required for Altair networks.

                      If a sync committee message is validated successfully the node MUST publish that sync committee message on all applicable subnets.

                      If one or more sync committee messages fail validation the node MUST return a 400 error with details of which sync committee messages have failed, and why.
                      """)
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(
                    SyncCommitteeMessageSchema.INSTANCE.getJsonTypeDefinition()))
            .response(
                SC_OK,
                "Sync committee signatures are stored in pool and broadcast on appropriate subnet")
            .response(
                SC_BAD_REQUEST,
                "Errors with one or more sync committee signatures",
                getBadRequestResponseTypes())
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<SyncCommitteeMessage> messages = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future = provider.submitCommitteeSignatures(messages);

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

  private static SerializableOneOfTypeDefinition<Object> getBadRequestResponseTypes() {
    final SerializableOneOfTypeDefinitionBuilder<Object> builder =
        new SerializableOneOfTypeDefinitionBuilder<>().title("BadRequestResponses");
    builder.withType(
        value -> value instanceof ErrorListBadRequest, ErrorListBadRequest.getJsonTypeDefinition());
    builder.withType(value -> value instanceof HttpErrorResponse, HTTP_ERROR_RESPONSE_TYPE);
    return builder.build();
  }
}

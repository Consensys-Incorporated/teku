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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostProposerSlashing extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/proposer_slashings";
  private final NodeDataProvider nodeDataProvider;

  public PostProposerSlashing(final DataProvider dataProvider) {
    this(dataProvider.getNodeDataProvider());
  }

  public PostProposerSlashing(final NodeDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitPoolProposerSlashings")
            .summary("Submit ProposerSlashing object to node's pool")
            .description(
                "Submits proposer slashing object to node's pool and, if it passes validation, the node MUST broadcast it to network.")
            .tags(TAG_BEACON)
            .requestBodyType(ProposerSlashing.SSZ_SCHEMA.getJsonTypeDefinition())
            .response(
                SC_OK,
                "Proposer Slashing has been successfully validated, added to the pool, and broadcast.")
            .response(
                SC_BAD_REQUEST,
                "Invalid proposer slashing, it will never pass validation so it's rejected",
                HTTP_ERROR_RESPONSE_TYPE)
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final ProposerSlashing proposerSlashing = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future =
        nodeDataProvider.postProposerSlashing(proposerSlashing);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse(
                            "Invalid proposer slashing, it will never pass validation so it's rejected"));
              } else {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
            }));
  }
}

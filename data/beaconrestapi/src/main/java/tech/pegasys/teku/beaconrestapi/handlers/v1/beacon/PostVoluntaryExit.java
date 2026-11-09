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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostVoluntaryExit extends RestApiEndpoint {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/pool/voluntary_exits";
  private final NodeDataProvider nodeDataProvider;

  public PostVoluntaryExit(final DataProvider dataProvider) {
    this(dataProvider.getNodeDataProvider());
  }

  public PostVoluntaryExit(final NodeDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitPoolVoluntaryExit")
            .summary("Submit SignedVoluntaryExit object to node's pool")
            .description(
                "Submits signed voluntary exit object to node's pool and if it passes validation node MUST broadcast it to network.")
            .tags(TAG_BEACON)
            .requestBodyType(SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition())
            .response(
                SC_OK,
                "Signed voluntary exit has been successfully validated, added to the pool, and broadcast.")
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SignedVoluntaryExit exit = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future = nodeDataProvider.postVoluntaryExit(exit);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                LOG.debug(
                    "Voluntary exit for validator id {} failed status {}: {}",
                    exit.getMessage().getValidatorIndex(),
                    internalValidationResult.code(),
                    internalValidationResult.getDescription().orElse(""));
                if (internalValidationResult.isIgnore()) {
                  LOG.info(
                      "Voluntary exit for validator id {} is already seen. It will be periodically resent as required.",
                      exit.getMessage().getValidatorIndex());
                  // though the API says we MUST send over network to respond 200, we've already
                  // sent it if it's in our pool, and the pool will manage retries periodically.
                  return AsyncApiResponse.respondWithCode(200);
                }
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse(
                            "Invalid voluntary exit, it will never pass validation so it's rejected"));
              }
              return AsyncApiResponse.respondWithCode(SC_OK);
            }));
  }
}

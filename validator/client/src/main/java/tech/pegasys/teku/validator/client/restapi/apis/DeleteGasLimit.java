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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GAS_LIMIT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class DeleteGasLimit extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/{pubkey}/gas_limit";

  private final Optional<ProposerConfigManager> proposerConfigManager;

  public DeleteGasLimit(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.delete(ROUTE)
            .operationId("DeleteGasLimit")
            .summary("Delete configured gas limit")
            .withBearerAuthSecurity()
            .tags(TAG_GAS_LIMIT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description("Delete a configured gas limit for the specified public key.")
            .response(SC_NO_CONTENT, "Success")
            .response(
                SC_FORBIDDEN,
                "The gas limit is set in configuration, and cannot be updated or removed via api.")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.proposerConfigManager = proposerConfigManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final ProposerConfigManager manager =
        proposerConfigManager.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Bellatrix is not currently scheduled on this network, unable to set fee recipient."));

    if (!manager.isOwnedValidator(publicKey)) {
      request.respondError(SC_NOT_FOUND, "Gas limit not found");
      return;
    }
    if (!manager.deleteGasLimit(publicKey)) {
      request.respondError(SC_FORBIDDEN, "Gas limit for public key could not be removed.");
      return;
    }
    request.respondWithCode(SC_NO_CONTENT);
  }
}

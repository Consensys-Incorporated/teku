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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GRAFFITI;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.api.GraffitiManagementException;
import tech.pegasys.teku.validator.api.GraffitiManager;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;

public class DeleteGraffiti extends RestApiEndpoint {
  private final KeyManager keyManager;
  private final GraffitiManager graffitiManager;

  public DeleteGraffiti(final KeyManager keyManager, final GraffitiManager graffitiManager) {
    super(
        EndpointMetadata.delete(GetGraffiti.ROUTE)
            .operationId("deleteGraffiti")
            .summary("Delete Configured Graffiti")
            .description("Delete the configured graffiti for the specified public key.")
            .tags(TAG_GRAFFITI)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .response(
                SC_NO_CONTENT,
                "Successfully removed the graffiti, or there was no graffiti set for the requested public key.")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.keyManager = keyManager;
    this.graffitiManager = graffitiManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);

    final Optional<Validator> maybeValidator = keyManager.getValidatorByPublicKey(publicKey);
    if (maybeValidator.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Validator not found");
      return;
    }

    try {
      graffitiManager.deleteGraffiti(publicKey);
      request.respondWithCode(SC_NO_CONTENT);
    } catch (GraffitiManagementException e) {
      request.respondError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }
}

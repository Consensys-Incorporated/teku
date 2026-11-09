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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_KEY_MANAGEMENT;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;

public class DeleteKeys extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/keystores";
  private final KeyManager keyManager;
  private final Path slashingProtectionPath;

  public DeleteKeys(final KeyManager keyManager, final Path slashingProtectionPath) {
    super(
        EndpointMetadata.delete(ROUTE)
            .operationId("DeleteKeys")
            .summary("Delete Keys")
            .withBearerAuthSecurity()
            .tags(TAG_KEY_MANAGEMENT)
            .description(
                "DELETE must delete all keys from `request.pubkeys` that are known to the key manager."
                    + "<br>Additionally, DELETE must fetch the slashing protection data for the requested keys, "
                    + "which must be retained after the response has been sent. "
                    + "<br>In the case of two identical delete requests being made, both will have access to slashing protection data."
                    + "<br>In a single atomic sequential operation the key manager must:"
                    + "<br>1. Guarantee that key(s) can not produce any more signatures; only then"
                    + "<br>2. Delete key(s) and serialize its associated slashing protection data"
                    + "<br><br>DELETE should never return a 404 response, even if all pubkeys from `request.pubkeys` have no key stores "
                    + "or slashing protection data.")
            .requestBodyType(ValidatorTypes.DELETE_KEYS_REQUEST)
            .response(SC_OK, "Success response", ValidatorTypes.DELETE_KEYS_RESPONSE_TYPE)
            .withAuthenticationResponses()
            .build());

    this.keyManager = keyManager;
    this.slashingProtectionPath = slashingProtectionPath;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    DeleteKeysRequest deleteRequest = request.getRequestBody();
    request.respondOk(
        keyManager.deleteValidators(deleteRequest.getPublicKeys(), slashingProtectionPath));
  }
}

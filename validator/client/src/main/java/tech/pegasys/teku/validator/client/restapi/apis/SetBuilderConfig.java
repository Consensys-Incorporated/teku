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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_BUILDER_CONFIG;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.BUILDER_CONFIG_TYPE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;

public class SetBuilderConfig extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/{pubkey}/builder_config";
  private final Optional<ProposerConfigManager> proposerConfigManager;

  public SetBuilderConfig(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("setBuilderConfig")
            .summary("Set Builder Configuration")
            .withBearerAuthSecurity()
            .tags(TAG_BUILDER_CONFIG)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                """
                    Set the builder configuration for an individual validator public key. The submission replaces
                    the key's stored configuration in full; the server MUST NOT merge it with what was stored
                    before.

                    Each entry in the `builders` array MUST contain a `url`, and no two entries may share both
                    their `url` and their `auth_data`. The server MUST reject a submission that violates either
                    rule with a 400 rather than storing a partial list or silently dropping an entry.

                    A `POST` body of `{}` stores a configuration with no overrides, which resolves identically to
                    having none, so it is behaviorally equivalent to `DELETE`. An empty `builders` array is a
                    configuration that names no builders, not a removal.
                    """)
            .requestBodyType(BUILDER_CONFIG_TYPE)
            .response(SC_ACCEPTED, "Success")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.proposerConfigManager = proposerConfigManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final BuilderConfig body = request.getRequestBody();

    final ProposerConfigManager manager =
        proposerConfigManager.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Bellatrix is not currently scheduled on this network, unable to set builder config."));

    if (!manager.isOwnedValidator(publicKey)) {
      request.respondError(SC_NOT_FOUND, "Validator public key not found");
      return;
    }

    try {
      manager.setBuilderConfig(publicKey, body);
    } catch (final IllegalArgumentException | InvalidConfigurationException e) {
      request.respondError(SC_BAD_REQUEST, e.getMessage());
      return;
    }
    request.respondWithCode(SC_ACCEPTED);
  }
}

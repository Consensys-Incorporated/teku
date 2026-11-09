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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostRemoteKeysRequest;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class PostRemoteKeys extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/remotekeys";

  @SuppressWarnings("unused")
  private final KeyManager keyManager;

  private final Optional<DoppelgangerDetector> maybeDoppelgangerDetector;
  private final SlashingRiskAction doppelgangerDetectionAction;

  public PostRemoteKeys(
      final KeyManager keyManager,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final SlashingRiskAction doppelgangerDetectionAction) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("ImportRemoteKeys")
            .summary("Import Remote Keys")
            .withBearerAuthSecurity()
            .tags(TAG_KEY_MANAGEMENT)
            .description("Import remote keys for the validator client to request duties for.")
            .requestBodyType(ValidatorTypes.POST_REMOTE_KEYS_REQUEST)
            .response(SC_OK, "Success response", ValidatorTypes.POST_KEYS_RESPONSE)
            .withAuthenticationResponses()
            .build());
    this.keyManager = keyManager;
    this.maybeDoppelgangerDetector = maybeDoppelgangerDetector;
    this.doppelgangerDetectionAction = doppelgangerDetectionAction;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final PostRemoteKeysRequest body = request.getRequestBody();

    if (body.getExternalValidators().isEmpty()) {
      request.respondOk(Collections.emptyList());
      return;
    }

    List<ExternalValidator> validators = body.getExternalValidators();
    request.respondOk(
        keyManager.importExternalValidators(
            validators, maybeDoppelgangerDetector, doppelgangerDetectionAction));
  }
}

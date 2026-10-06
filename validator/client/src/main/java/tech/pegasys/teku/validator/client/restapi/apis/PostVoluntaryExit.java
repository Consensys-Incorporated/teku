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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_VOLUNTARY_EXIT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.EPOCH_QUERY_TYPE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.VoluntaryExitDataProvider;

public class PostVoluntaryExit extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/voluntary_exit";

  public static final SerializableTypeDefinition<SignedVoluntaryExit>
      SIGNED_VOLUNTARY_EXIT_RESPONSE_TYPE =
          SerializableTypeDefinition.<SignedVoluntaryExit>object()
              .name("SignedVoluntaryExitResponse")
              .withField(
                  "data",
                  SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition(),
                  Function.identity())
              .build();

  private final VoluntaryExitDataProvider provider;
  private final KeyManager keyManager;

  public PostVoluntaryExit(final VoluntaryExitDataProvider provider, final KeyManager keyManager) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("signVoluntaryExit")
            .summary("Sign Voluntary Exit")
            .tags(TAG_VOLUNTARY_EXIT)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .queryParam(EPOCH_QUERY_TYPE)
            .description(
                "Create a signed voluntary exit message for an active validator, identified by a public key known to "
                    + "the validator client. This endpoint returns a SignedVoluntaryExit object, which can be used to "
                    + "initiate voluntary exit via the beacon node's submitPoolVoluntaryExit endpoint.")
            .response(SC_OK, "Success response", SIGNED_VOLUNTARY_EXIT_RESPONSE_TYPE)
            .withNotFoundResponse()
            .withUnauthorizedResponse()
            .withForbiddenResponse()
            .build());
    this.provider = provider;
    this.keyManager = keyManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    if (keyManager.getValidatorByPublicKey(publicKey).isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Validator public key not found");
      return;
    }

    final Optional<UInt64> maybeEpoch = request.getOptionalQueryParameter(EPOCH_QUERY_TYPE);
    final SafeFuture<SignedVoluntaryExit> future =
        provider.getSignedVoluntaryExit(publicKey, maybeEpoch);
    request.respondAsync(future.thenApply(AsyncApiResponse::respondOk));
  }
}

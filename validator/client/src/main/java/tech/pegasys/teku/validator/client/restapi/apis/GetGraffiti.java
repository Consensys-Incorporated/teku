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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.PUBKEY_API_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GRAFFITI;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.GraffitiManagementException;
import tech.pegasys.teku.validator.api.UpdatableGraffitiProvider;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;

public class GetGraffiti extends RestApiEndpoint {
  static final String ROUTE = "/eth/v1/validator/{pubkey}/graffiti";
  private final KeyManager keyManager;

  public static final DeserializableTypeDefinition<Bytes32> GRAFFITI_TYPE =
      DeserializableTypeDefinition.string(Bytes32.class)
          .formatter(GetGraffiti::processGraffitiString)
          .parser(Bytes32Parser::toBytes32)
          .example("Example graffiti")
          .description("Bytes32 string")
          .format("byte")
          .build();

  private static final SerializableTypeDefinition<Optional<Bytes32>> GRAFFITI_RESPONSE_TYPE =
      SerializableTypeDefinition.<Optional<Bytes32>>object()
          .withOptionalField("pubkey", PUBKEY_API_TYPE, value -> Optional.empty())
          .withField(
              "graffiti",
              GRAFFITI_TYPE,
              graffiti -> graffiti.orElse(Bytes32Parser.toBytes32(Bytes.EMPTY.toArray())))
          .build();

  private static final SerializableTypeDefinition<Optional<Bytes32>> RESPONSE_TYPE =
      SerializableTypeDefinition.<Optional<Bytes32>>object()
          .name("GraffitiResponse")
          .withField("data", GRAFFITI_RESPONSE_TYPE, Function.identity())
          .build();

  public GetGraffiti(final KeyManager keyManager) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGraffiti")
            .summary("Get Graffiti")
            .description(
                "Get the graffiti for an individual validator. If no graffiti is set explicitly, returns the process-wide default.")
            .tags(TAG_GRAFFITI)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .response(SC_OK, "Success response", RESPONSE_TYPE)
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.keyManager = keyManager;
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
      final UpdatableGraffitiProvider provider =
          (UpdatableGraffitiProvider) maybeValidator.get().getGraffitiProvider();
      request.respondOk(provider.getUnsafe());
    } catch (GraffitiManagementException e) {
      request.respondError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private static String processGraffitiString(final Bytes32 graffiti) {
    return new String(graffiti.toArrayUnsafe(), StandardCharsets.UTF_8).strip().replace("\0", "");
  }
}

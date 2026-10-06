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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GAS_LIMIT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.SetGasLimitException;

public class SetGasLimit extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/{pubkey}/gas_limit";
  private final Optional<ProposerConfigManager> proposerConfigManager;

  private static final DeserializableTypeDefinition<SetGasLimit.SetGasLimitBody>
      GAS_LIMIT_REQUEST_BODY =
          DeserializableTypeDefinition.object(SetGasLimit.SetGasLimitBody.class)
              .name("SetGasLimitBody")
              .initializer(SetGasLimit.SetGasLimitBody::new)
              .withField(
                  "gas_limit",
                  CoreTypes.UINT64_TYPE,
                  SetGasLimit.SetGasLimitBody::getGasLimit,
                  SetGasLimit.SetGasLimitBody::setGasLimit)
              .build();

  public SetGasLimit(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("SetGasLimit")
            .summary("Set validator gas limit")
            .withBearerAuthSecurity()
            .tags(TAG_GAS_LIMIT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                """
                    Set the gas limit for an individual validator. This limit will be propagated to the beacon node for use on future block proposals.
                    The beacon node is responsible for informing external block builders of the change.
                    The server may return a 400 status code if no external builder is configured.
                    WARNING: The gas_limit is not used on Phase0 or Altair networks.
                    """)
            .requestBodyType(GAS_LIMIT_REQUEST_BODY)
            .response(SC_ACCEPTED, "Success")
            .response(SC_SERVICE_UNAVAILABLE, "Unable to update gas limit at this time")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.proposerConfigManager = proposerConfigManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final SetGasLimit.SetGasLimitBody body = request.getRequestBody();
    if (body.gasLimit.equals(UInt64.ZERO)) {
      request.respondError(
          SC_BAD_REQUEST,
          "Gas limit cannot be set to 0. It must match the regex: ^[1-9][0-9]{0,19}$");
      return;
    }

    final ProposerConfigManager manager =
        proposerConfigManager.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Bellatrix is not currently scheduled on this network, unable to set gas limit."));

    if (!manager.isOwnedValidator(publicKey)) {
      request.respondError(SC_NOT_FOUND, "Validator public key not found");
      return;
    }

    try {
      manager.setGasLimit(publicKey, body.getGasLimit());
    } catch (SetGasLimitException e) {
      request.respondError(SC_BAD_REQUEST, e.getMessage());
      return;
    }
    request.respondWithCode(SC_ACCEPTED);
  }

  public static class SetGasLimitBody {
    private UInt64 gasLimit;

    public SetGasLimitBody() {}

    public SetGasLimitBody(final UInt64 gasLimit) {
      this.gasLimit = gasLimit;
    }

    public UInt64 getGasLimit() {
      return gasLimit;
    }

    public void setGasLimit(final UInt64 gasLimit) {
      this.gasLimit = gasLimit;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SetGasLimit.SetGasLimitBody that = (SetGasLimit.SetGasLimitBody) o;
      return Objects.equals(gasLimit, that.getGasLimit());
    }

    @Override
    public int hashCode() {
      return Objects.hash(gasLimit);
    }
  }
}

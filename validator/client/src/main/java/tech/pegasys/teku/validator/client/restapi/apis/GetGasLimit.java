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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PUBKEY;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GAS_LIMIT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class GetGasLimit extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/{pubkey}/gas_limit";
  private final Optional<ProposerConfigManager> proposerConfigManager;

  private static final SerializableTypeDefinition<GetGasLimit.GetGasLimitResponse> GAS_LIMIT_DATA =
      SerializableTypeDefinition.object(GetGasLimit.GetGasLimitResponse.class)
          .name("GetGasLimitData")
          .withField(
              "gas_limit", CoreTypes.UINT64_TYPE, GetGasLimit.GetGasLimitResponse::getGasLimit)
          .withField(
              PUBKEY, SharedApiTypes.PUBKEY_API_TYPE, GetGasLimit.GetGasLimitResponse::getPublicKey)
          .build();

  private static final SerializableTypeDefinition<GetGasLimit.GetGasLimitResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GetGasLimit.GetGasLimitResponse.class)
          .withField("data", GAS_LIMIT_DATA, Function.identity())
          .build();

  public GetGasLimit(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetGasLimit")
            .summary("Get Validator Gas Limit")
            .withBearerAuthSecurity()
            .tags(TAG_GAS_LIMIT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                """
                    Get the execution gas limit for an individual validator. This gas limit is the one used by the validator when proposing blocks via an external builder. If no limit has been set explicitly for a key then the process-wide default will be returned.
                    The server may return a 400 status code if no external builder is configured.
                    WARNING: The gas_limit is not used on Phase0 or Altair networks.
                    """)
            .response(SC_OK, "Success response", RESPONSE_TYPE)
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
    request.respondOk(new GetGasLimitResponse(manager.getGasLimit(publicKey), publicKey));
  }

  static class GetGasLimitResponse {
    private final UInt64 gasLimit;
    private final BLSPublicKey publicKey;

    public GetGasLimitResponse(final UInt64 gasLimit, final BLSPublicKey publicKey) {
      this.gasLimit = gasLimit;
      this.publicKey = publicKey;
    }

    public UInt64 getGasLimit() {
      return gasLimit;
    }

    public BLSPublicKey getPublicKey() {
      return publicKey;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetGasLimitResponse that = (GetGasLimitResponse) o;
      return gasLimit.equals(that.gasLimit) && publicKey.equals(that.publicKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(gasLimit, publicKey);
    }
  }
}

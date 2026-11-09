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

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PUBKEY;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_FEE_RECIPIENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class GetFeeRecipient extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/feerecipient";
  private final Optional<ProposerConfigManager> proposerConfigManager;

  private static final SerializableTypeDefinition<GetFeeRecipientResponse> FEE_RECIPIENT_DATA =
      SerializableTypeDefinition.object(GetFeeRecipientResponse.class)
          .name("GetFeeRecipientData")
          .withField("ethaddress", ETH1ADDRESS_TYPE, GetFeeRecipientResponse::getEthAddress)
          .withOptionalField(
              PUBKEY, SharedApiTypes.PUBKEY_API_TYPE, GetFeeRecipientResponse::getPublicKey)
          .build();

  private static final SerializableTypeDefinition<GetFeeRecipientResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GetFeeRecipientResponse.class)
          .withField("data", FEE_RECIPIENT_DATA, Function.identity())
          .build();

  public GetFeeRecipient(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetFeeRecipient")
            .summary("Get validator fee recipient")
            .withBearerAuthSecurity()
            .tags(TAG_FEE_RECIPIENT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                "List the validator public key to eth address mapping for fee recipient feature on a specific public key. "
                    + "The validator public key will return with the default fee recipient address if a specific one was not found.\n\n"
                    + "WARNING: The fee_recipient is not used on Phase0 or Altair networks.")
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

    final Optional<Eth1Address> maybeFeeRecipient =
        manager.isOwnedValidator(publicKey) ? manager.getFeeRecipient(publicKey) : Optional.empty();
    if (maybeFeeRecipient.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Fee recipient not found");
      return;
    }
    request.respondOk(new GetFeeRecipientResponse(maybeFeeRecipient.get()));
  }

  static class GetFeeRecipientResponse {
    private final Eth1Address ethAddress;
    private final Optional<BLSPublicKey> publicKey;

    public GetFeeRecipientResponse(
        final Eth1Address ethAddress, final Optional<BLSPublicKey> publicKey) {
      this.ethAddress = ethAddress;
      this.publicKey = publicKey;
    }

    public GetFeeRecipientResponse(final Eth1Address ethAddress) {
      this(ethAddress, Optional.empty());
    }

    public Eth1Address getEthAddress() {
      return ethAddress;
    }

    public Optional<BLSPublicKey> getPublicKey() {
      return publicKey;
    }
  }
}

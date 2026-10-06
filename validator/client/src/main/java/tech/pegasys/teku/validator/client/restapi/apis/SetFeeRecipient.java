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

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_FEE_RECIPIENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.SetFeeRecipientException;

public class SetFeeRecipient extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/feerecipient";

  private static final DeserializableTypeDefinition<SetFeeRecipientBody>
      FEE_RECIPIENT_REQUEST_BODY =
          DeserializableTypeDefinition.object(SetFeeRecipientBody.class)
              .name("SetFeeRecipientBody")
              .initializer(SetFeeRecipientBody::new)
              .withField(
                  "ethaddress",
                  ETH1ADDRESS_TYPE,
                  SetFeeRecipientBody::getEth1Address,
                  SetFeeRecipientBody::setEth1Address)
              .build();

  // proposerConfigManager is only empty when debug tools is creating documentation
  private final Optional<ProposerConfigManager> proposerConfigManager;

  public SetFeeRecipient(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("SetFeeRecipient")
            .summary("Set validator fee recipient")
            .withBearerAuthSecurity()
            .tags(TAG_FEE_RECIPIENT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                "Sets the validator client fee recipient mapping which will then update the beacon node. "
                    + "Existing mappings for the same validator public key will be overwritten.\n\n"
                    + "Configuration file settings will take precedence over this API, so if your validator fee recipient "
                    + "configuration file contains this public key, it will need to be removed before attempting to update with this api. "
                    + "Cannot specify a fee recipient of 0x00 via the API.\n\n"
                    + "WARNING: The fee_recipient is not used on Phase0 or Altair networks.")
            .requestBodyType(FEE_RECIPIENT_REQUEST_BODY)
            .response(SC_ACCEPTED, "Success")
            .response(SC_SERVICE_UNAVAILABLE, "Unable to update fee recipient at this time")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.proposerConfigManager = proposerConfigManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final SetFeeRecipientBody body = request.getRequestBody();
    final ProposerConfigManager manager =
        proposerConfigManager.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Bellatrix is not currently scheduled on this network, unable to set fee recipient."));

    if (!manager.isOwnedValidator(publicKey)) {
      request.respondError(SC_NOT_FOUND, "Validator public key not found");
      return;
    }

    try {
      manager.setFeeRecipient(publicKey, body.getEth1Address());
    } catch (SetFeeRecipientException e) {
      request.respondError(SC_BAD_REQUEST, e.getMessage());
      return;
    }
    request.respondWithCode(SC_ACCEPTED);
  }

  public static class SetFeeRecipientBody {
    private Eth1Address eth1Address;

    public SetFeeRecipientBody() {}

    public SetFeeRecipientBody(final Eth1Address eth1Address) {
      this.eth1Address = eth1Address;
    }

    public Eth1Address getEth1Address() {
      return eth1Address;
    }

    public void setEth1Address(final Eth1Address eth1Address) {
      this.eth1Address = eth1Address;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SetFeeRecipientBody that = (SetFeeRecipientBody) o;
      return Objects.equals(eth1Address, that.eth1Address);
    }

    @Override
    public int hashCode() {
      return Objects.hash(eth1Address);
    }
  }
}

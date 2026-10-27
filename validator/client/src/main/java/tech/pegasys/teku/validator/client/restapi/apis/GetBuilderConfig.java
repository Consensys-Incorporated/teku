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
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_BUILDER_CONFIG;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.BUILDER_CONFIG_TYPE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;

public class GetBuilderConfig extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/{pubkey}/builder_config";
  private final Optional<ProposerConfigManager> proposerConfigManager;

  private static final SerializableTypeDefinition<BuilderConfig> RESPONSE_TYPE =
      SerializableTypeDefinition.object(BuilderConfig.class)
          .name("GetBuilderConfigResponse")
          .withField("data", BUILDER_CONFIG_TYPE, Function.identity())
          .build();

  public GetBuilderConfig(final Optional<ProposerConfigManager> proposerConfigManager) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBuilderConfig")
            .summary("Get Builder Configuration")
            .withBearerAuthSecurity()
            .tags(TAG_BUILDER_CONFIG)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description(
                """
                    Get the builder configuration in effect for an individual validator public key, with omitted values resolved.
                    A key with no configuration of its own is returned with the validator client's own configuration.
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
                    "Bellatrix is not currently scheduled on this network, unable to get builder config."));

    if (!manager.isOwnedValidator(publicKey)) {
      request.respondError(SC_NOT_FOUND, "Validator public key not found");
      return;
    }
    final ResolvedBuilderConfig resolvedBuilderConfig = manager.resolveBuilderConfig(publicKey);
    final BuilderConfig builderConfigForValidator =
        new BuilderConfig(
            Optional.of(resolvedBuilderConfig.minBid()),
            Optional.of(resolvedBuilderConfig.builderBoostFactor()),
            Optional.of(
                resolvedBuilderConfig.builders().stream()
                    .map(
                        resolvedBuilderEntry ->
                            new BuilderEntry(
                                resolvedBuilderEntry.url().toExternalForm(),
                                Optional.of(resolvedBuilderEntry.authData()),
                                Optional.of(resolvedBuilderEntry.builderPubkeys()),
                                Optional.of(resolvedBuilderEntry.maxExecutionPayment()),
                                Optional.of(resolvedBuilderEntry.minBid()),
                                Optional.of(resolvedBuilderEntry.builderBoostFactor())))
                    .toList()));
    request.respondOk(builderConfigForValidator);
  }
}

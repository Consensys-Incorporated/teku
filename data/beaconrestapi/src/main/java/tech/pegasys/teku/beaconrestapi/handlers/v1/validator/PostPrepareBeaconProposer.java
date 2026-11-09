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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;

public class PostPrepareBeaconProposer extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/prepare_beacon_proposer";

  private final ValidatorDataProvider validatorDataProvider;
  private final boolean isProposerDefaultFeeRecipientDefined;

  public PostPrepareBeaconProposer(final DataProvider provider) {
    this(
        provider.getValidatorDataProvider(),
        provider.getNodeDataProvider().isProposerDefaultFeeRecipientDefined());
  }

  public PostPrepareBeaconProposer(
      final ValidatorDataProvider validatorDataProvider,
      final boolean isProposerDefaultFeeRecipientDefined) {
    super(createMetadata());
    this.validatorDataProvider = validatorDataProvider;
    this.isProposerDefaultFeeRecipientDefined = isProposerDefaultFeeRecipientDefined;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (!isProposerDefaultFeeRecipientDefined) {
      STATUS_LOG.warnMissingProposerDefaultFeeRecipientWithPreparedBeaconProposerBeingCalled();
    }
    final List<BeaconPreparableProposer> proposers = request.getRequestBody();
    request.respondAsync(
        validatorDataProvider
            .prepareBeaconProposer(proposers)
            .thenApply(AsyncApiResponse::respondOk));
  }

  private static EndpointMetadata createMetadata() {
    return EndpointMetadata.post(ROUTE)
        .operationId("prepareBeaconProposer")
        .summary("Provide beacon node with proposals for the given validators.")
        .description(
            """
                Prepares the beacon node for potential proposers by supplying information required when proposing blocks for the given validators. The information supplied for each validator index is considered persistent until overwritten by new information for the given validator index, or until the beacon node restarts.

                Note that because the information is not persistent across beacon node restarts it is recommended that either the beacon node is monitored for restarts or this information is refreshed by resending this request periodically (for example, each epoch).

                Also note that requests containing currently inactive or unknown validator indices will be accepted, as they may become active at a later epoch.
                """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(DeserializableTypeDefinition.listOf(BeaconPreparableProposer.SSZ_DATA))
        .response(SC_OK, "Preparation information has been received.")
        .response(
            HttpStatusCodes.SC_ACCEPTED,
            "Block has been successfully broadcast, but failed validation and has not been imported.")
        .withBadRequestResponse(Optional.of("Invalid parameter supplied."))
        .withChainDataResponses()
        .build();
  }
}

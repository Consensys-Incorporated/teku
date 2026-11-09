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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostRegisterValidator extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/register_validator";
  private final ValidatorDataProvider validatorDataProvider;

  public PostRegisterValidator(final DataProvider provider) {
    this(provider.getValidatorDataProvider());
  }

  public PostRegisterValidator(final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("registerValidator")
            .summary(
                "Provide beacon node with registrations for the given validators to the external builder network.")
            .description(
                "Prepares the beacon node for engaging with external builders."
                    + " The information must be sent by the beacon node to the builder network."
                    + " It is expected that the validator client will send this information periodically"
                    + " to ensure the beacon node has correct and timely registration information"
                    + " to provide to builders. The validator client should not sign blinded beacon"
                    + " blocks that do not adhere to their latest fee recipient and gas limit preferences.")
            .tags(TAG_VALIDATOR)
            .requestBodyType(
                SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition(),
                SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA::sszDeserialize)
            .response(SC_OK, "Registration information has been received.")
            .withUnsupportedMediaTypeResponse()
            .withBadRequestResponse(
                Optional.of(
                    "The request could not be processed, check the response for more information."))
            .withChainDataResponses()
            .build());

    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        validatorDataProvider
            .registerValidators(request.getRequestBody())
            .handle(
                (__, error) -> {
                  if (error != null) {
                    return AsyncApiResponse.respondWithError(
                        SC_INTERNAL_SERVER_ERROR, ExceptionUtil.getRootCauseMessage(error));
                  }
                  return AsyncApiResponse.respondOk(null);
                }));
  }
}

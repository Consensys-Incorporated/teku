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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionData;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;

public class PostSubscribeToBeaconCommitteeSubnet extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/beacon_committee_subscriptions";
  private final ValidatorDataProvider provider;

  public PostSubscribeToBeaconCommitteeSubnet(final DataProvider dataProvider) {
    this(dataProvider.getValidatorDataProvider());
  }

  public PostSubscribeToBeaconCommitteeSubnet(final ValidatorDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("prepareBeaconCommitteeSubnet")
            .summary("Signal beacon node to prepare for a committee subnet")
            .description(
                """
                    After Beacon node receives this request, search using discv5 for peers related to this subnet and replace current peers with those ones if necessary If validator is_aggregator, beacon node must:
                    - announce subnet topic subscription on gossipsub
                    - aggregate attestations received on that subnet
                    """)
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(CommitteeSubscriptionData.SSZ_DATA))
            .response(
                HttpStatusCodes.SC_OK,
                "Slot signature is valid and beacon node has prepared the attestation subnet. Note that, there is no guarantee the node will find peers for the subnet")
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<CommitteeSubscriptionData> requestBody = request.getRequestBody();
    final List<CommitteeSubscriptionRequest> subscriptionRequests =
        requestBody.stream()
            .map(CommitteeSubscriptionData::toCommitteeSubscriptionRequest)
            .toList();
    request.respondAsync(
        provider
            .subscribeToBeaconCommittee(subscriptionRequests)
            .thenApply(AsyncApiResponse::respondOk));
  }
}

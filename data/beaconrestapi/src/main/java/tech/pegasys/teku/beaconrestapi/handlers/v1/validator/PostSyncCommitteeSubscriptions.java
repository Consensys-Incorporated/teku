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

import static tech.pegasys.teku.ethereum.json.types.validator.PostSyncCommitteeData.SYNC_COMMITTEE_SUBSCRIPTION;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.ethereum.json.types.validator.PostSyncCommitteeData;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostSyncCommitteeSubscriptions extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/sync_committee_subscriptions";
  private final ValidatorDataProvider provider;

  public PostSyncCommitteeSubscriptions(final DataProvider dataProvider) {
    this(dataProvider.getValidatorDataProvider());
  }

  public PostSyncCommitteeSubscriptions(final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("prepareSyncCommitteeSubnets")
            .summary("Subscribe to sync committee subnets")
            .description(
                """
                    Subscribe to a number of sync committee subnets

                    Sync committees are not present in phase0, but are required for Altair networks.

                    Subscribing to sync committee subnets is an action performed by VC to enable network participation in Altair networks, and only required if the VC has an active validator in an active sync committee.
                    """)
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(DeserializableTypeDefinition.listOf(SYNC_COMMITTEE_SUBSCRIPTION))
            .response(SC_OK, "Successful response")
            .withChainDataResponses()
            .build());
    this.provider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<PostSyncCommitteeData> requestData = request.getRequestBody();
    final List<SyncCommitteeSubnetSubscription> subscriptions =
        requestData.stream().map(PostSyncCommitteeData::toSyncCommitteeSubnetSubscription).toList();
    request.respondAsync(
        provider
            .subscribeToSyncCommitteeSubnets(subscriptions)
            .thenApply(AsyncApiResponse::respondOk));
  }
}

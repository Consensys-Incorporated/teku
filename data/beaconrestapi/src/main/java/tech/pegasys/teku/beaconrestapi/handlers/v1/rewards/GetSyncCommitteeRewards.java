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

package tech.pegasys.teku.beaconrestapi.handlers.v1.rewards;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.BODY_STRING_LIST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_REWARDS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.LONG_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetSyncCommitteeRewards extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/rewards/sync_committee/{block_id}";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<Map.Entry<Integer, Long>> DATA_TYPE =
      SerializableTypeDefinition.<Map.Entry<Integer, Long>>object()
          .withField(
              "validator_index",
              INTEGER_TYPE.withDescription("The validator index receiving this reward"),
              Map.Entry::getKey)
          .withField(
              "reward",
              LONG_TYPE.withDescription("The sync committee reward in GWEI for the validator"),
              Map.Entry::getValue)
          .build();

  public static final SerializableTypeDefinition<SyncCommitteeRewardData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(SyncCommitteeRewardData.class)
          .name("GetSyncCommitteeRewards")
          .withField(
              EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, SyncCommitteeRewardData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, SyncCommitteeRewardData::isFinalized)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(DATA_TYPE),
              SyncCommitteeRewardData::getRewardData)
          .build();

  public GetSyncCommitteeRewards(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetSyncCommitteeRewards(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getSyncCommitteeRewards")
            .summary("Get sync committee rewards")
            .description(
                "Retrieves rewards info for sync committee members specified by array of public keys "
                    + "or validator index. If no array is provided, return reward info for every committee member.")
            .tags(TAG_BEACON, TAG_REWARDS)
            .pathParam(PARAMETER_BLOCK_ID)
            .optionalRequestBody()
            .requestBodyType(BODY_STRING_LIST)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withInternalErrorResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    // Validator identifier might be the validator's public key or index. If empty we query all
    // validators.
    final Optional<List<String>> maybeList = request.getOptionalRequestBody();
    final List<String> validatorIds = maybeList.orElse(List.of());

    request.respondAsync(
        chainDataProvider
            .getSyncCommitteeRewardsFromBlockId(
                request.getPathParameter(PARAMETER_BLOCK_ID), new HashSet<>(validatorIds))
            .thenApply(
                result ->
                    result
                        .map(AsyncApiResponse::respondOk)
                        .orElse(AsyncApiResponse.respondNotFound())));
  }
}

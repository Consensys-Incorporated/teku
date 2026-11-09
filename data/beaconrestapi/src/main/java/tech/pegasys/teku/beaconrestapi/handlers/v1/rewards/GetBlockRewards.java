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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_REWARDS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.LONG_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetBlockRewards extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/rewards/blocks/{block_id}";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<BlockRewardData> DATA_TYPE =
      SerializableTypeDefinition.object(BlockRewardData.class)
          .withField("proposer_index", UINT64_TYPE, BlockRewardData::proposerIndex)
          .withField("total", LONG_TYPE, BlockRewardData::getTotal)
          .withField("attestations", LONG_TYPE, BlockRewardData::attestations)
          .withField("sync_aggregate", LONG_TYPE, BlockRewardData::syncAggregate)
          .withField("proposer_slashings", LONG_TYPE, BlockRewardData::proposerSlashings)
          .withField("attester_slashings", LONG_TYPE, BlockRewardData::attesterSlashings)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<BlockRewardData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<BlockRewardData>>object()
              .name("GetBlockRewards")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField("data", DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  public GetBlockRewards(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetBlockRewards(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockRewards")
            .summary("Get block rewards")
            .description(
                "Retrieve block reward info for a single block. The rewards value is the sum of values of the proposer rewards from attestations, sync committees and slashings included in the proposal.")
            .tags(TAG_BEACON, TAG_REWARDS)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withInternalErrorResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getBlockRewardsFromBlockId(request.getPathParameter(PARAMETER_BLOCK_ID))
            .thenApply(
                result ->
                    result
                        .map(AsyncApiResponse::respondOk)
                        .orElse(AsyncApiResponse.respondNotFound())));
  }
}

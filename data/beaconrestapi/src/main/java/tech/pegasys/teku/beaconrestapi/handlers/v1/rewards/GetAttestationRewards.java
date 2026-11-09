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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.BODY_STRING_LIST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_REWARDS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.LONG_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.GetAttestationRewardsResponse;
import tech.pegasys.teku.api.migrated.IdealAttestationReward;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetAttestationRewards extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/rewards/attestations/{epoch}";

  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<IdealAttestationReward> IDEAL_REWARDS_TYPE =
      SerializableTypeDefinition.object(IdealAttestationReward.class)
          .withField("effective_balance", UINT64_TYPE, IdealAttestationReward::getEffectiveBalance)
          .withField("head", LONG_TYPE, IdealAttestationReward::getHead)
          .withField("target", LONG_TYPE, IdealAttestationReward::getTarget)
          .withField("source", LONG_TYPE, IdealAttestationReward::getSource)
          .withOptionalField(
              "inclusion_delay", UINT64_TYPE, IdealAttestationReward::getInclusionDelay)
          .withField("inactivity", LONG_TYPE, IdealAttestationReward::getInactivity)
          .build();

  private static final SerializableTypeDefinition<TotalAttestationReward> TOTAL_REWARDS_TYPE =
      SerializableTypeDefinition.object(TotalAttestationReward.class)
          .withField("validator_index", LONG_TYPE, TotalAttestationReward::getValidatorIndex)
          .withField("head", LONG_TYPE, TotalAttestationReward::getHead)
          .withField("target", LONG_TYPE, TotalAttestationReward::getTarget)
          .withField("source", LONG_TYPE, TotalAttestationReward::getSource)
          .withOptionalField(
              "inclusion_delay", UINT64_TYPE, TotalAttestationReward::getInclusionDelay)
          .withField("inactivity", LONG_TYPE, TotalAttestationReward::getInactivity)
          .build();

  private static final SerializableTypeDefinition<AttestationRewardsData> DATA_TYPE =
      SerializableTypeDefinition.object(AttestationRewardsData.class)
          .withField(
              "ideal_rewards",
              SerializableTypeDefinition.listOf(IDEAL_REWARDS_TYPE),
              AttestationRewardsData::getIdealAttestationRewards)
          .withField(
              "total_rewards",
              SerializableTypeDefinition.listOf(TOTAL_REWARDS_TYPE),
              AttestationRewardsData::getTotalAttestationRewards)
          .build();

  public static final SerializableTypeDefinition<GetAttestationRewardsResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GetAttestationRewardsResponse.class)
          .name("GetAttestationRewards")
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              GetAttestationRewardsResponse::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, GetAttestationRewardsResponse::isFinalized)
          .withField("data", DATA_TYPE, GetAttestationRewardsResponse::getAttestationRewardsData)
          .build();

  public GetAttestationRewards(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getAttestationsRewards")
            .summary("Get attestations rewards")
            .description(
                "Retrieve attestation reward info for validators specified by array of public keys or validator index"
                    + ". If no array is provided, return reward info for every validator.")
            .tags(TAG_BEACON, TAG_REWARDS)
            .pathParam(EPOCH_PARAMETER)
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
    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    // Validator identifier might be the validator's public key or index. If empty we query all
    // validators.
    final Optional<List<String>> maybeList = request.getOptionalRequestBody();
    final List<String> validatorIds = maybeList.orElse(List.of());

    request.respondAsync(
        chainDataProvider
            .calculateAttestationRewardsAtEpoch(epoch, validatorIds)
            .thenApply(
                result ->
                    result
                        .map(AsyncApiResponse::respondOk)
                        .orElse(AsyncApiResponse.respondNotFound())));
  }
}

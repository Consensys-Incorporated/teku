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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;

public class GetValidatorInclusion extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/validator_inclusion/{epoch}/{validator_id}";
  private final ChainDataProvider chainDataProvider;

  public static final ParameterMetadata<UInt64> PARAMETER_VALIDATOR_ID =
      new ParameterMetadata<>("validator_id", UINT64_TYPE);

  public GetValidatorInclusion(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetValidatorInclusion(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getValidatorInclusion")
            .summary("Get Validator Inclusion")
            .description(
                "Returns a per-validator summary of how that validator performed during the current epoch.")
            .tags(TAG_TEKU, TAG_EXPERIMENTAL)
            .pathParam(EPOCH_PARAMETER)
            .pathParam(PARAMETER_VALIDATOR_ID)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<GetValidatorInclusionResponseData>object()
                    .name("GetValidatorInclusionResponse")
                    .withField(
                        "data",
                        GetValidatorInclusionResponseData.RESPONSE_DATA,
                        Function.identity())
                    .build())
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withNotFoundResponse()
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final int validator = request.getPathParameter(PARAMETER_VALIDATOR_ID).intValue();
    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    request.respondAsync(
        chainDataProvider
            .getValidatorInclusionAtEpoch(epoch)
            .thenApply(
                maybeValidatorStatuses ->
                    maybeValidatorStatuses
                        .map(
                            validatorStatuses -> {
                              if (validator < 0
                                  || validatorStatuses.getValidatorCount() <= validator) {
                                return AsyncApiResponse.respondWithError(
                                    SC_BAD_REQUEST,
                                    "Expected validator index between 0 and "
                                        + (validatorStatuses.getValidatorCount() - 1));
                              }
                              return AsyncApiResponse.respondOk(
                                  new GetValidatorInclusionResponseData(
                                      validatorStatuses.getStatuses().get(validator)));
                            })
                        .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }

  public static class GetValidatorResponse {
    @JsonProperty("data")
    public final GetValidatorInclusionResponseData data;

    public GetValidatorResponse(final GetValidatorInclusionResponseData data) {
      this.data = data;
    }
  }

  public static class GetValidatorInclusionResponseData {
    public static final SerializableTypeDefinition<GetValidatorInclusionResponseData>
        RESPONSE_DATA =
            SerializableTypeDefinition.object(GetValidatorInclusionResponseData.class)
                .withField("is_slashed", BOOLEAN_TYPE, GetValidatorInclusionResponseData::isSlashed)
                .withField(
                    "is_withdrawable_in_current_epoch",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isWithdrawableInCurrentEpoch)
                .withField(
                    "is_active_unslashed_in_current_epoch",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isActiveUnslashedInCurrentEpoch)
                .withField(
                    "is_active_unslashed_in_previous_epoch",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isActiveUnslashedInPreviousEpoch)
                .withField(
                    "current_epoch_effective_balance_gwei",
                    UINT64_TYPE,
                    GetValidatorInclusionResponseData::getCurrentEpochEffectiveBalanceGwei)
                .withField(
                    "is_current_epoch_target_attester",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isCurrentEpochTargetAttester)
                .withField(
                    "is_previous_epoch_target_attester",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isPreviousEpochTargetAttester)
                .withField(
                    "is_previous_epoch_head_attester",
                    BOOLEAN_TYPE,
                    GetValidatorInclusionResponseData::isPreviousEpochHeadAttester)
                .build();
    private final boolean isSlashed;
    private final boolean isWithdrawableInCurrentEpoch;
    private final boolean isActiveUnslashedInCurrentEpoch;
    private final boolean isActiveUnslashedInPreviousEpoch;
    private final UInt64 currentEpochEffectiveBalanceGwei;
    private final boolean isCurrentEpochTargetAttester;
    private final boolean isPreviousEpochTargetAttester;
    private final boolean isPreviousEpochHeadAttester;

    public GetValidatorInclusionResponseData(final ValidatorStatus validatorStatus) {
      this.isSlashed = validatorStatus.isSlashed();
      this.isWithdrawableInCurrentEpoch = validatorStatus.isWithdrawableInCurrentEpoch();
      this.isActiveUnslashedInCurrentEpoch = validatorStatus.isActiveInCurrentEpoch();
      this.isActiveUnslashedInPreviousEpoch = validatorStatus.isActiveInPreviousEpoch();
      this.currentEpochEffectiveBalanceGwei = validatorStatus.getCurrentEpochEffectiveBalance();
      this.isCurrentEpochTargetAttester = validatorStatus.isCurrentEpochTargetAttester();
      this.isPreviousEpochTargetAttester = validatorStatus.isPreviousEpochTargetAttester();
      this.isPreviousEpochHeadAttester = validatorStatus.isPreviousEpochHeadAttester();
    }

    @JsonProperty("is_slashed")
    public boolean isSlashed() {
      return isSlashed;
    }

    @JsonProperty("is_withdrawable_in_current_epoch")
    public boolean isWithdrawableInCurrentEpoch() {
      return isWithdrawableInCurrentEpoch;
    }

    @JsonProperty("is_active_unslashed_in_current_epoch")
    public boolean isActiveUnslashedInCurrentEpoch() {
      return isActiveUnslashedInCurrentEpoch;
    }

    @JsonProperty("is_active_unslashed_in_previous_epoch")
    public boolean isActiveUnslashedInPreviousEpoch() {
      return isActiveUnslashedInPreviousEpoch;
    }

    @JsonProperty("current_epoch_effective_balance_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getCurrentEpochEffectiveBalanceGwei() {
      return currentEpochEffectiveBalanceGwei;
    }

    @JsonProperty("is_current_epoch_target_attester")
    public boolean isCurrentEpochTargetAttester() {
      return isCurrentEpochTargetAttester;
    }

    @JsonProperty("is_previous_epoch_target_attester")
    public boolean isPreviousEpochTargetAttester() {
      return isPreviousEpochTargetAttester;
    }

    @JsonProperty("is_previous_epoch_head_attester")
    public boolean isPreviousEpochHeadAttester() {
      return isPreviousEpochHeadAttester;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetValidatorInclusionResponseData that = (GetValidatorInclusionResponseData) o;
      return isSlashed == that.isSlashed
          && isWithdrawableInCurrentEpoch == that.isWithdrawableInCurrentEpoch
          && isActiveUnslashedInCurrentEpoch == that.isActiveUnslashedInCurrentEpoch
          && isActiveUnslashedInPreviousEpoch == that.isActiveUnslashedInPreviousEpoch
          && isCurrentEpochTargetAttester == that.isCurrentEpochTargetAttester
          && isPreviousEpochTargetAttester == that.isPreviousEpochTargetAttester
          && isPreviousEpochHeadAttester == that.isPreviousEpochHeadAttester
          && Objects.equals(
              currentEpochEffectiveBalanceGwei, that.currentEpochEffectiveBalanceGwei);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          isSlashed,
          isWithdrawableInCurrentEpoch,
          isActiveUnslashedInCurrentEpoch,
          isActiveUnslashedInPreviousEpoch,
          currentEpochEffectiveBalanceGwei,
          isCurrentEpochTargetAttester,
          isPreviousEpochTargetAttester,
          isPreviousEpochHeadAttester);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("isSlashed", isSlashed)
          .add("isWithdrawableInCurrentEpoch", isWithdrawableInCurrentEpoch)
          .add("isActiveUnslashedInCurrentEpoch", isActiveUnslashedInCurrentEpoch)
          .add("isActiveUnslashedInPreviousEpoch", isActiveUnslashedInPreviousEpoch)
          .add("currentEpochEffectiveBalanceGwei", currentEpochEffectiveBalanceGwei)
          .add("isCurrentEpochTargetAttester", isCurrentEpochTargetAttester)
          .add("isPreviousEpochTargetAttester", isPreviousEpochTargetAttester)
          .add("isPreviousEpochHeadAttester", isPreviousEpochHeadAttester)
          .toString();
    }
  }
}

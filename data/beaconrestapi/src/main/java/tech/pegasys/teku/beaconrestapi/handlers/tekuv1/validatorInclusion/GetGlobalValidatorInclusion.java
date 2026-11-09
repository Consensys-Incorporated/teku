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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
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
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;

public class GetGlobalValidatorInclusion extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/validator_inclusion/{epoch}/global";

  private final ChainDataProvider chainDataProvider;

  public GetGlobalValidatorInclusion(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetGlobalValidatorInclusion(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGlobalValidatorInclusion")
            .summary("Get Global Validator Inclusion")
            .description("Returns a global count of votes for a given epoch.")
            .tags(TAG_TEKU, TAG_EXPERIMENTAL)
            .pathParam(EPOCH_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<GetGlobalValidatorResponseData>object()
                    .name("GetGlobalValidatorInclusionResponse")
                    .withField(
                        "data", GetGlobalValidatorResponseData.RESPONSE_DATA, Function.identity())
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
    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    request.respondAsync(
        chainDataProvider
            .getValidatorInclusionAtEpoch(epoch)
            .thenApply(
                maybeValidatorStatuses ->
                    maybeValidatorStatuses
                        .map(
                            validatorStatuses ->
                                AsyncApiResponse.respondOk(
                                    new GetGlobalValidatorResponseData(
                                        validatorStatuses.getTotalBalances())))
                        .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }

  public static class GetGlobalValidatorResponse {
    @JsonProperty("data")
    public final GetGlobalValidatorResponseData data;

    public GetGlobalValidatorResponse(final GetGlobalValidatorResponseData data) {
      this.data = data;
    }
  }

  public static class GetGlobalValidatorResponseData {
    public static final SerializableTypeDefinition<GetGlobalValidatorResponseData> RESPONSE_DATA =
        SerializableTypeDefinition.object(GetGlobalValidatorResponseData.class)
            .withField(
                "current_epoch_active_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getCurrentEpochActiveGwei)
            .withField(
                "previous_epoch_active_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochActiveGwei)
            .withField(
                "current_epoch_target_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getCurrentEpochTargetAttestingGwei)
            .withField(
                "previous_epoch_target_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochTargetAttestingGwei)
            .withField(
                "previous_epoch_head_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochHeadAttestingGwei)
            .build();
    private final UInt64 currentEpochActiveGwei;
    private final UInt64 previousEpochActiveGwei;
    private final UInt64 currentEpochTargetAttestingGwei;
    private final UInt64 previousEpochTargetAttestingGwei;
    private final UInt64 previousEpochHeadAttestingGwei;

    public GetGlobalValidatorResponseData(final TotalBalances totalBalances) {
      this.currentEpochActiveGwei = totalBalances.getCurrentEpochActiveValidators();
      this.previousEpochActiveGwei = totalBalances.getPreviousEpochActiveValidators();
      this.currentEpochTargetAttestingGwei = totalBalances.getCurrentEpochTargetAttesters();
      this.previousEpochTargetAttestingGwei = totalBalances.getPreviousEpochTargetAttesters();
      this.previousEpochHeadAttestingGwei = totalBalances.getPreviousEpochHeadAttesters();
    }

    @JsonProperty("current_epoch_active_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getCurrentEpochActiveGwei() {
      return currentEpochActiveGwei;
    }

    @JsonProperty("previous_epoch_active_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochActiveGwei() {
      return previousEpochActiveGwei;
    }

    @JsonProperty("current_epoch_target_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getCurrentEpochTargetAttestingGwei() {
      return currentEpochTargetAttestingGwei;
    }

    @JsonProperty("previous_epoch_target_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochTargetAttestingGwei() {
      return previousEpochTargetAttestingGwei;
    }

    @JsonProperty("previous_epoch_head_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochHeadAttestingGwei() {
      return previousEpochHeadAttestingGwei;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetGlobalValidatorResponseData that = (GetGlobalValidatorResponseData) o;
      return Objects.equals(currentEpochActiveGwei, that.currentEpochActiveGwei)
          && Objects.equals(previousEpochActiveGwei, that.previousEpochActiveGwei)
          && Objects.equals(currentEpochTargetAttestingGwei, that.currentEpochTargetAttestingGwei)
          && Objects.equals(previousEpochTargetAttestingGwei, that.previousEpochTargetAttestingGwei)
          && Objects.equals(previousEpochHeadAttestingGwei, that.previousEpochHeadAttestingGwei);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          currentEpochActiveGwei,
          previousEpochActiveGwei,
          currentEpochTargetAttestingGwei,
          previousEpochTargetAttestingGwei,
          previousEpochHeadAttestingGwei);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("currentEpochActiveGwei", currentEpochActiveGwei)
          .add("previousEpochActiveGwei", previousEpochActiveGwei)
          .add("currentEpochTargetAttestingGwei", currentEpochTargetAttestingGwei)
          .add("previousEpochTargetAttestingGwei", previousEpochTargetAttestingGwei)
          .add("previousEpochHeadAttestingGwei", previousEpochHeadAttestingGwei)
          .toString();
    }
  }
}

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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateSyncCommittees extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/sync_committees";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<StateSyncCommitteesData> DATA_TYPE =
      SerializableTypeDefinition.object(StateSyncCommitteesData.class)
          .withField("validators", listOf(UINT64_TYPE), StateSyncCommitteesData::getValidators)
          .withField(
              "validator_aggregates",
              listOf(listOf(UINT64_TYPE)),
              StateSyncCommitteesData::getValidatorAggregates)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<StateSyncCommitteesData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<StateSyncCommitteesData>>object()
              .name("GetEpochSyncCommitteesResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField("data", DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  public GetStateSyncCommittees(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetStateSyncCommittees(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEpochSyncCommittees")
            .summary("Get sync committees for a state.")
            .description("Retrieves the sync committees for the given state.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .queryParam(EPOCH_PARAMETER)
            .response(HttpStatusCodes.SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<UInt64> epoch = request.getOptionalQueryParameter(EPOCH_PARAMETER);
    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        chainDataProvider.getStateSyncCommittees(
            request.getPathParameter(PARAMETER_STATE_ID), epoch);

    request.respondAsync(
        future
            .thenApply(
                maybeStateSyncCommitteesAndMetaData ->
                    maybeStateSyncCommitteesAndMetaData
                        .map(AsyncApiResponse::respondOk)
                        .orElse(AsyncApiResponse.respondNotFound()))
            .exceptionallyCompose(
                error -> {
                  final Throwable rootCause = Throwables.getRootCause(error);

                  if (rootCause instanceof IllegalArgumentException
                      || rootCause instanceof IllegalStateException) {
                    return SafeFuture.completedFuture(
                        AsyncApiResponse.respondWithError(SC_BAD_REQUEST, error.getMessage()));
                  }

                  return failedFuture(error);
                }));
  }
}

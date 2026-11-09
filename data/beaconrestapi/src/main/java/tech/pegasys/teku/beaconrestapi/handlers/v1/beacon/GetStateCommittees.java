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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64Util;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;

public class GetStateCommittees extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/committees";

  private static final SerializableTypeDefinition<CommitteeAssignment> EPOCH_COMMITTEE_TYPE =
      SerializableTypeDefinition.object(CommitteeAssignment.class)
          .withField("index", UINT64_TYPE, CommitteeAssignment::committeeIndex)
          .withField("slot", UINT64_TYPE, CommitteeAssignment::slot)
          .withField(
              "validators",
              listOf(UINT64_TYPE),
              committeeAssignment -> UInt64Util.intToUInt64List(committeeAssignment.committee()))
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<List<CommitteeAssignment>>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<List<CommitteeAssignment>>>object()
              .name("GetEpochCommitteesResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField("data", listOf(EPOCH_COMMITTEE_TYPE), ObjectAndMetaData::getData)
              .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateCommittees(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateCommittees(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEpochCommittees")
            .summary("Get all committees for a state.")
            .description("Retrieves the committees for the given state.")
            .pathParam(PARAMETER_STATE_ID)
            .queryParam(EPOCH_PARAMETER)
            .queryParam(INDEX_PARAMETER)
            .queryParam(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<UInt64> epoch = request.getOptionalQueryParameter(EPOCH_PARAMETER);
    final Optional<UInt64> committeeIndex = request.getOptionalQueryParameter(INDEX_PARAMETER);
    final Optional<UInt64> slot =
        request.getOptionalQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));

    final SafeFuture<Optional<ObjectAndMetaData<List<CommitteeAssignment>>>> future =
        chainDataProvider.getStateCommittees(
            request.getPathParameter(PARAMETER_STATE_ID), epoch, committeeIndex, slot);

    request.respondAsync(
        future.thenApply(
            maybeListObjectAndMetaData ->
                maybeListObjectAndMetaData
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}

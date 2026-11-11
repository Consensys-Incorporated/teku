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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StatePtcData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStatePtc extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/ptc";

  private final ChainDataProvider chainDataProvider;

  public GetStatePtc(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStatePtc(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStatePtc")
            .summary("Get the payload timeliness committee for a slot.")
            .description(
                "Returns the payload timeliness committee for the requested slot from the state with the given 'stateId', in committee order. Should return 400 if the state retrieved or the requested slot is prior to Gloas, or the slot is outside the PTC window of the state.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .queryParam(SLOT_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<ObjectAndMetaData<StatePtcData>>object()
                    .name("GetStatePtcResponse")
                    .withField(
                        EXECUTION_OPTIMISTIC,
                        BOOLEAN_TYPE,
                        ObjectAndMetaData::isExecutionOptimistic)
                    .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
                    .withField(
                        "data",
                        SerializableTypeDefinition.object(StatePtcData.class)
                            .withField("slot", UINT64_TYPE, StatePtcData::slot)
                            .withField("validators", listOf(UINT64_TYPE), StatePtcData::validators)
                            .build(),
                        ObjectAndMetaData::getData)
                    .build())
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getStatePtc(
                request.getPathParameter(PARAMETER_STATE_ID),
                request.getOptionalQueryParameter(SLOT_PARAMETER))
            .thenApply(
                maybeData ->
                    maybeData
                        .map(AsyncApiResponse::respondOk)
                        .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

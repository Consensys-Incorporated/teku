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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ROOT_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;

public class GetStateRoot extends AbstractGetSimpleDataFromState {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/root";

  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .name("GetStateRootResponse")
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
          .withField(
              "data", ROOT_TYPE, stateAndMetaData -> stateAndMetaData.getData().hashTreeRoot())
          .build();

  public GetStateRoot(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateRoot(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateRoot")
            .summary("Get state SSZ HashTreeRoot")
            .description(
                "Calculates HashTreeRoot for state with given 'state_id'. If stateId is root, same value will be returned.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build(),
        chainDataProvider);
  }
}

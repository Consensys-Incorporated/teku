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
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateFinalityCheckpoints extends AbstractGetSimpleDataFromState {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/finality_checkpoints";

  private static final SerializableTypeDefinition<BeaconState> DATA_TYPE =
      SerializableTypeDefinition.object(BeaconState.class)
          .withField(
              "previous_justified",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getPreviousJustifiedCheckpoint)
          .withField(
              "current_justified",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getCurrentJustifiedCheckpoint)
          .withField(
              "finalized",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getFinalizedCheckpoint)
          .build();

  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
          .withField("data", DATA_TYPE, StateAndMetaData::getData)
          .build();

  public GetStateFinalityCheckpoints(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateFinalityCheckpoints(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateFinalityCheckpoints")
            .summary("Get state finality checkpoints")
            .description(
                "Returns finality checkpoints for state with given 'state_id'. In case finality is not yet achieved, checkpoint should return epoch 0 and ZERO_HASH as root.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build(),
        chainDataProvider);
  }
}

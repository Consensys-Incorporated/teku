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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class GetStateFork extends AbstractGetSimpleDataFromState {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/fork";

  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .name("GetStateForkResponse")
          .description(
              "Returns [Fork](https://github.com/ethereum/consensus-specs/blob/v0.11.1/specs/phase0/beacon-chain.md#fork) object for state with given 'stateId'.")
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
          .withField(
              "data",
              Fork.SSZ_SCHEMA.getJsonTypeDefinition(),
              stateAndMetaData -> stateAndMetaData.getData().getFork())
          .build();

  public GetStateFork(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateFork(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateFork")
            .summary("Get Fork object for requested state")
            .description("Returns Fork object for state with given 'state_id'.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .pathParam(PARAMETER_STATE_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build(),
        chainDataProvider);
  }
}

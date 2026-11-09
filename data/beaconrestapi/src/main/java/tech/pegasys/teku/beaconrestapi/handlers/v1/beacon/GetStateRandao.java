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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateRandao extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/randao";
  private final ChainDataProvider chainDataProvider;

  public GetStateRandao(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateRandao(final ChainDataProvider chainDataProvider) {
    super(createMetadata());
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createMetadata() {

    final SerializableTypeDefinition<Bytes32> randaoType =
        SerializableTypeDefinition.object(Bytes32.class)
            .name("Randao")
            .withField("randao", BYTES32_TYPE, Function.identity())
            .build();

    final SerializableTypeDefinition<ObjectAndMetaData<Bytes32>> responseType =
        SerializableTypeDefinition.<ObjectAndMetaData<Bytes32>>object()
            .name("GetStateRandaoResponse")
            .description("RANDAO mix for state with given 'stateId'.")
            .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
            .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
            .withField("data", randaoType, ObjectAndMetaData::getData)
            .build();
    return EndpointMetadata.get(ROUTE)
        .operationId("getStateRandao")
        .summary("Get the RANDAO mix for some epoch in a specified state.")
        .description(
            "Fetch the RANDAO mix for the requested epoch from the state identified by `state_id`.\n\n"
                + "If an epoch is not specified then the RANDAO mix for the state's current epoch will be returned.\n\n"
                + "By adjusting the `state_id` parameter you can query for any historic value of the RANDAO mix. "
                + "Ordinarily states from the same epoch will mutate the RANDAO mix for that epoch as blocks are applied.")
        .tags(TAG_BEACON)
        .pathParam(PARAMETER_STATE_ID)
        .queryParam(EPOCH_PARAMETER)
        .withNotFoundResponse()
        .response(SC_OK, "Request successful", responseType)
        .withChainDataResponses()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<UInt64> epoch = request.getOptionalQueryParameter(EPOCH_PARAMETER);
    final SafeFuture<Optional<ObjectAndMetaData<Optional<Bytes32>>>> future =
        chainDataProvider.getRandaoAtEpoch(request.getPathParameter(PARAMETER_STATE_ID), epoch);

    request.respondAsync(
        future.thenApply(
            maybeObjectAndMetaData ->
                maybeObjectAndMetaData
                    .map(this::generateRandaoResponse)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private AsyncApiResponse generateRandaoResponse(
      final ObjectAndMetaData<Optional<Bytes32>> maybeObjectAndMetadata) {
    final Optional<Bytes32> maybeRandao = maybeObjectAndMetadata.getData();

    if (maybeRandao.isEmpty()) {
      return AsyncApiResponse.respondWithError(
          SC_BAD_REQUEST, "Epoch is out of range for the `randao_mixes` of the state");
    } else {
      return AsyncApiResponse.respondOk(maybeObjectAndMetadata.map(Optional::orElseThrow));
    }
  }
}

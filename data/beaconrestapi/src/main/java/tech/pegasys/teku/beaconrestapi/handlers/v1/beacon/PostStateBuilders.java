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
import static tech.pegasys.teku.ethereum.json.types.beacon.StateBuilderRequestBodyType.STATE_BUILDER_REQUEST_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.BuilderStatus;
import tech.pegasys.teku.api.migrated.StateBuilderData;
import tech.pegasys.teku.ethereum.json.types.beacon.StateBuilderRequestBodyType;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class PostStateBuilders extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/builders";
  private final ChainDataProvider chainDataProvider;

  static final SerializableTypeDefinition<StateBuilderData> STATE_BUILDER_DATA_TYPE =
      SerializableTypeDefinition.<StateBuilderData>object()
          .name("BuilderResponse")
          .withField("index", UINT64_TYPE, StateBuilderData::index)
          .withField("status", STRING_TYPE, StateBuilderData::getStatusName)
          .withField(
              "builder", Builder.SSZ_SCHEMA.getJsonTypeDefinition(), StateBuilderData::builder)
          .build();

  static final SerializableTypeDefinition<ObjectAndMetaData<List<StateBuilderData>>> RESPONSE_TYPE =
      SerializableTypeDefinition.<ObjectAndMetaData<List<StateBuilderData>>>object()
          .name("GetStateBuildersResponse")
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(STATE_BUILDER_DATA_TYPE),
              ObjectAndMetaData::getData)
          .build();

  public PostStateBuilders(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  PostStateBuilders(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getStateBuilders")
            .summary("Get builders from state")
            .description("Returns filterable list of builders with their status and index.")
            .pathParam(PARAMETER_STATE_ID)
            .optionalRequestBody()
            .requestBodyType(STATE_BUILDER_REQUEST_TYPE)
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<StateBuilderRequestBodyType> requestBody;

    try {
      requestBody = request.getOptionalRequestBody();
    } catch (RuntimeException e) {
      final Throwable throwable = Throwables.getRootCause(e);
      if (throwable instanceof JsonParseException) {
        request.respondError(SC_BAD_REQUEST, throwable.getMessage());
      } else {
        throw e;
      }
      return;
    }

    final List<String> builderIds =
        requestBody.map(StateBuilderRequestBodyType::getIds).orElse(List.of());
    final List<BuilderStatus> builderStatuses =
        requestBody.map(StateBuilderRequestBodyType::getStatuses).orElse(List.of());

    final SafeFuture<Optional<ObjectAndMetaData<List<StateBuilderData>>>> future =
        chainDataProvider.getStateBuilders(
            request.getPathParameter(PARAMETER_STATE_ID), builderIds, builderStatuses);

    request.respondAsync(
        future.thenApply(
            maybeData ->
                maybeData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

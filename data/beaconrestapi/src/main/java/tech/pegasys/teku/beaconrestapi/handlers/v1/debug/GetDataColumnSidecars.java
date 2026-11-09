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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.DATA_COLUMN_INDICES_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetDataColumnSidecars extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/debug/beacon/data_column_sidecars/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetDataColumnSidecars(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), schemaCache);
  }

  public GetDataColumnSidecars(
      final ChainDataProvider chainDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getDebugDataColumnSidecars")
        .summary("Get data column sidecars")
        .description(
            """
                        Retrieves data column sidecars for a given block id.
                            Depending on `Accept` header it can be returned either as json or as bytes serialized by SSZ.
                            If the `indices` parameter is specified, only the data column sidecars with the specified indices will be returned. There are no guarantees
                            for the returned data column sidecars in terms of ordering.""")
        .tags(TAG_DEBUG)
        .pathParam(PARAMETER_BLOCK_ID)
        .queryListParam(DATA_COLUMN_INDICES_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaCache),
            getSszResponseType(),
            ETH_CONSENSUS_HEADER_TYPE)
        .withNotFoundResponse()
        .withInternalErrorResponse()
        .withNotAcceptableResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<UInt64> indices = request.getQueryParameterList(DATA_COLUMN_INDICES_PARAMETER);
    final SafeFuture<Optional<DataColumnSidecarsAndMetaData>> future =
        chainDataProvider.getDataColumnSidecars(
            request.getPathParameter(PARAMETER_BLOCK_ID), indices);

    request.respondAsync(
        future.thenApply(
            maybeDataColumnSidecarsAndMetaData ->
                maybeDataColumnSidecarsAndMetaData
                    .map(
                        dataColumnSidecarsAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              dataColumnSidecarsAndMetaData.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(dataColumnSidecarsAndMetaData);
                        })
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<DataColumnSidecarsAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<DataColumnSidecar>>
        schemaGetters = generateDataColumnSidecarSchemaGetters(schemaDefinitionCache);

    final SerializableTypeDefinition<DataColumnSidecar> dataColumnSidecarType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache, "DataColumnSidecar", schemaGetters);
    return SerializableTypeDefinition.<DataColumnSidecarsAndMetaData>object()
        .name("GetDataColumnSidecarsResponse")
        .withField("version", MILESTONE_TYPE, DataColumnSidecarsAndMetaData::getMilestone)
        .withField(
            EXECUTION_OPTIMISTIC,
            BOOLEAN_TYPE,
            DataColumnSidecarsAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, DataColumnSidecarsAndMetaData::isFinalized)
        .withField("data", listOf(dataColumnSidecarType), DataColumnSidecarsAndMetaData::getData)
        .build();
  }

  private static ResponseContentTypeDefinition<List<DataColumnSidecar>> getSszResponseType() {
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<List<DataColumnSidecar>>
        serializer =
            (data, out) -> data.forEach(dataColumnSidecar -> dataColumnSidecar.sszSerialize(out));

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<DataColumnSidecar>>
      generateDataColumnSidecarSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<DataColumnSidecar>>
        schemaGetterList = new ArrayList<>();
    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (dataColumnSidecar, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(dataColumnSidecar.getSlot())
                    .equals(milestone),
            SpecMilestone.FULU,
            schemaDefinitions ->
                SchemaDefinitionsFulu.required(schemaDefinitions).getDataColumnSidecarSchema()));

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (dataColumnSidecar, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(dataColumnSidecar.getSlot())
                    .equals(milestone),
            SpecMilestone.GLOAS,
            schemaDefinitions ->
                SchemaDefinitionsGloas.required(schemaDefinitions).getDataColumnSidecarSchema()));
    return schemaGetterList;
  }
}

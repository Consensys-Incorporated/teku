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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOB_INDICES_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.metadata.BlobSidecarsAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobSidecars extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blob_sidecars/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetBlobSidecars(final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), schemaCache);
  }

  public GetBlobSidecars(
      final ChainDataProvider chainDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getBlobSidecars")
        .summary("Get blob sidecars")
        .description(
            """
                Retrieves blob sidecars for a given block id.
                    Depending on `Accept` header it can be returned either as json or as bytes serialized by SSZ.
                    If the `indices` parameter is specified, only the blob sidecars with the specified indices will be returned. There are no guarantees
                    for the returned blob sidecars in terms of ordering.
                """)
        .deprecated(true)
        .tags(TAG_BEACON)
        .pathParam(PARAMETER_BLOCK_ID)
        .queryListParam(BLOB_INDICES_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaCache),
            getSszResponseType(),
            ETH_CONSENSUS_HEADER_TYPE)
        .withNotFoundResponse()
        .withChainDataResponses()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<UInt64> indices = request.getQueryParameterList(BLOB_INDICES_PARAMETER);
    final SafeFuture<Optional<BlobSidecarsAndMetaData>> future =
        chainDataProvider.getBlobSidecars(request.getPathParameter(PARAMETER_BLOCK_ID), indices);
    request.respondAsync(
        future.thenApply(
            maybeBlobSidecars ->
                maybeBlobSidecars
                    .map(
                        blobSidecarsAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              blobSidecarsAndMetaData.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(blobSidecarsAndMetaData);
                        })
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<BlobSidecarsAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaCache) {
    final DeserializableTypeDefinition<BlobSidecar> blobSidecarType =
        SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
            .getBlobSidecarSchema()
            .getJsonTypeDefinition();
    return SerializableTypeDefinition.<BlobSidecarsAndMetaData>object()
        .name("GetBlobSidecarsResponse")
        .withField("version", MILESTONE_TYPE, BlobSidecarsAndMetaData::getMilestone)
        .withField(
            EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, BlobSidecarsAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, BlobSidecarsAndMetaData::isFinalized)
        .withField("data", listOf(blobSidecarType), BlobSidecarsAndMetaData::getData)
        .build();
  }

  private static ResponseContentTypeDefinition<BlobSidecarsAndMetaData> getSszResponseType() {
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<BlobSidecarsAndMetaData>
        serializer =
            (data, out) -> data.getData().forEach(blobSidecar -> blobSidecar.sszSerialize(out));

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}

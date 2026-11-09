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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.VERSIONED_HASHES_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.metadata.BlobsAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobs extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blobs/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetBlobs(final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), schemaCache);
  }

  public GetBlobs(
      final ChainDataProvider chainDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getBlobs")
        .summary("Get blobs")
        .description(
            """
    Retrieves blobs for a given block id.
    Depending on `Accept` header it can be returned either as json or as bytes serialized by SSZ.
    If the `versioned_hashes` parameter is specified, only the blobs for the specified versioned hashes will be returned. Blobs are
    returned as an ordered list matching the order of their corresponding KZG commitments in the block.
    After the Fulu fork, only supernodes (which custody all data columns) are required to return blobs. Clients may implement
    blob reconstruction logic for non-super nodes.
                """)
        .tags(TAG_BEACON)
        .pathParam(PARAMETER_BLOCK_ID)
        .queryListParam(VERSIONED_HASHES_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaCache),
            getSszResponseType(schemaCache))
        .withNotFoundResponse()
        .withChainDataResponses()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<Bytes32> versionedHashes = request.getQueryParameterList(VERSIONED_HASHES_PARAMETER);
    final SafeFuture<Optional<BlobsAndMetaData>> future =
        chainDataProvider.getBlobs(request.getPathParameter(PARAMETER_BLOCK_ID), versionedHashes);
    request.respondAsync(
        future.thenApply(
            maybeBlobsAndMetaData ->
                maybeBlobsAndMetaData
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<BlobsAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaCache) {
    final DeserializableTypeDefinition<Blob> blobType =
        SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
            .getBlobSchema()
            .getJsonTypeDefinition();
    return SerializableTypeDefinition.<BlobsAndMetaData>object()
        .name("GetBlobsResponse")
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, BlobsAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, BlobsAndMetaData::isFinalized)
        .withField("data", listOf(blobType), BlobsAndMetaData::getData)
        .build();
  }

  private static ResponseContentTypeDefinition<BlobsAndMetaData> getSszResponseType(
      final SchemaDefinitionCache schemaCache) {
    final SszListSchema<Blob, ? extends SszList<Blob>> blobsSchema =
        SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
            .getBlobsInBlockSchema();
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<BlobsAndMetaData>
        serializer =
            (data, out) -> blobsSchema.createFromElements(data.getData()).sszSerialize(out);

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}

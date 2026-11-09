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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOB_INDICES_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetAllBlobSidecarsAtSlot extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/beacon/blob_sidecars/{slot}";
  private final ChainDataProvider chainDataProvider;

  public GetAllBlobSidecarsAtSlot(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetAllBlobSidecarsAtSlot(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createEndpointMetadata(schemaDefinitionCache));
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getAllBlobSidecarsAtSlot")
        .summary("Get blob sidecars at slot")
        .description("Get all blob sidecars (canonical and non canonical) at slot")
        .tags(TAG_TEKU)
        .pathParam(SLOT_PARAMETER.withDescription("slot of the blob sidecars to retrieve."))
        .queryListParam(BLOB_INDICES_PARAMETER)
        .response(SC_OK, "Request successful", getResponseType(schemaCache), getSszResponseType())
        .withNotFoundResponse()
        .withChainDataResponses()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<UInt64> indices = request.getQueryParameterList(BLOB_INDICES_PARAMETER);
    final SafeFuture<Optional<List<BlobSidecar>>> future =
        chainDataProvider.getAllBlobSidecarsAtSlot(
            request.getPathParameter(SLOT_PARAMETER), indices);

    request.respondAsync(
        future.thenApply(
            blobSidecars ->
                blobSidecars
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<List<BlobSidecar>> getResponseType(
      final SchemaDefinitionCache schemaCache) {
    final DeserializableTypeDefinition<BlobSidecar> blobSidecarType =
        SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
            .getBlobSidecarSchema()
            .getJsonTypeDefinition();
    return SerializableTypeDefinition.<List<BlobSidecar>>object()
        .name("GetAllBlobSidecarsAtSlotResponse")
        .withField("data", listOf(blobSidecarType), Function.identity())
        .build();
  }

  private static ResponseContentTypeDefinition<List<BlobSidecar>> getSszResponseType() {
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<List<BlobSidecar>>
        serializer = (data, out) -> data.forEach(blobSidecar -> blobSidecar.sszSerialize(out));

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}

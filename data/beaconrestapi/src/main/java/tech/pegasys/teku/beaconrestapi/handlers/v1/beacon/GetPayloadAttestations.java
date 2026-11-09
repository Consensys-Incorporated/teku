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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetPayloadAttestations extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/payload_attestations";

  private final NodeDataProvider nodeDataProvider;

  public GetPayloadAttestations(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getNodeDataProvider(), schemaDefinitionCache);
  }

  GetPayloadAttestations(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPoolPayloadAttestations")
            .summary("Get PayloadAttestations from operations pool")
            .description(
                "Retrieves payload attestations known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .queryParam(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                ETH_CONSENSUS_HEADER_TYPE)
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<UInt64> slot =
        request.getOptionalQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));
    final ObjectAndMetaData<List<PayloadAttestation>> payloadAttestationsAndMetaData =
        nodeDataProvider.getPayloadAttestationsAndMetaData(slot);
    request.header(
        HEADER_CONSENSUS_VERSION, payloadAttestationsAndMetaData.getMilestone().lowerCaseName());
    request.respondOk(payloadAttestationsAndMetaData);
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<PayloadAttestation>>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    return SerializableTypeDefinition.<ObjectAndMetaData<List<PayloadAttestation>>>object()
        .name("GetPoolPayloadAttestationsResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(
            "data",
            listOf(
                SchemaDefinitionsGloas.required(
                        schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                    .getPayloadAttestationSchema()
                    .getJsonTypeDefinition()),
            ObjectAndMetaData::getData)
        .build();
  }
}

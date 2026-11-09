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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetAttestationsV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/beacon/pool/attestations";

  private final NodeDataProvider nodeDataProvider;

  public GetAttestationsV2(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {

    this(dataProvider.getNodeDataProvider(), schemaDefinitionCache);
  }

  public GetAttestationsV2(
      final NodeDataProvider nodeDataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPoolAttestationsV2")
            .summary("Get Attestations from operations pool")
            .description(
                "Retrieves attestations known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .queryParam(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .queryParam(COMMITTEE_INDEX_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                ETH_CONSENSUS_HEADER_TYPE)
            .build());
    this.nodeDataProvider = nodeDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<UInt64> slot =
        request.getOptionalQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));
    final Optional<UInt64> committeeIndex =
        request.getOptionalQueryParameter(COMMITTEE_INDEX_PARAMETER);
    final ObjectAndMetaData<List<Attestation>> attestationsAndMetaData =
        nodeDataProvider.getAttestationsAndMetaData(slot, committeeIndex);

    request.header(
        HEADER_CONSENSUS_VERSION, attestationsAndMetaData.getMilestone().lowerCaseName());
    request.respondOk(attestationsAndMetaData);
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<Attestation>>> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<Attestation>> schemaGetters =
        generateAttestationSchemaGetters(schemaDefinitionCache);

    final SerializableTypeDefinition<Attestation> attestationType =
        getMultipleSchemaDefinitionFromMilestone(
            schemaDefinitionCache, "Attestation", schemaGetters);

    return SerializableTypeDefinition.<ObjectAndMetaData<List<Attestation>>>object()
        .name("GetPoolAttestationsV2Response")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", listOf(attestationType), ObjectAndMetaData::getData)
        .build();
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<Attestation>>
      generateAttestationSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<Attestation>> schemaGetterList =
        new ArrayList<>();

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (attestation, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(attestation.getData().getSlot())
                    .equals(milestone),
            SpecMilestone.PHASE0,
            SchemaDefinitions::getAttestationSchema));
    return schemaGetterList;
  }
}

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

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.function.Predicate;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetAttesterSlashingsV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public GetAttesterSlashingsV2(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getNodeDataProvider(), schemaDefinitionCache);
  }

  GetAttesterSlashingsV2(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPoolAttesterSlashingsV2")
            .summary("Get AttesterSlashings from operations pool")
            .description(
                "Retrieves attester slashings known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
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
    ObjectAndMetaData<List<AttesterSlashing>> attesterSlashingsWithMetadaData =
        nodeDataProvider.getAttesterSlashingsAndMetaData();
    request.header(HEADER_CONSENSUS_VERSION, attesterSlashingsWithMetadaData.getMilestone().name());
    request.respondOk(attesterSlashingsWithMetadaData);
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<AttesterSlashing>>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {

    final DeserializableTypeDefinition<AttesterSlashing> attesterSlashingPhase0Schema =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.PHASE0)
            .getAttesterSlashingSchema()
            .getJsonTypeDefinition();

    final DeserializableTypeDefinition<AttesterSlashing> attesterSlashingElectraSchema =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.ELECTRA)
            .getAttesterSlashingSchema()
            .getJsonTypeDefinition();

    final SerializableOneOfTypeDefinition<List<AttesterSlashing>> oneOfTypeDefinition =
        new SerializableOneOfTypeDefinitionBuilder<List<AttesterSlashing>>()
            .withType(
                electraAttesterSlashingsPredicate(schemaDefinitionCache),
                listOf(attesterSlashingElectraSchema))
            .withType(
                phase0AttesterSlashingsPredicate(schemaDefinitionCache),
                listOf(attesterSlashingPhase0Schema))
            .build();

    return SerializableTypeDefinition.<ObjectAndMetaData<List<AttesterSlashing>>>object()
        .name("GetPoolAttesterSlashingsV2Response")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", oneOfTypeDefinition, ObjectAndMetaData::getData)
        .build();
  }

  private static Predicate<List<AttesterSlashing>> electraAttesterSlashingsPredicate(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return attesterSlashings ->
        !attesterSlashings.isEmpty()
            && schemaDefinitionCache
                .milestoneAtSlot(attesterSlashings.get(0).getAttestation1().getData().getSlot())
                .isGreaterThanOrEqualTo(SpecMilestone.ELECTRA);
  }

  private static Predicate<List<AttesterSlashing>> phase0AttesterSlashingsPredicate(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return attesterSlashings ->
        attesterSlashings.isEmpty()
            || !schemaDefinitionCache
                .milestoneAtSlot(attesterSlashings.get(0).getAttestation1().getData().getSlot())
                .isGreaterThanOrEqualTo(SpecMilestone.ELECTRA);
  }
}

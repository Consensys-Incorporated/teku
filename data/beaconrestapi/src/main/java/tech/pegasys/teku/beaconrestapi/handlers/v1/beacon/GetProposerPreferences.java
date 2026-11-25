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
import static tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostProposerPreferences.getMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostProposerPreferences.getSchema;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.DEPENDENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetProposerPreferences extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/proposer_preferences";

  private static final ParameterMetadata<UInt64> PROPOSAL_SLOT_PARAMETER =
      SLOT_PARAMETER.withDescription(
          "Only return signed proposer preferences for this proposal slot.");

  private static final ParameterMetadata<Bytes32> DEPENDENT_ROOT_PARAMETER =
      new ParameterMetadata<>(
          DEPENDENT_ROOT,
          BYTES32_TYPE.withDescription(
              "Only return signed proposer preferences with this dependent root."));

  private final NodeDataProvider nodeDataProvider;
  private final SchemaDefinitionCache schemaDefinitionCache;

  public GetProposerPreferences(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getNodeDataProvider(), schemaDefinitionCache);
  }

  GetProposerPreferences(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(schemaDefinitionCache));
    this.nodeDataProvider = provider;
    this.schemaDefinitionCache = schemaDefinitionCache;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<UInt64> slot = request.getOptionalQueryParameter(PROPOSAL_SLOT_PARAMETER);
    if (slot.isPresent()
        && schemaDefinitionCache.milestoneAtSlot(slot.get()).isLessThan(SpecMilestone.GLOAS)) {
      throw new BadRequestException(
          "Cannot request proposer preferences for slots prior to gloas fork.");
    }
    final Optional<Bytes32> dependentRoot =
        request.getOptionalQueryParameter(DEPENDENT_ROOT_PARAMETER);
    final ObjectAndMetaData<List<SignedProposerPreferences>> proposerPreferencesAndMetaData =
        nodeDataProvider.getProposerPreferencesAndMetaData(slot, dependentRoot);
    request.header(
        HEADER_CONSENSUS_VERSION, proposerPreferencesAndMetaData.getMilestone().lowerCaseName());
    request.respondOk(proposerPreferencesAndMetaData);
  }

  private static EndpointMetadata createMetadata(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getProposerPreferences")
        .summary("Get signed proposer preferences")
        .description(
            """
                Retrieves signed proposer preferences known by the node for upcoming proposal slots.

                Signed proposer preferences are only broadcast once per proposal slot and dependent root.
                This allows builders to retrieve the ones that were broadcast before they subscribed to the
                `proposer_preferences` event.""")
        .tags(TAG_BEACON)
        .queryParam(PROPOSAL_SLOT_PARAMETER)
        .queryParam(DEPENDENT_ROOT_PARAMETER)
        .response(
            SC_OK,
            "Successful response",
            getResponseType(schemaDefinitionCache),
            getSszResponseType(schemaDefinitionCache),
            ETH_CONSENSUS_HEADER_TYPE)
        .build();
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<SignedProposerPreferences>>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    // From PQ the preferences are signed with XMSS: the same container with a different signature
    final SerializableOneOfTypeDefinitionBuilder<SignedProposerPreferences> builder =
        new SerializableOneOfTypeDefinitionBuilder<SignedProposerPreferences>()
            .title("SignedProposerPreferences");
    for (final SpecMilestone milestone : getMilestones(schemaDefinitionCache)) {
      final SignedProposerPreferencesSchema schema = getSchema(schemaDefinitionCache, milestone);
      builder.withType(value -> value.getSchema().equals(schema), schema.getJsonTypeDefinition());
    }
    return SerializableTypeDefinition.<ObjectAndMetaData<List<SignedProposerPreferences>>>object()
        .name("GetProposerPreferencesResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", listOf(builder.build()), ObjectAndMetaData::getData)
        .build();
  }

  private static ResponseContentTypeDefinition<ObjectAndMetaData<List<SignedProposerPreferences>>>
      getSszResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<
            ObjectAndMetaData<List<SignedProposerPreferences>>>
        serializer =
            (data, out) -> {
              final SpecMilestone milestone =
                  data.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.PQ)
                      ? SpecMilestone.PQ
                      : SpecMilestone.GLOAS;
              SszProgressiveListSchema.create(getSchema(schemaDefinitionCache, milestone))
                  .createFromElements(data.getData())
                  .sszSerialize(out);
            };
    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}

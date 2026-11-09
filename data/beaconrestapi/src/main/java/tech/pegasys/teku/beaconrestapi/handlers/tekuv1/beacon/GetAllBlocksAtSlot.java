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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.AllBlocksAtSlotData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetAllBlocksAtSlot extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/beacon/blocks/{slot}";
  private final ChainDataProvider chainDataProvider;

  public GetAllBlocksAtSlot(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetAllBlocksAtSlot(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAllBlocksAtSlot")
            .summary("Get blocks at slot")
            .description("Get all blocks (canonical and non-canonical) by slot.")
            .tags(TAG_TEKU)
            .pathParam(SLOT_PARAMETER.withDescription("slot of the blocks to retrieve."))
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);

    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);
    final SafeFuture<List<BlockAndMetaData>> future = chainDataProvider.getAllBlocksAtSlot(slot);

    request.respondAsync(
        future.thenApply(
            blockAndMetaDataList -> {
              if (blockAndMetaDataList.isEmpty()) {
                return AsyncApiResponse.respondWithError(SC_NOT_FOUND, "Blocks not found: " + slot);
              }

              return AsyncApiResponse.respondOk(new AllBlocksAtSlotData(blockAndMetaDataList));
            }));
  }

  public static SerializableTypeDefinition<AllBlocksAtSlotData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinition<BeaconBlock> messageType =
        getSchemaDefinitionForAllSupportedMilestones(
            schemaDefinitionCache,
            "BeaconBlock",
            SchemaDefinitions::getBeaconBlockSchema,
            (beaconBlock, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(beaconBlock.getSlot()).equals(milestone));

    final SerializableTypeDefinition<SignedBeaconBlock> dataType =
        SerializableTypeDefinition.object(SignedBeaconBlock.class)
            .withField("message", messageType, SignedBeaconBlock::getMessage)
            .withField("signature", SIGNATURE_TYPE, SignedBeaconBlock::getSignature)
            .withField("root", BYTES32_TYPE, SignedBeaconBlock::getRoot)
            .build();

    return SerializableTypeDefinition.object(AllBlocksAtSlotData.class)
        .name("GetAllBlocksAtSlotResponse")
        .withField("version", MILESTONE_TYPE, AllBlocksAtSlotData::getVersion)
        .withField("data", listOf(dataType), AllBlocksAtSlotData::getBlocks)
        .build();
  }
}

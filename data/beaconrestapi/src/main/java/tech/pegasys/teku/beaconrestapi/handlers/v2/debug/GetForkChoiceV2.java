/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.debug;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.javalin.http.Header;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ForkChoiceDataV2;
import tech.pegasys.teku.api.ForkChoiceNodeDataV2;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.EnumTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class GetForkChoiceV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/debug/fork_choice";

  private static final SerializableTypeDefinition<Map<String, String>> NODE_EXTRA_DATA_TYPE =
      DeserializableTypeDefinition.mapOfStrings();

  private static final SerializableTypeDefinition<ForkChoicePayloadStatus> PAYLOAD_STATUS_TYPE =
      new EnumTypeDefinition.EnumTypeBuilder<>(
              ForkChoicePayloadStatus.class, GetForkChoiceV2::payloadStatusToString)
          .build();

  private static final SerializableTypeDefinition<List<ForkChoiceNodeDataV2>> NODES_TYPE =
      SerializableTypeDefinition.listOf(
          SerializableTypeDefinition.object(ForkChoiceNodeDataV2.class)
              .name("NodeV2")
              .description("A fork choice node, identified by its block root and payload status.")
              .withField(
                  "slot",
                  UINT64_TYPE.withDescription("The slot of the beacon block."),
                  node -> node.getNode().getSlot())
              .withField(
                  "block_root",
                  BYTES32_TYPE.withDescription("The hash tree root of the beacon block."),
                  node -> node.getNode().getRoot())
              .withField(
                  "payload_status",
                  PAYLOAD_STATUS_TYPE.withDescription(
                      "`pending`: the parent of this block's `empty` and `full` nodes. "
                          + "`empty`: this block without its execution payload. "
                          + "`full`: this block with its execution payload."),
                  ForkChoiceNodeDataV2::getPayloadStatus)
              .withField(
                  "parent_root",
                  BYTES32_TYPE.withDescription(
                      "The block root of the parent fork choice node. For Gloas `empty` and `full` "
                          + "nodes, this equals `block_root`, pointing to the same block's `pending` "
                          + "node. Otherwise, it is the beacon block's `parent_root`."),
                  ForkChoiceNodeDataV2::getParentRoot)
              .withNullableField(
                  "parent_payload_status",
                  PAYLOAD_STATUS_TYPE.withDescription(
                      "The payload status of the parent fork choice node. Null if the parent is "
                          + "not retained in the fork choice tree."),
                  ForkChoiceNodeDataV2::getParentPayloadStatus)
              .withField(
                  "justified_checkpoint",
                  Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
                  node -> node.getNode().getCheckpoints().getJustifiedCheckpoint())
              .withField(
                  "finalized_checkpoint",
                  Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
                  node -> node.getNode().getCheckpoints().getFinalizedCheckpoint())
              .withField(
                  "weight",
                  UINT64_TYPE.withDescription(
                      "The raw stored weight of this fork choice node in Gwei."),
                  node -> node.getNode().getWeight())
              .withField(
                  "validity",
                  DeserializableTypeDefinition.enumOf(ProtoNodeValidationStatus.class, true),
                  node -> node.getNode().getValidationStatus())
              .withField(
                  "execution_block_hash",
                  BYTES32_TYPE.withDescription(
                      "For `full` nodes, the block's execution payload hash. For Gloas `pending` "
                          + "and `empty` nodes, the bid's `parent_block_hash`."),
                  node -> node.getNode().getExecutionBlockHash())
              .withField(
                  "payload_attester_count",
                  UINT64_TYPE.withDescription("Number of PTC positions with a recorded vote."),
                  ForkChoiceNodeDataV2::getPayloadAttesterCount)
              .withField(
                  "payload_availability_yes_count",
                  UINT64_TYPE.withDescription(
                      "Number of PTC positions voting that the payload was received on time."),
                  ForkChoiceNodeDataV2::getPayloadAvailabilityYesCount)
              .withField(
                  "payload_data_availability_yes_count",
                  UINT64_TYPE.withDescription(
                      "Number of PTC positions voting that the blob data is available."),
                  ForkChoiceNodeDataV2::getPayloadDataAvailabilityYesCount)
              .withField("extra_data", NODE_EXTRA_DATA_TYPE, GetForkChoiceV2::getNodeExtraData)
              .build());

  private static final SerializableTypeDefinition<ForkChoiceDataV2> DATA_TYPE =
      SerializableTypeDefinition.object(ForkChoiceDataV2.class)
          .withField(
              "justified_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceDataV2::getJustifiedCheckpoint)
          .withField(
              "finalized_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceDataV2::getFinalizedCheckpoint)
          .withField("fork_choice_nodes", NODES_TYPE, ForkChoiceDataV2::getNodes)
          .withField(
              "extra_data",
              DeserializableTypeDefinition.mapOfStrings(),
              __ -> Collections.emptyMap())
          .build();

  private static final SerializableTypeDefinition<ForkChoiceDataV2> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ForkChoiceDataV2.class)
          .name("GetForkChoiceResponseV2")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  private final ChainDataProvider chainDataProvider;

  public GetForkChoiceV2(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetForkChoiceV2(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDebugForkChoiceV2")
            .summary("Get fork choice array")
            .description(
                "Retrieves all current fork choice context, with one node per `(block_root, "
                    + "payload_status)` pair. Each pre-Gloas block has a single `full` node.\n\n"
                    + "Payload Timeliness Committee (PTC) counts are per committee position, "
                    + "including repeated validator indices, and are the same for all nodes of a "
                    + "block. They are zero for pre-Gloas blocks.")
            .tags(TAG_DEBUG)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(chainDataProvider.getForkChoiceDataV2());
  }

  private static Map<String, String> getNodeExtraData(final ForkChoiceNodeDataV2 nodeData) {
    final ProtoNodeData node = nodeData.getNode();
    final BlockCheckpoints checkpoints = node.getCheckpoints();
    return ImmutableMap.<String, String>builder()
        .put("state_root", node.getStateRoot().toHexString())
        .put(
            "unrealised_justified_epoch",
            checkpoints.getUnrealizedJustifiedCheckpoint().getEpoch().toString())
        .put(
            "unrealized_justified_root",
            checkpoints.getUnrealizedJustifiedCheckpoint().getRoot().toHexString())
        .put(
            "unrealised_finalized_epoch",
            checkpoints.getUnrealizedFinalizedCheckpoint().getEpoch().toString())
        .put(
            "unrealized_finalized_root",
            checkpoints.getUnrealizedFinalizedCheckpoint().getRoot().toHexString())
        .build();
  }

  private static String payloadStatusToString(final ForkChoicePayloadStatus payloadStatus) {
    return switch (payloadStatus) {
      case PAYLOAD_STATUS_EMPTY -> "empty";
      case PAYLOAD_STATUS_FULL -> "full";
      case PAYLOAD_STATUS_PENDING -> "pending";
    };
  }
}

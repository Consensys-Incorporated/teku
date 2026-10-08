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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

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
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ForkChoiceData;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class GetForkChoice extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/debug/fork_choice";

  private static final SerializableTypeDefinition<Map<String, String>> NODE_EXTRA_DATA_TYPE =
      DeserializableTypeDefinition.mapOfStrings();

  private static final SerializableTypeDefinition<List<ProtoNodeData>> NODES_TYPE =
      SerializableTypeDefinition.listOf(
          SerializableTypeDefinition.object(ProtoNodeData.class)
              .withField(
                  "slot",
                  UINT64_TYPE.withDescription("The slot to which this block corresponds."),
                  ProtoNodeData::getSlot)
              .withField(
                  "block_root",
                  BYTES32_TYPE.withDescription("The signing merkle root of the `BeaconBlock`."),
                  ProtoNodeData::getRoot)
              .withField(
                  "parent_root",
                  BYTES32_TYPE.withDescription(
                      "The signing merkle root of the parent `BeaconBlock`."),
                  ProtoNodeData::getParentRoot)
              .withField(
                  "justified_epoch",
                  UINT64_TYPE,
                  node -> node.getCheckpoints().getJustifiedCheckpoint().getEpoch())
              .withField(
                  "finalized_epoch",
                  UINT64_TYPE,
                  node -> node.getCheckpoints().getFinalizedCheckpoint().getEpoch())
              .withField("weight", UINT64_TYPE, ProtoNodeData::getWeight)
              .withField(
                  "validity",
                  DeserializableTypeDefinition.enumOf(ProtoNodeValidationStatus.class, true),
                  ProtoNodeData::getValidationStatus)
              .withField(
                  "execution_block_hash",
                  BYTES32_TYPE.withDescription(
                      "The `block_hash` from the `execution_payload` of the `BeaconBlock`"),
                  ProtoNodeData::getExecutionBlockHash)
              .withField(
                  "extra_data",
                  NODE_EXTRA_DATA_TYPE,
                  node ->
                      ImmutableMap.<String, String>builder()
                          .put("state_root", node.getStateRoot().toHexString())
                          .put(
                              "justified_root",
                              node.getCheckpoints()
                                  .getJustifiedCheckpoint()
                                  .getRoot()
                                  .toHexString())
                          .put(
                              "unrealised_justified_epoch",
                              node.getCheckpoints()
                                  .getUnrealizedJustifiedCheckpoint()
                                  .getEpoch()
                                  .toString())
                          .put(
                              "unrealized_justified_root",
                              node.getCheckpoints()
                                  .getUnrealizedJustifiedCheckpoint()
                                  .getRoot()
                                  .toHexString())
                          .put(
                              "unrealised_finalized_epoch",
                              node.getCheckpoints()
                                  .getUnrealizedFinalizedCheckpoint()
                                  .getEpoch()
                                  .toString())
                          .put(
                              "unrealized_finalized_root",
                              node.getCheckpoints()
                                  .getUnrealizedFinalizedCheckpoint()
                                  .getRoot()
                                  .toHexString())
                          .build())
              .build());

  private static final SerializableTypeDefinition<ForkChoiceData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ForkChoiceData.class)
          .withField(
              "justified_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceData::getJustifiedCheckpoint)
          .withField(
              "finalized_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceData::getFinalizedCheckpoint)
          .withField("fork_choice_nodes", NODES_TYPE, ForkChoiceData::getNodes)
          .withField(
              "extra_data",
              DeserializableTypeDefinition.mapOfStrings(),
              __ -> Collections.emptyMap())
          .build();

  private final ChainDataProvider chainDataProvider;

  public GetForkChoice(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetForkChoice(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDebugForkChoice")
            .summary("Get fork choice array")
            .description(
                "Retrieves all current fork choice context. Use `GET /eth/v2/debug/fork_choice` for "
                    + "Gloas and later forks.")
            .tags(TAG_DEBUG)
            .deprecated(true)
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
    request.respondOk(chainDataProvider.getForkChoiceData());
  }
}

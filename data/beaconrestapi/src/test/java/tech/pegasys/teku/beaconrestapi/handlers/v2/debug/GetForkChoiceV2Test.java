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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.ForkChoiceDataV2;
import tech.pegasys.teku.api.ForkChoiceNodeDataV2;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class GetForkChoiceV2Test extends AbstractMigratedBeaconHandlerTest {

  private final ProtoNodeData pendingNode =
      new ProtoNodeData(
          UInt64.valueOf(32),
          Bytes32.fromHexString("0x3333"),
          Bytes32.fromHexString("0x4444"),
          Bytes32.fromHexString("0x5555"),
          UInt64.valueOf(42),
          Bytes32.fromHexString("0x6666"),
          UInt64.ZERO,
          ProtoNodeValidationStatus.OPTIMISTIC,
          new BlockCheckpoints(
              new Checkpoint(UInt64.valueOf(10), Bytes32.fromHexString("0x7777")),
              new Checkpoint(UInt64.valueOf(11), Bytes32.fromHexString("0x8888")),
              new Checkpoint(UInt64.valueOf(12), Bytes32.fromHexString("0x9999")),
              new Checkpoint(UInt64.valueOf(13), Bytes32.fromHexString("0x0000"))),
          UInt64.valueOf(409600000000L),
          ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);

  private final ForkChoiceDataV2 response =
      new ForkChoiceDataV2(
          new Checkpoint(UInt64.ONE, Bytes32.fromHexString("0x1111")),
          new Checkpoint(UInt64.ZERO, Bytes32.fromHexString("0x2222")),
          List.of(
              // a PENDING node whose parent is the parent block's FULL node
              new ForkChoiceNodeDataV2(
                  ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING,
                  pendingNode,
                  Bytes32.fromHexString("0x4444"),
                  Optional.of(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL),
                  UInt64.valueOf(4),
                  UInt64.valueOf(2),
                  UInt64.valueOf(3)),
              // the same block's EMPTY node, whose parent is its PENDING node
              new ForkChoiceNodeDataV2(
                  ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY,
                  pendingNode,
                  Bytes32.fromHexString("0x3333"),
                  Optional.of(ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING),
                  UInt64.valueOf(4),
                  UInt64.valueOf(2),
                  UInt64.valueOf(3)),
              // a node whose parent is not retained
              new ForkChoiceNodeDataV2(
                  ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL,
                  pendingNode,
                  Bytes32.fromHexString("0x4444"),
                  Optional.empty(),
                  UInt64.ZERO,
                  UInt64.ZERO,
                  UInt64.ZERO)));

  @BeforeEach
  void setup() {
    setHandler(new GetForkChoiceV2(chainDataProvider));
  }

  @Test
  public void shouldReturnProtoArrayInformation() throws JsonProcessingException {
    when(chainDataProvider.getForkChoiceDataV2()).thenReturn(response);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(response);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, response);
    final String expected =
        Resources.toString(
                Resources.getResource(GetForkChoiceV2.class, "getForkChoiceV2.json"), UTF_8)
            .stripTrailing();
    assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @ParameterizedTest
  @EnumSource(ForkChoicePayloadStatus.class)
  void shouldSerializeParentPayloadStatus(final ForkChoicePayloadStatus status) throws IOException {
    final ForkChoiceNodeDataV2 node =
        new ForkChoiceNodeDataV2(
            ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING,
            pendingNode,
            pendingNode.getParentRoot(),
            Optional.of(status),
            UInt64.ZERO,
            UInt64.ZERO,
            UInt64.ZERO);
    final ForkChoiceDataV2 data =
        new ForkChoiceDataV2(
            response.getJustifiedCheckpoint(), response.getFinalizedCheckpoint(), List.of(node));
    final JsonNode json =
        new ObjectMapper().readTree(getResponseStringFromMetadata(handler, SC_OK, data));
    assertThat(
            json.path("data")
                .path("fork_choice_nodes")
                .get(0)
                .path("parent_payload_status")
                .asText())
        .isEqualTo(
            switch (status) {
              case PAYLOAD_STATUS_PENDING -> "pending";
              case PAYLOAD_STATUS_EMPTY -> "empty";
              case PAYLOAD_STATUS_FULL -> "full";
            });
  }
}

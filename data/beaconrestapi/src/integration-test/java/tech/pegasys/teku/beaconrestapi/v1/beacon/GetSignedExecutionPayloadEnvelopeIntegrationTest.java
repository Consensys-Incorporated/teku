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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetSignedExecutionPayloadEnvelope;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetSignedExecutionPayloadEnvelopeIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setUp() {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
  }

  @Test
  public void shouldGetExecutionPayloadEnvelopeAtTheHeadOfTheChain() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(1, 2, 3);

    final Response response = get("head");
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    assertThat(body.get("version").asText()).isEqualTo("gloas");
    assertThat(body.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(body.get("finalized").asBoolean()).isFalse();
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");

    final SignedExecutionPayloadEnvelope executionPayloadEnvelope =
        getSignedExecutionPayloadEnvelope(body);

    assertThat(executionPayloadEnvelope)
        .isEqualTo(
            chainBuilder.getExecutionPayload(created.getLast().getBlock().getRoot()).orElseThrow());
  }

  @Test
  public void shouldGetExecutionPayloadEnvelopeByBlockRoot() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(1, 2, 3);

    final SignedBeaconBlock block = created.get(1).getBlock();

    final Response response = get(block.getRoot().toHexString());
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    final SignedExecutionPayloadEnvelope executionPayloadEnvelope =
        getSignedExecutionPayloadEnvelope(body);

    assertThat(executionPayloadEnvelope)
        .isEqualTo(chainBuilder.getExecutionPayload(block.getRoot()).orElseThrow());
  }

  @Test
  public void shouldGetExecutionPayloadEnvelopeBySlot() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(1, 2, 3);

    final SignedBeaconBlock block = created.get(1).getBlock();

    final Response response = get("2");

    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    final SignedExecutionPayloadEnvelope executionPayloadEnvelope =
        getSignedExecutionPayloadEnvelope(body);

    assertThat(executionPayloadEnvelope)
        .isEqualTo(chainBuilder.getExecutionPayload(block.getRoot()).orElseThrow());
  }

  @Test
  public void shouldGetExecutionPayloadEnvelopeAsSsz() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(1, 2, 3);

    final Response response =
        getResponse(
            GetSignedExecutionPayloadEnvelope.ROUTE.replace("{block_id}", "head"), OCTET_STREAM);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");

    final byte[] responseBody = response.body().bytes();

    assertThat(responseBody)
        .isEqualTo(
            getExpectedSsz(
                chainBuilder
                    .getExecutionPayload(created.getLast().getBlock().getRoot())
                    .orElseThrow()));
  }

  private SignedExecutionPayloadEnvelope getSignedExecutionPayloadEnvelope(final JsonNode body)
      throws JsonProcessingException {
    return JsonUtil.parse(
        body.get("data").toString(),
        SchemaDefinitionsGloas.required(
                spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions())
            .getSignedExecutionPayloadEnvelopeSchema()
            .getJsonTypeDefinition());
  }

  private Response get(final String blockIdString) throws IOException {
    return getResponse(
        GetSignedExecutionPayloadEnvelope.ROUTE.replace("{block_id}", blockIdString));
  }

  private byte[] getExpectedSsz(final SignedExecutionPayloadEnvelope data) throws IOException {
    final ExecutionPayloadAndMetaData value =
        new ExecutionPayloadAndMetaData(data, SpecMilestone.GLOAS, false, false);
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      EthereumTypes.executionPayloadAndMetaDataSszResponseType().serialize(value, outputStream);
      return outputStream.toByteArray();
    }
  }
}

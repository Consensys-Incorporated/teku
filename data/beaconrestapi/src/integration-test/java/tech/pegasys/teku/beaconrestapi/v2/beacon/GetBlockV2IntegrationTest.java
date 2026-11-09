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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONTENT_ENCODING;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetBlockV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetBlock() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    assertThat(body.get("version").asText()).isEqualTo(SpecMilestone.PHASE0.lowerCaseName());
    assertThat(body.get("data").get("message").get("parent_root").asText())
        .isEqualTo(created.get(0).getBlock().getMessage().getParentRoot().toHexString());
    assertThat(body.get("data").get("message").get("state_root").asText())
        .isEqualTo(created.get(0).getBlock().getMessage().getStateRoot().toHexString());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.PHASE0.lowerCaseName());

    // this is the most practical way to compare the block to that created, but its secondary to
    // comparing state root and parent root.
    final SignedBeaconBlock block =
        getSignedBeaconBlock(SpecMilestone.PHASE0, body.get("data").toString());
    assertThat(block).isEqualTo(created.get(0).getBlock());
  }

  @Test
  public void shouldGetAltairBlock() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    assertThat(body.get("version").asText()).isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
    assertThat(body.get("data").get("message").get("parent_root").asText())
        .isEqualTo(created.get(0).getBlock().getMessage().getParentRoot().toHexString());
    assertThat(body.get("data").get("message").get("state_root").asText())
        .isEqualTo(created.get(0).getBlock().getMessage().getStateRoot().toHexString());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());

    // this is the most practical way to compare the block to that created, but its secondary to
    // comparing state root and parent root.
    final SignedBeaconBlock block =
        getSignedBeaconBlock(SpecMilestone.ALTAIR, body.get("data").toString());
    assertThat(block).isEqualTo(created.get(0).getBlock());
  }

  private SignedBeaconBlock getSignedBeaconBlock(final SpecMilestone milestone, final String data)
      throws JsonProcessingException {
    return JsonUtil.parse(
        data,
        spec.forMilestone(milestone)
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .getJsonTypeDefinition());
  }

  @Test
  public void shouldGetAltairBlockAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    createBlocksAtSlots(10);
    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
  }

  @Test
  public void shouldGetAltairBlockAsGzip() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    createBlocksAtSlots(10);
    final Response response = getGzip("head", ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
    assertThat(response.header(HEADER_CONTENT_ENCODING)).isEqualTo("gzip");

    // Decompress response
    final byte[] responseBody = response.body().bytes();
    final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(responseBody));
    final byte[] bytesResult = gis.readAllBytes();
    final String block = new String(bytesResult, StandardCharsets.UTF_8);

    // Check block signatures are equivalent to ensure same block
    final JsonNode node = OBJECT_MAPPER.readTree(block);
    final String blockSignature = node.get("data").get("signature").asText();
    final String expectedSignature =
        chainBuilder.getLatestBlockAndState().getBlock().getSignature().toString();
    assertThat(blockSignature).isEqualTo(expectedSignature);
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response getGzip(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString), contentType, "gzip");
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString));
  }
}

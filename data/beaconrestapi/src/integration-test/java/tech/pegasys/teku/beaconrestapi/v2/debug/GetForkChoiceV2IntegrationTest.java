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

package tech.pegasys.teku.beaconrestapi.v2.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetForkChoiceV2;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetForkChoiceV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  void shouldExposeGloasParentLinks() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final SignedBlockAndState block = createBlocksAtSlots(1).getFirst();
    try (final Response response = getResponse(GetForkChoiceV2.ROUTE)) {
      assertThat(response.code()).isEqualTo(SC_OK);
      final JsonNode nodes = getResponseData(response).path("fork_choice_nodes");
      int variants = 0;
      for (final JsonNode node : nodes) {
        if (!node.path("block_root").asText().equals(block.getRoot().toHexString())) {
          continue;
        }
        variants++;
        if (node.path("payload_status").asText().equals("pending")) {
          assertThat(node.path("parent_root").asText())
              .isEqualTo(block.getParentRoot().toHexString());
          assertThat(node.path("parent_payload_status").isTextual()).isTrue();
        } else {
          assertThat(node.path("parent_root").asText()).isEqualTo(block.getRoot().toHexString());
          assertThat(node.path("parent_payload_status").asText()).isEqualTo("pending");
        }
      }
      assertThat(variants).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  public void shouldGetForkChoiceAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = getResponse(GetForkChoiceV2.ROUTE);
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode node = getResponseData(response).path("fork_choice_nodes").get(0);
    assertThat(node.path("payload_status").asText()).isEqualTo("full");
    assertThat(node.has("parent_payload_status")).isTrue();
    assertThat(node.path("parent_payload_status").isNull()).isTrue();
    assertThat(node.path("payload_attester_count").asText()).isEqualTo("0");
    assertThat(node.path("payload_availability_yes_count").asText()).isEqualTo("0");
    assertThat(node.path("payload_data_availability_yes_count").asText()).isEqualTo("0");
    assertThat(node.path("justified_checkpoint").has("root")).isTrue();
    assertThat(node.path("finalized_checkpoint").has("root")).isTrue();
    assertThat(node.path("extra_data").has("state_root")).isTrue();
  }
}

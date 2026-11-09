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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStatePendingDeposits;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;

public class GetPendingDepositsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  public void shouldGetElectraDepositsJson() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ELECTRA);
    final List<SignedBlockAndState> data = createBlocksAtSlots(10);
    final Response response = get("head");

    final String responseText = response.body().string();
    final JsonNode node = JsonTestUtil.parseAsJsonNode(responseText);
    final BeaconStateElectra stateElectra =
        data.getLast().getState().toVersionElectra().orElseThrow();
    assertThat(node.get("version").asText()).isEqualTo("electra");
    assertThat(node.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(node.get("finalized").asBoolean()).isFalse();
    assertThat(node.get("data").size()).isEqualTo(2);
    assertThat(node.get("data").get(0).get("slot").asInt()).isEqualTo(10);
    assertThat(node.get("data").get(0).get("pubkey").asText())
        .isEqualTo(stateElectra.getPendingDeposits().get(0).getPublicKey().toHexString());
    assertThat(node.get("data").get(1).get("slot").asInt()).isEqualTo(10);
    assertThat(node.get("data").get(1).get("pubkey").asText())
        .isEqualTo(stateElectra.getPendingDeposits().get(1).getPublicKey().toHexString());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ELECTRA.lowerCaseName());
  }

  public Response get(final String stateId) throws IOException {
    return getResponse(GetStatePendingDeposits.ROUTE.replace("{state_id}", stateId));
  }
}

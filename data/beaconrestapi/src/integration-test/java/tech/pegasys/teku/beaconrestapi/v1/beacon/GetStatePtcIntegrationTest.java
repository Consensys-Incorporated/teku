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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStatePtc;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

public class GetStatePtcIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetPtcForStateSlot() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);

    try (final Response response = get("head")) {
      assertThat(response.code()).isEqualTo(SC_OK);
      final JsonNode node = JsonTestUtil.parseAsJsonNode(response.body().string());
      assertThat(node.has("version")).isFalse();
      assertThat(node.get("execution_optimistic").isBoolean()).isTrue();
      assertThat(node.get("finalized").isBoolean()).isTrue();
      assertThat(node.get("data").get("slot").asText()).isEqualTo("0");
      assertThat(node.get("data").get("validators").size())
          .isEqualTo(
              spec.getGenesisSpecConfig()
                  .toVersionGloas()
                  .orElseThrow()
                  .getPayloadTimelinessCommitteeSize());
    }
  }

  @Test
  public void shouldReturnBadRequestForSlotOutsidePtcWindow() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final int minSeedLookahead = spec.getGenesisSpecConfig().getMinSeedLookahead();
    final String outsideWindowSlot = String.valueOf((minSeedLookahead + 1) * slotsPerEpoch);

    try (final Response response =
        getResponse(
            GetStatePtc.ROUTE.replace("{state_id}", "head"), Map.of("slot", outsideWindowSlot))) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string()).contains("is not queryable");
    }
  }

  @Test
  public void shouldReturnBadRequestBeforeGloas() throws Exception {
    startRestAPIAtGenesis(TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(10)));
    createBlocksAtSlots(10);

    try (final Response response = get("head")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string())
          .contains("prior to GLOAS and does not contain payload timeliness committee");
    }
  }

  @Test
  public void shouldNotRegisterEndpointWithoutGloasSupport() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.FULU);

    try (final Response response = get("head")) {
      assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
    }
  }

  public Response get(final String stateId) throws IOException {
    return getResponse(GetStatePtc.ROUTE.replace("{state_id}", stateId));
  }
}

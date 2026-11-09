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

package tech.pegasys.teku.beaconrestapi.v1.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetBlockRewards;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetBlockRewardsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    spec = TestSpecFactory.createMinimalAltair();
    rewardCalculator = new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    chainBuilder.generateBlocksUpToSlot(10);

    final SyncAggregate syncAggregate =
        dataStructureUtil.randomSyncAggregate(0, 3, 4, 7, 8, 9, 10, 16, 17, 20, 23, 25, 26, 29, 30);
    final Attestation attestation =
        chainBuilder.streamValidAttestationsForBlockAtSlot(12).findFirst().orElseThrow();

    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create()
            .addAttestation(attestation)
            .setSyncAggregate(syncAggregate);
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(12, blockOptions);

    chainUpdater.updateBestBlock(blockAndState);
  }

  @Test
  public void shouldReturnBlockRewards() throws Exception {
    Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode jsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(jsonNode.get("execution_optimistic").asText()).isEqualTo("false");
    assertThat(jsonNode.get("finalized").asText()).isEqualTo("false");

    final JsonNode data = jsonNode.get("data");
    assertThat(data.get("proposer_index").asText()).isEqualTo("6");
    assertThat(data.get("total").asText()).isEqualTo("178076");
    assertThat(data.get("attestations").asText()).isEqualTo("166106");
    assertThat(data.get("sync_aggregate").asText()).isEqualTo("11970");
    assertThat(data.get("proposer_slashings").asText()).isEqualTo("0");
    assertThat(data.get("attester_slashings").asText()).isEqualTo("0");
  }

  private Response get(final String blockId) throws IOException {
    return getResponse(GetBlockRewards.ROUTE.replace("{block_id}", blockId));
  }
}

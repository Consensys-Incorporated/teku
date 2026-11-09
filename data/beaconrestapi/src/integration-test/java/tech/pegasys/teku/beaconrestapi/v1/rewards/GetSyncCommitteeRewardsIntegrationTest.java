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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetSyncCommitteeRewards;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetSyncCommitteeRewardsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    spec = TestSpecFactory.createMinimalAltair();
    rewardCalculator = new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final SyncAggregate syncAggregate =
        dataStructureUtil.randomSyncAggregate(0, 3, 4, 7, 8, 9, 10, 16, 17, 20, 23, 25, 26, 29, 30);
    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create().setSyncAggregate(syncAggregate);
    SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(3, blockOptions);
    chainUpdater.updateBestBlock(blockAndState);
  }

  @Test
  public void handleEmptyRequestBodyList() throws IOException {
    final Response response1 =
        post(GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"), "[]");

    final SyncCommitteeRewardData data = new SyncCommitteeRewardData(false, false);
    data.increaseReward(0, 11180L);
    data.decreaseReward(1, 11180L);
    data.decreaseReward(2, 11180L);
    data.increaseReward(3, 11180L);
    data.increaseReward(4, 0L);
    data.increaseReward(5, 11180L);
    data.increaseReward(6, 0L);
    data.decreaseReward(7, 11180L);
    data.increaseReward(8, 11180L);
    data.increaseReward(9, 0L);
    data.increaseReward(10, 11180L);
    data.decreaseReward(11, 11180L);
    data.increaseReward(12, 0L);
    data.decreaseReward(13, 11180L);
    data.decreaseReward(14, 11180L);
    data.increaseReward(15, 0L);
    final String expectedResponse = serialize(data, GetSyncCommitteeRewards.RESPONSE_TYPE);

    assertThat(response1.code()).isEqualTo(SC_OK);
    assertThat(response1.body().string()).isEqualTo(expectedResponse);

    final Response response2 =
        post(GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"), "");
    assertThat(response2.code()).isEqualTo(SC_OK);
    assertThat(response2.body().string()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnBadRequestWhenOutOfValidatorRange() throws IOException {
    final List<String> requestBody = List.of("9a");
    final Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            OBJECT_MAPPER.writeValueAsString(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .startsWith("{\"code\":400,\"message\":\"'9a' was expected to be a committee index");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnValueOnlyForValidatorsInCommittee() throws Exception {
    final List<String> requestBody = List.of("1", "6", "0");
    final Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            OBJECT_MAPPER.writeValueAsString(requestBody));

    final Map<String, Object> body = JsonTestUtil.parse(response.body().string());
    final ArrayList<HashMap<String, String>> data =
        (ArrayList<HashMap<String, String>>) body.get("data");

    // 0 didn't participate, 1 participated, 6 not in committee
    assertThat(data.get(0)).isEqualTo(Map.of("validator_index", "0", "reward", "11180"));
    assertThat(data.get(1)).isEqualTo(Map.of("validator_index", "1", "reward", "-11180"));
    assertThat(data.get(2)).isEqualTo(Map.of("validator_index", "6", "reward", "0"));

    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnValueOnlyForValidatorsPublicKeysInCommittee() throws IOException {
    final List<String> requestBody = getPubKeysRequestBody(0, 6);
    final Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            OBJECT_MAPPER.writeValueAsString(requestBody));

    final SyncCommitteeRewardData data = new SyncCommitteeRewardData(false, false);
    data.increaseReward(3, 11180L);
    data.decreaseReward(11, 11180L);
    final String expectedResponse = serialize(data, GetSyncCommitteeRewards.RESPONSE_TYPE);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEqualTo(expectedResponse);
  }

  private List<String> getPubKeysRequestBody(final int... committeeIndices) {
    final UInt64 slot = chainBuilder.getLatestSlot();
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final SszVector<SszPublicKey> committee =
        spec.getSyncCommitteeUtil(slot)
            .orElseThrow()
            .getSyncCommittee(chainBuilder.getStateAtSlot(slot), epoch)
            .getPubkeys();

    final List<String> requestBody = new ArrayList<>();
    for (int i : committeeIndices) {
      requestBody.add(committee.get(i).getBLSPublicKey().toHexString());
    }
    return requestBody;
  }
}

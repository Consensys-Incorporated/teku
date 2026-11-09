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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetAttestationRewards;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class GetAttestationRewardsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  final int validRewardsEpoch = 0;
  final int upperBoundaryEpoch = 2;

  @BeforeEach
  public void setup() {
    spec = TestSpecFactory.createMinimalAltair();
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(upperBoundaryEpoch));
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(slot));
  }

  @Test
  public void shouldReturnNotFoundWhenQueryingEpochOutOfRange() throws IOException {
    Response response = sendGetAttestationRewardsRequest(Long.MAX_VALUE, List.of());
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(Objects.requireNonNull(response.body()).string()).contains("Invalid epoch range");
  }

  @Test
  public void shouldReturnAllValidatorsWhenValidatorIdListIsEmpty() throws Exception {
    final SszList<Validator> validators =
        recentChainData.getBestState().orElseThrow().get().getValidators();
    Response response = sendGetAttestationRewardsRequest(validRewardsEpoch, List.of());
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode jsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(jsonNode.get("data").get("total_rewards").size()).isEqualTo(validators.size());
  }

  @Test
  public void shouldReturnAllValidatorsWhenRequestBodyIsEmpty() throws Exception {
    final SszList<Validator> validators =
        recentChainData.getBestState().orElseThrow().get().getValidators();
    Response response = sendGetAttestationRewardsRequest(validRewardsEpoch, null);
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode jsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(jsonNode.get("data").get("total_rewards").size()).isEqualTo(validators.size());
  }

  @Test
  public void shouldReturnValidatorsMatchingValidatorIdsProvided() throws Exception {
    final SszList<Validator> validators =
        recentChainData.getBestState().orElseThrow().get().getValidators();
    final List<String> validatorIds = List.of("0", validators.get(1).getPublicKey().toHexString());
    Response response = sendGetAttestationRewardsRequest(validRewardsEpoch, validatorIds);
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode jsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    final JsonNode totalRewards = jsonNode.get("data").get("total_rewards");
    assertThat(totalRewards.size()).isEqualTo(validatorIds.size());
    assertThat(totalRewards.get(0).get("validator_index").asText()).isEqualTo("0");
    assertThat(totalRewards.get(1).get("validator_index").asText()).isEqualTo("1");
  }

  private Response sendGetAttestationRewardsRequest(
      final long epoch, final List<String> validatorIds) {
    try {
      final String requestBody =
          validatorIds != null ? OBJECT_MAPPER.writeValueAsString(validatorIds) : "";
      return post(
          GetAttestationRewards.ROUTE.replace("{epoch}", String.valueOf(epoch)), requestBody);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

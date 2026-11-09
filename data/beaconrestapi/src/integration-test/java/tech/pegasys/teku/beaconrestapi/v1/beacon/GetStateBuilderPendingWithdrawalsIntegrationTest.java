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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.stream.Stream;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateBuilderPendingWithdrawals;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetStateBuilderPendingWithdrawalsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private static final int WITHDRAWAL_COUNT = 3;

  @Test
  public void shouldGetBuilderPendingWithdrawalsJson() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.GLOAS, this::withBuilderPendingWithdrawals);
    final SszList<BuilderPendingWithdrawal> expected = getHeadStateBuilderPendingWithdrawals();
    assertThat(expected).hasSize(WITHDRAWAL_COUNT);

    try (final Response response = get("head")) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.header(HEADER_CONSENSUS_VERSION))
          .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
      final JsonNode node = JsonTestUtil.parseAsJsonNode(response.body().string());
      assertThat(node.get("version").asText()).isEqualTo("gloas");
      assertThat(node.get("execution_optimistic").isBoolean()).isTrue();
      assertThat(node.get("finalized").isBoolean()).isTrue();
      assertThat(
              JsonUtil.parse(
                  node.get("data").toString(), expected.getSchema().getJsonTypeDefinition()))
          .isEqualTo(expected);
    }
  }

  @Test
  public void shouldGetBuilderPendingWithdrawalsSsz() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.GLOAS, this::withBuilderPendingWithdrawals);
    final SszList<BuilderPendingWithdrawal> expected = getHeadStateBuilderPendingWithdrawals();
    assertThat(expected).hasSize(WITHDRAWAL_COUNT);

    try (final Response response =
        getResponse(
            GetStateBuilderPendingWithdrawals.ROUTE.replace("{state_id}", "head"), OCTET_STREAM)) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.header(HEADER_CONSENSUS_VERSION))
          .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
      assertThat(Bytes.wrap(response.body().bytes())).isEqualTo(expected.sszSerialize());
    }
  }

  @Test
  public void shouldReturnBadRequestBeforeGloas() throws Exception {
    startRestAPIAtGenesis(TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(10)));
    createBlocksAtSlots(10);

    try (final Response response = get("head")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string())
          .contains("prior to GLOAS and does not contain builder pending withdrawals");
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
    return getResponse(GetStateBuilderPendingWithdrawals.ROUTE.replace("{state_id}", stateId));
  }

  private BeaconState withBuilderPendingWithdrawals(final BeaconState state) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconStateGloas gloasState = BeaconStateGloas.required(state);
    final SszList<BuilderPendingWithdrawal> withdrawals =
        gloasState
            .getBuilderPendingWithdrawals()
            .getSchema()
            .createFromElements(
                Stream.generate(dataStructureUtil::randomBuilderPendingWithdrawal)
                    .limit(WITHDRAWAL_COUNT)
                    .toList());
    return gloasState.updatedGloas(mutable -> mutable.setBuilderPendingWithdrawals(withdrawals));
  }

  private SszList<BuilderPendingWithdrawal> getHeadStateBuilderPendingWithdrawals() {
    return BeaconStateGloas.required(safeJoin(recentChainData.getBestState().orElseThrow()))
        .getBuilderPendingWithdrawals();
  }
}

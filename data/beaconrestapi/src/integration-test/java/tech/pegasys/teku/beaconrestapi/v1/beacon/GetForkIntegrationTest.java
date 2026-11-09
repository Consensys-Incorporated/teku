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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class GetForkIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetForkChoiceAtEmptyHeadSlot() throws IOException {
    createBlocksAtSlots(10, 11, 12);
    setCurrentSlot(13);
    final Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);

    final Fork expected = safeJoin(recentChainData.getBestState().get()).getFork();
    assertThat(data.get("current_version").asText())
        .isEqualTo(expected.getCurrentVersion().toHexString());
    assertThat(data.get("previous_version").asText())
        .isEqualTo(expected.getPreviousVersion().toHexString());
    assertThat(data.get("epoch").asText()).isEqualTo(expected.getEpoch().toString());
  }

  public Response get(final String stateIdString) throws IOException {
    return getResponse(GetStateFork.ROUTE.replace("{state_id}", stateIdString));
  }
}

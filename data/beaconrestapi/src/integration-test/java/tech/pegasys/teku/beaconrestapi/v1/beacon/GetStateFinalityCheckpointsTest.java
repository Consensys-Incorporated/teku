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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;

public class GetStateFinalityCheckpointsTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetEmptyCheckpointsAtGenesis() throws IOException {
    startRestAPIAtGenesis();
    final Response response = get("genesis");
    final JsonNode data = getResponseData(response);

    checkIsEmpty(data.get("current_justified"));
    checkIsEmpty(data.get("previous_justified"));
    checkIsEmpty(data.get("finalized"));
  }

  private void checkIsEmpty(final JsonNode node) {
    assertThat(node.get("root").asText()).isEqualTo(Bytes32.ZERO.toHexString());
    assertThat(node.get("epoch").asInt()).isZero();
  }

  public Response get(final String stateIdString) throws IOException {
    return getResponse(GetStateFinalityCheckpoints.ROUTE.replace("{state_id}", stateIdString));
  }
}

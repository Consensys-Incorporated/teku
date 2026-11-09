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

package tech.pegasys.teku.beaconrestapi.v2.node;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.node.GetVersionV2;

public class GetVersionV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetVersionV2FromRunningServer() throws IOException {
    startRestAPIAtGenesis();
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    final JsonNode beaconNode = data.get("beacon_node");
    assertThat(beaconNode).isNotNull();
    assertThat(beaconNode.get("code").asText()).isEqualTo("TK");
    assertThat(beaconNode.get("name").asText()).isEqualTo("teku");
    assertThat(beaconNode.get("version").asText()).isNotEmpty();
    assertThat(beaconNode.get("commit").asText()).isNotEmpty();
    // execution_client is optional and may be null if not connected to execution layer
    final JsonNode executionClient = data.get("execution_client");
    if (executionClient != null && !executionClient.isNull()) {
      assertThat(executionClient.get("code").asText()).isNotEmpty();
      assertThat(executionClient.get("name").asText()).isNotEmpty();
      assertThat(executionClient.get("version").asText()).isNotEmpty();
      assertThat(executionClient.get("commit").asText()).isNotEmpty();
    }
  }

  private Response get() throws IOException {
    return getResponse(GetVersionV2.ROUTE);
  }
}

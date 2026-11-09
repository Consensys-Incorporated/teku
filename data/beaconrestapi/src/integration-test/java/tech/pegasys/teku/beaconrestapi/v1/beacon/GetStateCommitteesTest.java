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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;

public class GetStateCommitteesTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetCommitteesWithNoQueryParameters() throws IOException {
    final Response response = get("head", emptyMap());
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data.size()).isGreaterThan(0);
  }

  @Test
  public void shouldGetCommitteesForNextEpoch() throws IOException {
    final Response response = get("head", Map.of("epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    for (int i = 0; i < data.size(); i++) {
      assertThat(data.get(i).get("slot").asInt())
          .isGreaterThanOrEqualTo(specConfig.getSlotsPerEpoch());
      assertThat(data.get(i).get("slot").asLong()).isLessThan(specConfig.getSlotsPerEpoch() * 2L);
    }
    assertThat(data.size()).isEqualTo(8);
  }

  @Test
  public void shouldGetCommitteesForSingleIndex() throws IOException {
    final Response response = get("head", Map.of("index", "0"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data.size()).isEqualTo(8);
    for (int i = 0; i < data.size(); i++) {
      assertThat(data.get(i).get("index").asInt()).isZero();
    }
  }

  @Test
  public void shouldGetCommitteesForSingleIndexAndSlotAndEpoch() throws IOException {
    final Response response = get("head", Map.of("index", "0", "slot", "9", "epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data.size()).isEqualTo(1);
    assertThat(data.get(0).get("index").asInt()).isZero();
    assertThat(data.get(0).get("slot").asInt()).isEqualTo(9);
    assertThat(data.get(0).get("validators").size()).isEqualTo(2);
  }

  @Test
  public void shouldGetBadRequestIfSlotAndEpochDontAlign() throws IOException {
    final Response response = get("head", Map.of("slot", "1", "epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());
    assertThat(body.get("code").asInt()).isEqualTo(SC_BAD_REQUEST);
    assertThat(body.get("message").asText()).isEqualToIgnoringCase("Slot 1 is not in epoch 1");
  }

  @Test
  public void shouldGetBadRequestIfEpochTooFarInFuture() throws IOException {
    final Response response = get("head", Map.of("epoch", "1024000"));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());
    assertThat(body.get("code").asInt()).isEqualTo(SC_BAD_REQUEST);
    assertThat(body.get("message").asText()).startsWith("Epoch 1024000 is too far ahead ");
  }

  public Response get(final String stateIdString, final Map<String, String> query)
      throws IOException {
    return getResponse(GetStateCommittees.ROUTE.replace("{state_id}", stateIdString), query);
  }
}

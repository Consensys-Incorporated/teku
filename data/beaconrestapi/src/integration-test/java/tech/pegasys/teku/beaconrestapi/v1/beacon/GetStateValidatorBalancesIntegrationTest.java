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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;

public class GetStateValidatorBalancesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void queryFiltersCanRemoveAllResults() throws IOException {
    final Response response = get("head", Map.of("id", "1024000"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data).isEmpty();
  }

  @Test
  public void queryFiltersCanFilterOnValidatorId() throws IOException {
    final Response response = get("genesis", Map.of("id", "1,16"));
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode data = getResponseData(response);
    assertThat(data.get(0).get("index").asInt()).isEqualTo(1);
    assertThat(data.get(0).get("balance").asLong())
        .isEqualTo(specConfig.getMaxEffectiveBalance().longValue());
    assertThat(data.size()).isEqualTo(1);
  }

  @Test
  public void shouldReturnAllBalancesWithoutQueryParameter() throws IOException {
    final Response response = get("finalized", Collections.emptyMap());
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data).hasSize(16);
  }

  public Response get(final String stateIdString, final Map<String, String> query)
      throws IOException {
    return getResponse(GetStateValidatorBalances.ROUTE.replace("{state_id}", stateIdString), query);
  }
}

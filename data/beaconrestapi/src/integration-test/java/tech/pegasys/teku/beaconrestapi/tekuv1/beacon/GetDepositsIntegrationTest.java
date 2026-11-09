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

package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDeposits;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetDepositsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  public void shouldReturnEmptyListWhenNoDeposits() throws IOException {
    when(eth1DataProvider.getAvailableDeposits()).thenReturn(new ArrayList<>());
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data).isNotNull();
    assertThat(data.isArray()).isTrue();
    assertThat(data.size()).isZero();
  }

  @Test
  public void shouldReturnAllAvailableDeposits() throws IOException {
    DepositWithIndex deposit1 = dataStructureUtil.randomDepositWithIndex();
    DepositWithIndex deposit2 = dataStructureUtil.randomDepositWithIndex();
    List<DepositWithIndex> deposits = new ArrayList<>();
    deposits.add(deposit1);
    deposits.add(deposit2);
    when(eth1DataProvider.getAvailableDeposits()).thenReturn(deposits);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final String actualResponse = response.body().string();
    assertThat(actualResponse)
        .isEqualTo(JsonUtil.serialize(deposits, GetDeposits.DEPOSITS_RESPONSE_TYPE));
  }

  @Test
  public void shouldReturnServerErrorWhenProviderFails() throws IOException {
    when(eth1DataProvider.getAvailableDeposits()).thenThrow(new RuntimeException(""));
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  private Response get() throws IOException {
    return getResponse(GetDeposits.ROUTE);
  }
}

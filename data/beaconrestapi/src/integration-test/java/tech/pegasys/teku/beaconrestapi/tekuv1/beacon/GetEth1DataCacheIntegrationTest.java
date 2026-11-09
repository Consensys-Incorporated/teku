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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1DataCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetEth1DataCacheIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  public void shouldReturnAllEth1BlocksFromCache() throws IOException {
    final List<Eth1Data> eth1DataCacheList = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      eth1DataCacheList.add(dataStructureUtil.randomEth1Data());
    }
    final List<Eth1Data> eth1DataCacheBlocks = new ArrayList<>(eth1DataCacheList);
    when(eth1DataProvider.getEth1CachedBlocks()).thenReturn(eth1DataCacheList);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data.size()).isEqualTo(eth1DataCacheBlocks.size());
    for (int i = 0; i < data.size(); i++) {
      final JsonNode block = data.get(i);
      final Eth1Data eth1Data = eth1DataCacheBlocks.get(i);
      assertThat(Bytes32.fromHexString(block.get("deposit_root").asText()))
          .isEqualTo(eth1Data.getDepositRoot());
      assertThat(UInt64.valueOf(block.get("deposit_count").asText()))
          .isEqualTo(eth1Data.getDepositCount());
      assertThat(Bytes32.fromHexString(block.get("block_hash").asText()))
          .isEqualTo(eth1Data.getBlockHash());
    }
  }

  @Test
  public void shouldReturnNotFoundWhenCacheIsEmpty() throws IOException {
    when(eth1DataProvider.getEth1CachedBlocks()).thenReturn(new ArrayList<>());
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().string()).contains("Eth1 blocks cache is empty");
  }

  private Response get() throws IOException {
    return getResponse(GetEth1DataCache.ROUTE);
  }
}

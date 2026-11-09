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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

public class GetEth1DataCacheTest extends AbstractMigratedBeaconHandlerTest {
  private final Eth1DataProvider eth1DataProvider = mock(Eth1DataProvider.class);

  private final List<Eth1Data> eth1DataCacheBlocks =
      Arrays.asList(
          dataStructureUtil.randomEth1Data(),
          dataStructureUtil.randomEth1Data(),
          dataStructureUtil.randomEth1Data());

  @BeforeEach
  void setup() {
    setHandler(new GetEth1DataCache(eth1DataProvider));
  }

  @Test
  public void shouldReturnListOfCachedEth1Blocks() throws JsonProcessingException {
    when(eth1DataProvider.getEth1CachedBlocks()).thenReturn(eth1DataCacheBlocks);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    assertThat(request.getResponseBody()).isEqualTo(eth1DataCacheBlocks);
  }

  @Test
  public void shouldReturnNotFoundWhenCacheIsEmpty() throws JsonProcessingException {
    when(eth1DataProvider.getEth1CachedBlocks()).thenReturn(new ArrayList<>());
    handler.handleRequest(request);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new HttpErrorResponse(HttpStatusCodes.SC_NOT_FOUND, "Eth1 blocks cache is empty"));
  }
}

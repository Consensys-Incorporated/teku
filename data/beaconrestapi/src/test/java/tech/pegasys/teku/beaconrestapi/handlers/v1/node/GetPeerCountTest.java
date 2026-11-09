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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.node.PeerCountBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class GetPeerCountTest extends AbstractMigratedBeaconHandlerTest {
  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final PeerCount peerCount =
      new PeerCountBuilder().disconnected(UInt64.valueOf(2)).connected(UInt64.valueOf(4)).build();

  @BeforeEach
  void setUp() {
    setHandler(new GetPeerCount(networkDataProvider));
  }

  @Test
  public void shouldReturnListOfPeers() throws Exception {

    when(networkDataProvider.getPeerCount()).thenReturn(peerCount);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(peerCount);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, peerCount);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"disconnected\":\"2\",\"connecting\":\"0\",\"connected\":\"4\",\"disconnecting\":\"0\"}}");
  }
}

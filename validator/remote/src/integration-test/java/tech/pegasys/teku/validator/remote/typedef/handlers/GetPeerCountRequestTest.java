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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class GetPeerCountRequestTest extends AbstractTypeDefRequestTestBase {

  private GetPeerCountRequest request;

  @BeforeEach
  public void setupRequest() {
    request = new GetPeerCountRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  public void correctResponseDeserialization() {
    final String mockResponse = readResource("responses/get_peer_count.json");

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    final Optional<PeerCount> maybePeerCount = request.submit();
    assertThat(maybePeerCount).isPresent();

    final PeerCount peerCount = maybePeerCount.get();
    assertThat(peerCount.getConnected().longValue()).isEqualTo(56);
    assertThat(peerCount.getDisconnected().longValue()).isEqualTo(12);
    assertThat(peerCount.getConnecting().longValue()).isEqualTo(0);
    assertThat(peerCount.getDisconnecting().longValue()).isEqualTo(0);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit())
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

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

package tech.pegasys.teku.beaconrestapi.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeerByIdIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  final Eth2Peer peer = mock(Eth2Peer.class);
  final MockNodeId node1 = new MockNodeId(0);
  final PeerAddress peerAddress = mock(PeerAddress.class);

  @Test
  public void shouldGetPeerById() throws IOException {
    startRestAPIAtGenesis();
    when(eth2P2PNetwork.getPeer(any())).thenReturn(Optional.of(peer));
    when(peer.getId()).thenReturn(node1);
    when(peer.getAddress()).thenReturn(peerAddress);
    when(peerAddress.toExternalForm()).thenReturn("/ip/1.2.3.4/tcp/4242/p2p/aeiou");
    when(peer.isConnected()).thenReturn(true);
    when(peer.connectionInitiatedLocally()).thenReturn(true);

    final Response response = get("QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N");
    assertThat(response.code()).isEqualTo(SC_OK);

    final JsonNode data = getResponseData(response);
    assertThat(data.get("peer_id").asText()).isEqualTo(node1.toBase58());
    assertThat(data.get("enr")).isNull();
    assertThat(data.get("last_seen_p2p_address").asText())
        .isEqualTo("/ip/1.2.3.4/tcp/4242/p2p/aeiou");
    assertThat(data.get("state").asText()).isEqualTo("connected");
    assertThat(data.get("direction").asText()).isEqualTo("outbound");
  }

  private Response get(final String peerId) throws IOException {
    return getResponse(GetPeerById.ROUTE.replace("{epoch}", peerId));
  }
}

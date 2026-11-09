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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.peer.Eth2PeerWithEnr;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.PeersData;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeersTest extends AbstractMigratedBeaconHandlerTest {
  private final MockNodeId peerId1 = new MockNodeId(123456);
  private final Eth2Peer peer1 = mock(Eth2Peer.class);

  private final MockNodeId peerId2 = new MockNodeId(789123);
  private final Eth2Peer peer2 = mock(Eth2Peer.class);

  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final List<Eth2PeerWithEnr> data =
      List.of(
          new Eth2PeerWithEnr(peer1, Optional.empty()),
          new Eth2PeerWithEnr(peer2, Optional.empty()));
  private final GetPeers.PeersData peersData = new PeersData(data);

  @BeforeEach
  void setup() {
    setHandler(new GetPeers(networkDataProvider));
    when(peer1.getId()).thenReturn(peerId1);
    when(peer1.getAddress()).thenReturn(new PeerAddress(peerId1));
    when(peer1.isConnected()).thenReturn(true);
    when(peer1.connectionInitiatedLocally()).thenReturn(false);
    when(peer1.getAgentVersion()).thenReturn(Optional.of("Lighthouse/v8.1.3"));
    when(peer1.getGossipScore()).thenReturn(-17.4828);

    when(peer2.getId()).thenReturn(peerId2);
    when(peer2.getAddress()).thenReturn(new PeerAddress(peerId2));
    when(peer2.isConnected()).thenReturn(true);
    when(peer2.connectionInitiatedLocally()).thenReturn(true);
    when(peer2.getAgentVersion()).thenReturn(Optional.empty());
    when(peer2.getGossipScore()).thenReturn(3.5);
  }

  @Test
  public void shouldReturnListOfPeers() throws Exception {

    when(networkDataProvider.getEth2PeersWithEnr()).thenReturn(data);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
    assertThat(request.getResponseBody()).isEqualTo(peersData);
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, peersData);
    assertThat(data)
        .isEqualTo(
            "{\"data\":[{\"peer_id\":\"1111111111111111111111111111177em\","
                + "\"last_seen_p2p_address\":\"1111111111111111111111111111177em\","
                + "\"state\":\"connected\",\"direction\":\"inbound\","
                + "\"agent_version\":\"Lighthouse/v8.1.3\",\"score\":-17.4828},"
                + "{\"peer_id\":\"11111111111111111111111111111hVqL\","
                + "\"last_seen_p2p_address\":\"11111111111111111111111111111hVqL\","
                + "\"state\":\"connected\",\"direction\":\"outbound\","
                + "\"score\":3.5}],\"meta\":{\"count\":2}}");
  }
}

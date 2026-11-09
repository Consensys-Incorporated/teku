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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.peer.Eth2PeerWithEnr;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class GetPeerByIdTest extends AbstractMigratedBeaconHandlerTest {
  private final MockNodeId peerId = new MockNodeId(123456);
  private final NodeId peerNodeId = mock(NodeId.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private static final String ENR_STUB = "enr:test";
  private final Eth2PeerWithEnr peerWithEnr = new Eth2PeerWithEnr(peer, Optional.of(ENR_STUB));

  @BeforeEach
  void setUp() {
    setHandler(new GetPeerById(network));
    request.setPathParameter("peer_id", peerId.toBase58());
    when(peer.getId()).thenReturn(peerId);
    when(peer.getAddress()).thenReturn(new PeerAddress(peerId));
    when(peer.isConnected()).thenReturn(true);
    when(peer.connectionInitiatedLocally()).thenReturn(false);
    when(peer.getAgentVersion()).thenReturn(Optional.empty());
    when(peer.getGossipScore()).thenReturn(2.0);
  }

  @Test
  public void shouldReturnNotFoundIfPeerNotFound() throws Exception {
    when(eth2P2PNetwork.parseNodeId(peerId.toBase58())).thenReturn(peerNodeId);
    when(eth2P2PNetwork.getPeer(eq(peerNodeId))).thenReturn(Optional.empty());
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Peer not found"));
  }

  @Test
  public void shouldReturnPeerIfFound() throws Exception {
    when(eth2P2PNetwork.parseNodeId(peerId.toBase58())).thenReturn(peerNodeId);
    when(eth2P2PNetwork.getPeer(eq(peerNodeId))).thenReturn(Optional.of(peer));
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(UInt256.ONE));
    final DiscoveryNetwork<?> discoveryNetwork = mock(DiscoveryNetwork.class);
    when(eth2P2PNetwork.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
    final DiscoveryService discoveryService = mock(DiscoveryService.class);
    when(discoveryNetwork.getDiscoveryService()).thenReturn(discoveryService);
    when(discoveryService.lookupEnr(any())).thenReturn(Optional.of(ENR_STUB));
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(peerWithEnr);
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, peerWithEnr);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"peer_id\":\"1111111111111111111111111111177em\","
                + "\"enr\":\"enr:test\","
                + "\"last_seen_p2p_address\":\"1111111111111111111111111111177em\","
                + "\"state\":\"connected\",\"direction\":\"inbound\",\"score\":2.0}}");
  }
}

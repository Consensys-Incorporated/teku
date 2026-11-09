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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer.PeerStatusSubscriber;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2PeerManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final PeerStatusFactory statusFactory = PeerStatusFactory.create(1L, spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Eth2PeerFactory eth2PeerFactory = mock(Eth2PeerFactory.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StatusMessageFactory statusMessageFactory =
      new StatusMessageFactory(spec, combinedChainDataClient, metricsSystem);

  private final Map<Peer, Eth2Peer> eth2Peers = new HashMap<>();

  private final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
  private final Eth2PeerManager peerManager =
      new Eth2PeerManager(
          spec,
          asyncRunner,
          combinedChainDataClient,
          () -> CustodyGroupCountManager.NOOP,
          recentChainData,
          new NoOpMetricsSystem(),
          eth2PeerFactory,
          statusMessageFactory,
          new MetadataMessagesFactory(),
          rpcEncoding,
          Eth2P2PNetworkBuilder.DEFAULT_ETH2_RPC_PING_INTERVAL,
          Eth2P2PNetworkBuilder.DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD,
          Eth2P2PNetworkBuilder.DEFAULT_ETH2_STATUS_UPDATE_INTERVAL,
          DataColumnSidecarArchiveReconstructor.NOOP,
          DasReqRespLogger.NOOP,
          Optional.empty());

  @BeforeEach
  public void setUp() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
  }

  @Test
  public void subscribeConnect_singleListener() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer(1);
    peerManager.onConnect(peer);

    // Connect event should not broadcast until status is set
    assertThat(connectedPeers).isEmpty();

    // Set status and check event was broadcast
    setInitialPeerStatus(peer);
    assertThat(connectedPeers).containsExactly(getEth2Peer(peer));
  }

  @Test
  public void subscribeConnect_singleListener_multiplePeers() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer(1);
    peerManager.onConnect(peer);
    setInitialPeerStatus(peer);
    assertThat(connectedPeers).containsExactly(getEth2Peer(peer));

    // Add another peer
    final Peer peerB = createPeer(2);
    peerManager.onConnect(peerB);
    setInitialPeerStatus(peerB);
    assertThat(connectedPeers).containsExactly(getEth2Peer(peer), getEth2Peer(peerB));
  }

  @Test
  public void subscribeConnect_multipleListeners() {
    final List<Peer> connectedPeers = new ArrayList<>();
    final List<Peer> connectedPeersB = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    peerManager.subscribeConnect(connectedPeersB::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    final Peer peer = createPeer(1);
    peerManager.onConnect(peer);
    setInitialPeerStatus(peer);

    assertThat(connectedPeers).containsExactly(getEth2Peer(peer));
    assertThat(connectedPeersB).containsExactly(getEth2Peer(peer));
  }

  @Test
  void onConnect_shouldDisconnectIfPeerReturnsErrorResponseToStatusMessage() {
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = getEth2Peer(peer);
    when(peer.connectionInitiatedLocally()).thenReturn(true);
    when(eth2Peer.sendStatus())
        .thenReturn(
            SafeFuture.failedFuture(
                new RpcException(RpcResponseStatus.SERVER_ERROR_CODE, "It went boom")));

    peerManager.onConnect(peer);

    verify(eth2Peer).disconnectImmediately(Optional.of(DisconnectReason.REMOTE_FAULT), true);
  }

  @Test
  void onConnect_shouldDisconnectIfStatusMessageFailsToSend() {
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = getEth2Peer(peer);
    when(peer.connectionInitiatedLocally()).thenReturn(true);
    when(eth2Peer.sendStatus())
        .thenReturn(SafeFuture.failedFuture(new IOException("Failed to send")));

    peerManager.onConnect(peer);

    verify(eth2Peer).disconnectImmediately(Optional.empty(), true);
  }

  @Test
  void onConnect_shouldDisconnectIfIncomingPeerDoesNotSendStatusMessage() {
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = getEth2Peer(peer);
    when(eth2Peer.hasStatus()).thenReturn(false);
    when(peer.connectionInitiatedLocally()).thenReturn(false);

    peerManager.onConnect(peer);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    // Didn't receive a status message in time, so disconnect.
    verify(eth2Peer).disconnectCleanly(DisconnectReason.NO_STATUS_RECEIVED);
  }

  @Test
  void onConnect_shouldNotDisconnectIncomingPeerWhenStatusMessageSent() {
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = getEth2Peer(peer);
    when(eth2Peer.hasStatus()).thenReturn(true);
    when(peer.connectionInitiatedLocally()).thenReturn(false);

    peerManager.onConnect(peer);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // We receive peer status before the timeout
    setInitialPeerStatus(peer);
    asyncRunner.executeQueuedActions();

    // So don't disconnect
    verify(eth2Peer, never()).disconnectCleanly(DisconnectReason.REMOTE_FAULT);
  }

  private Peer createPeer(final int id) {
    final Peer peer = mock(Peer.class);
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    eth2Peers.put(peer, eth2Peer);
    when(peer.getId()).thenReturn(new MockNodeId(id));
    when(eth2PeerFactory.create(same(peer), any())).thenReturn(eth2Peer);
    return peer;
  }

  private Eth2Peer getEth2Peer(final Peer peer) {
    return eth2Peers.get(peer);
  }

  private Eth2Peer createEth2Peer(final Peer peer) {
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.idMatches(peer)).thenReturn(true);
    when(peer.idMatches(eth2Peer)).thenReturn(true);
    final NodeId peerId = peer.getId();
    when(eth2Peer.getId()).thenReturn(peerId);
    return eth2Peer;
  }

  private void setInitialPeerStatus(final Peer peer) {
    final ArgumentCaptor<PeerStatusSubscriber> subscriberArgumentCaptor =
        ArgumentCaptor.forClass(PeerStatusSubscriber.class);
    verify(getEth2Peer(peer)).subscribeInitialStatus(subscriberArgumentCaptor.capture());
    subscriberArgumentCaptor.getValue().onPeerStatus(statusFactory.random());
  }
}

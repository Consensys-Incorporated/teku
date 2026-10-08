/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.Connection;
import io.libp2p.core.Network;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.security.SecureChannel.Session;
import io.libp2p.crypto.keys.EcdsaKt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.Transport;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class PeerManagerTest {

  private final ReputationManager reputationManager = mock(ReputationManager.class);
  private final Network network = mock(Network.class);
  final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final PeerManager peerManager =
      new PeerManager(
          metricsSystem,
          reputationManager,
          Collections.emptyList(),
          Collections.emptyList(),
          peerId -> 0.0);

  @Test
  public void subscribeConnect_singleListener() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);
    assertThat(connectedPeers).containsExactly(peer);

    // Add another peer
    final Peer peer2 = mock(Peer.class);
    when(peer2.getId()).thenReturn(new MockNodeId(2));
    peerManager.onConnectedPeer(peer2);
    assertThat(connectedPeers).containsExactly(peer, peer2);
  }

  @Test
  public void shouldCreatePeerTypeMetrics() {
    final MetricsSystem metricsSystem = mock(MetricsSystem.class);
    final LabelledSuppliedMetric peerClientLabelledGauge = mock(LabelledSuppliedMetric.class);
    final LabelledSuppliedMetric peerDirectionLabelledGauge = mock(LabelledSuppliedMetric.class);

    when(metricsSystem.createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P), eq("connected_peers_current"), any(), eq("client")))
        .thenReturn(peerClientLabelledGauge);
    when(metricsSystem.createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P),
            eq("peers_direction_current"),
            any(),
            eq("direction"),
            eq("transport")))
        .thenReturn(peerDirectionLabelledGauge);
    new PeerManager(
        metricsSystem,
        reputationManager,
        Collections.emptyList(),
        Collections.emptyList(),
        peerId -> 0.0);

    for (PeerClientType type : PeerClientType.values()) {
      verify(peerClientLabelledGauge).labels(any(), eq(type.getDisplayName()));
    }
    verify(peerDirectionLabelledGauge).labels(any(), eq("inbound"), eq("tcp"));
    verify(peerDirectionLabelledGauge).labels(any(), eq("inbound"), eq("quic"));
    verify(peerDirectionLabelledGauge).labels(any(), eq("outbound"), eq("tcp"));
    verify(peerDirectionLabelledGauge).labels(any(), eq("outbound"), eq("quic"));
  }

  @Test
  public void subscribeConnect_multipleListeners() {
    final List<Peer> connectedPeers = new ArrayList<>();
    final List<Peer> connectedPeersB = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    peerManager.subscribeConnect(connectedPeersB::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);

    assertThat(connectedPeers).containsExactly(peer);
    assertThat(connectedPeersB).containsExactly(peer);
  }

  @Test
  public void subscribeConnect_shouldRejectConnectionThatAlreadyExists() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);

    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);
    assertThat(connectedPeers).containsExactly(peer);

    Assertions.assertThrows(
        PeerAlreadyConnectedException.class, () -> peerManager.onConnectedPeer(peer));

    assertThat(connectedPeers).containsExactly(peer);
  }

  @Test
  public void subscribeConnect_shouldRejectInboundConnectionWithBadReputation() {
    final PeerHandler peerHandler = mock(PeerHandler.class);
    final PeerManager peerManager =
        new PeerManager(
            new StubMetricsSystem(),
            reputationManager,
            List.of(peerHandler),
            Collections.emptyList(),
            peerId -> 0.0);
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);

    final Peer peer = mock(Peer.class);
    final MockNodeId nodeId = new MockNodeId(1);
    final PeerAddress peerAddress = new PeerAddress(nodeId);
    when(peer.getId()).thenReturn(nodeId);
    when(peer.getAddress()).thenReturn(peerAddress);
    when(peer.connectionInitiatedLocally()).thenReturn(false);
    when(peer.disconnectCleanly(DisconnectReason.BAD_SCORE)).thenReturn(SafeFuture.COMPLETE);
    when(reputationManager.getInboundConnectionRejectionReason(peerAddress))
        .thenReturn(Optional.of(DisconnectReason.BAD_SCORE));

    peerManager.onConnectedPeer(peer);

    final InOrder inOrder = inOrder(peerHandler, peer);
    inOrder.verify(peerHandler).onConnect(peer);
    inOrder.verify(peer).disconnectCleanly(DisconnectReason.BAD_SCORE);
    assertThat(connectedPeers).isEmpty();
  }

  @Test
  public void subscribeConnect_shouldNotRejectOutboundConnectionWithBadReputation() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);

    final Peer peer = mock(Peer.class);
    final MockNodeId nodeId = new MockNodeId(1);
    final PeerAddress peerAddress = new PeerAddress(nodeId);
    when(peer.getId()).thenReturn(nodeId);
    when(peer.getAddress()).thenReturn(peerAddress);
    when(peer.connectionInitiatedLocally()).thenReturn(true);
    when(reputationManager.getInboundConnectionRejectionReason(peerAddress))
        .thenReturn(Optional.of(DisconnectReason.BAD_SCORE));

    peerManager.onConnectedPeer(peer);

    verify(peer, never()).disconnectCleanly(DisconnectReason.BAD_SCORE);
    assertThat(connectedPeers).containsExactly(peer);
  }

  @Test
  public void shouldReportFailedConnectionsToReputationManager() {
    final Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress = new MultiaddrPeerAddress(new MockNodeId(1), multiaddr);
    when(network.connect(multiaddr))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Nope")));

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    assertThat(result).isCompletedExceptionally();

    verify(reputationManager).reportInitiatedConnectionFailed(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionSuccessful(peerAddress);
  }

  @Test
  public void shouldReportSuccessfulConnectionsToReputationManager() {
    final Connection connection = mock(Connection.class);
    final Session secureSession =
        new Session(
            PeerId.random(), PeerId.random(), EcdsaKt.generateEcdsaKeyPair().component2(), null);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(new SafeFuture<>());
    final Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress = new MultiaddrPeerAddress(new MockNodeId(1), multiaddr);
    final SafeFuture<Connection> connectionFuture = new SafeFuture<>();
    when(network.connect(multiaddr)).thenReturn(connectionFuture);

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    peerManager.handleConnection(connection);
    connectionFuture.complete(connection);
    assertThat(result).isCompleted();

    verify(reputationManager).reportInitiatedConnectionSuccessful(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionFailed(peerAddress);
  }

  @Test
  public void shouldDialFallbackAddressWhenPrimaryDialFails() {
    final Connection connection = mock(Connection.class);
    final Session secureSession =
        new Session(
            PeerId.random(), PeerId.random(), EcdsaKt.generateEcdsaKeyPair().component2(), null);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(new SafeFuture<>());
    final Multiaddr quicMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/quic-v1");
    final Multiaddr tcpMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress =
        new MultiaddrPeerAddress(new MockNodeId(1), quicMultiaddr, Optional.of(tcpMultiaddr));
    when(network.connect(quicMultiaddr))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("QUIC unreachable")));
    final SafeFuture<Connection> tcpConnectionFuture = new SafeFuture<>();
    when(network.connect(tcpMultiaddr)).thenReturn(tcpConnectionFuture);

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    verify(network).connect(tcpMultiaddr);
    peerManager.handleConnection(connection);
    tcpConnectionFuture.complete(connection);
    assertThat(result).isCompleted();

    verify(reputationManager).reportInitiatedConnectionSuccessful(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionFailed(peerAddress);
  }

  @Test
  public void shouldNotDialFallbackAddressWhilePrimaryDialIsPendingOrSucceeds() {
    final Connection connection = mock(Connection.class);
    final Session secureSession =
        new Session(
            PeerId.random(), PeerId.random(), EcdsaKt.generateEcdsaKeyPair().component2(), null);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(new SafeFuture<>());
    final Multiaddr quicMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/quic-v1");
    final Multiaddr tcpMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress =
        new MultiaddrPeerAddress(new MockNodeId(1), quicMultiaddr, Optional.of(tcpMultiaddr));
    final SafeFuture<Connection> quicConnectionFuture = new SafeFuture<>();
    when(network.connect(quicMultiaddr)).thenReturn(quicConnectionFuture);

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    assertThat(result).isNotDone();
    verify(network, never()).connect(tcpMultiaddr);

    peerManager.handleConnection(connection);
    quicConnectionFuture.complete(connection);
    assertThat(result).isCompleted();
    verify(network, never()).connect(tcpMultiaddr);
    verify(reputationManager).reportInitiatedConnectionSuccessful(peerAddress);
  }

  @Test
  public void shouldNotDialFallbackAddressWhenPeerIsAlreadyConnected() {
    final Peer existingPeer = mock(Peer.class);
    when(existingPeer.getId()).thenReturn(new MockNodeId(1));
    final Multiaddr quicMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/quic-v1");
    final Multiaddr tcpMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress =
        new MultiaddrPeerAddress(new MockNodeId(1), quicMultiaddr, Optional.of(tcpMultiaddr));
    final RuntimeException error =
        new RuntimeException(new PeerAlreadyConnectedException(existingPeer));
    when(network.connect(quicMultiaddr)).thenReturn(CompletableFuture.failedFuture(error));

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    assertThat(result).isCompletedWithValue(existingPeer);

    verify(network, never()).connect(tcpMultiaddr);
    verify(reputationManager, never()).reportInitiatedConnectionFailed(peerAddress);
  }

  @Test
  public void shouldReportFailureWhenPrimaryAndFallbackDialsFail() {
    final Multiaddr quicMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/quic-v1");
    final Multiaddr tcpMultiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress =
        new MultiaddrPeerAddress(new MockNodeId(1), quicMultiaddr, Optional.of(tcpMultiaddr));
    when(network.connect(quicMultiaddr))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("QUIC unreachable")));
    when(network.connect(tcpMultiaddr))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("TCP unreachable")));

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    assertThat(result).isCompletedExceptionally();

    verify(network).connect(quicMultiaddr);
    verify(network).connect(tcpMultiaddr);
    verify(reputationManager).reportInitiatedConnectionFailed(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionSuccessful(peerAddress);
  }

  @Test
  public void connect_shouldNotDropNewPendingConnectionWhenPreviousAttemptCompletes() {
    final Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress = new MultiaddrPeerAddress(new MockNodeId(1), multiaddr);
    final SafeFuture<Connection> firstDial = new SafeFuture<>();
    final SafeFuture<Connection> secondDial = new SafeFuture<>();
    when(network.connect(multiaddr)).thenReturn(firstDial).thenReturn(secondDial);

    // Two callers share the first pending attempt, each registering its own cleanup
    final SafeFuture<Peer> firstCaller = peerManager.connect(peerAddress, network);
    final SafeFuture<Peer> secondCaller = peerManager.connect(peerAddress, network);
    // A new attempt starts as soon as the second caller observes the failure, i.e. after the
    // second caller's cleanup ran but before the first caller's cleanup runs
    final List<SafeFuture<Peer>> laterCallers = new ArrayList<>();
    final SafeFuture<Void> thirdCaller =
        secondCaller
            .exceptionally(__ -> null)
            .thenRun(() -> laterCallers.add(peerManager.connect(peerAddress, network)));

    firstDial.completeExceptionally(new RuntimeException("Nope"));
    assertThat(firstCaller).isCompletedExceptionally();
    assertThat(thirdCaller).isCompleted();

    // The new attempt must still be pending, so another caller joins it instead of dialing again
    laterCallers.add(peerManager.connect(peerAddress, network));
    verify(network, times(2)).connect(multiaddr);
    assertThat(laterCallers).allMatch(future -> !future.isDone());
  }

  @Test
  public void testPeerDirectionAndTransportMetric() {
    // Sanity check — all four series start at zero
    validatePeerMetric("outbound", "tcp", 0);
    validatePeerMetric("outbound", "quic", 0);
    validatePeerMetric("inbound", "tcp", 0);
    validatePeerMetric("inbound", "quic", 0);

    final Peer outboundTcp = createPeer(1, true, Transport.TCP);
    peerManager.onConnectedPeer(outboundTcp);
    validatePeerMetric("outbound", "tcp", 1);

    final Peer inboundQuic = createPeer(2, false, Transport.QUIC);
    peerManager.onConnectedPeer(inboundQuic);
    validatePeerMetric("inbound", "quic", 1);

    final Peer outboundQuic = createPeer(3, true, Transport.QUIC);
    peerManager.onConnectedPeer(outboundQuic);
    validatePeerMetric("outbound", "quic", 1);

    final Peer inboundTcp = createPeer(4, false, Transport.TCP);
    peerManager.onConnectedPeer(inboundTcp);
    validatePeerMetric("inbound", "tcp", 1);

    // Final state of every series
    validatePeerMetric("outbound", "tcp", 1);
    validatePeerMetric("outbound", "quic", 1);
    validatePeerMetric("inbound", "tcp", 1);
    validatePeerMetric("inbound", "quic", 1);

    // Disconnect the outbound TCP peer
    peerManager.onDisconnectedPeer(outboundTcp, Optional.empty(), true);
    validatePeerMetric("outbound", "tcp", 0);

    // Disconnect the inbound TCP peer
    peerManager.onDisconnectedPeer(inboundTcp, Optional.empty(), true);
    validatePeerMetric("inbound", "tcp", 0);
  }

  private void validatePeerMetric(
      final String direction, final String transport, final double expected) {
    final StubLabelledGauge labelledGauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.LIBP2P, "peers_direction_current");
    assertThat(labelledGauge.getValue(direction, transport)).hasValue(expected);
  }

  private Peer createPeer(final int id, final boolean outbound, final Transport transport) {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(id));
    when(peer.connectionInitiatedLocally()).thenReturn(outbound);
    when(peer.connectionInitiatedRemotely()).thenReturn(!outbound);
    when(peer.getTransport()).thenReturn(transport);
    return peer;
  }
}

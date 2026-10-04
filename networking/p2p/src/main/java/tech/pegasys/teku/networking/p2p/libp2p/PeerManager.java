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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.Network;
import io.libp2p.core.PeerId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.networking.p2p.peer.Transport;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class PeerManager implements ConnectionHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final List<? extends RpcHandler<?, ?, ?>> rpcHandlers;

  private final Function<PeerId, Double> peerScoreFunction;

  private final ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<NodeId, SafeFuture<Peer>> pendingConnections =
      new ConcurrentHashMap<>();
  private final ReputationManager reputationManager;
  private final List<PeerHandler> peerHandlers;

  private final Subscribers<PeerConnectedSubscriber<Peer>> connectSubscribers =
      Subscribers.create(true);

  public PeerManager(
      final MetricsSystem metricsSystem,
      final ReputationManager reputationManager,
      final List<PeerHandler> peerHandlers,
      final List<? extends RpcHandler<?, ?, ?>> rpcHandlers,
      final Function<PeerId, Double> peerScoreFunction) {
    this.reputationManager = reputationManager;
    this.peerHandlers = peerHandlers;
    this.rpcHandlers = rpcHandlers;
    this.peerScoreFunction = peerScoreFunction;
    metricsSystem.createGauge(
        TekuMetricCategory.LIBP2P, "peers", "Tracks number of libp2p peers", this::getPeerCount);
    final LabelledSuppliedMetric peerClientLabelledGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P,
            "connected_peers_current",
            "The number of clients connected by client type",
            "client");

    for (PeerClientType type : PeerClientType.values()) {
      peerClientLabelledGauge.labels(() -> countConnectedPeersOfType(type), type.getDisplayName());
    }

    final LabelledSuppliedMetric peerDirectionLabelledGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P,
            "peers_direction_current",
            "The number of peers by direction and transport",
            "direction",
            "transport");
    registerPeersByDirectionAndTransport(
        peerDirectionLabelledGauge, "inbound", Peer::connectionInitiatedRemotely);
    registerPeersByDirectionAndTransport(
        peerDirectionLabelledGauge, "outbound", Peer::connectionInitiatedLocally);
  }

  private void registerPeersByDirectionAndTransport(
      final LabelledSuppliedMetric gauge,
      final String direction,
      final Predicate<Peer> directionPredicate) {
    for (final Transport transport : List.of(Transport.TCP, Transport.QUIC)) {
      gauge.labels(
          () ->
              connectedPeerMap.values().stream()
                  .filter(directionPredicate)
                  .filter(peer -> peer.getTransport() == transport)
                  .count(),
          direction,
          transport.getLabel());
    }
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    final Peer peer = new LibP2PPeer(connection, rpcHandlers, reputationManager, peerScoreFunction);
    onConnectedPeer(peer);
  }

  double countConnectedPeersOfType(final PeerClientType type) {
    return connectedPeerMap.values().stream()
        .filter(Peer::isConnected)
        .filter(peer -> type.equals(peer.getPeerClientType()))
        .count();
  }

  public long subscribeConnect(final PeerConnectedSubscriber<Peer> subscriber) {
    return connectSubscribers.subscribe(subscriber);
  }

  public void unsubscribeConnect(final long subscriptionId) {
    connectSubscribers.unsubscribe(subscriptionId);
  }

  public SafeFuture<Peer> connect(final MultiaddrPeerAddress peer, final Network network) {
    return pendingConnections
        .computeIfAbsent(peer.getId(), __ -> doConnect(peer, network))
        .whenComplete((result, error) -> pendingConnections.remove(peer.getId()));
  }

  private SafeFuture<Peer> doConnect(final MultiaddrPeerAddress peer, final Network network) {
    LOG.debug("Connecting to {}", peer);

    return SafeFuture.of(() -> network.connect(peer.getMultiaddr()))
        .exceptionallyCompose(
            error ->
                peer.getFallbackMultiaddr()
                    // A duplicate connection means the peer is already connected, nothing to retry
                    .filter(__ -> !isPeerAlreadyConnected(error))
                    .map(
                        fallbackMultiaddr -> {
                          LOG.debug(
                              "Failed to connect to {} ({}), retrying via {}",
                              peer,
                              Throwables.getRootCause(error).getMessage(),
                              fallbackMultiaddr);
                          return SafeFuture.of(() -> network.connect(fallbackMultiaddr));
                        })
                    .orElseGet(() -> SafeFuture.failedFuture(error)))
        .thenApply(
            connection -> {
              final LibP2PNodeId nodeId =
                  new LibP2PNodeId(connection.secureSession().getRemoteId());
              final Peer connectedPeer = connectedPeerMap.get(nodeId);
              if (connectedPeer == null) {
                if (connection.closeFuture().isDone()) {
                  // Connection has been immediately closed and the peer already removed
                  // Since the connection is closed anyway, we can create a new peer to wrap it.
                  return new LibP2PPeer(
                      connection, rpcHandlers, reputationManager, peerScoreFunction);
                } else {
                  // Theoretically this should never happen because removing from the map is done
                  // by the close future completing, but make a loud noise just in case.
                  throw new IllegalStateException(
                      "No peer registered for established connection to " + nodeId);
                }
              }
              reputationManager.reportInitiatedConnectionSuccessful(peer);
              return connectedPeer;
            })
        .exceptionallyCompose(this::handleConcurrentConnectionInitiation)
        .catchAndRethrow(error -> reputationManager.reportInitiatedConnectionFailed(peer));
  }

  private CompletionStage<Peer> handleConcurrentConnectionInitiation(final Throwable error) {
    return isPeerAlreadyConnected(error)
        ? SafeFuture.completedFuture(
            ((PeerAlreadyConnectedException) Throwables.getRootCause(error)).getPeer())
        : SafeFuture.failedFuture(error);
  }

  private static boolean isPeerAlreadyConnected(final Throwable error) {
    return Throwables.getRootCause(error) instanceof PeerAlreadyConnectedException;
  }

  public Optional<Peer> getPeer(final NodeId id) {
    return Optional.ofNullable(connectedPeerMap.get(id));
  }

  @VisibleForTesting
  void onConnectedPeer(final Peer peer) {
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getId(), peer) == null;
    if (wasAdded) {
      LOG.debug("onConnectedPeer() {}", peer.getId());
      peer.subscribeDisconnect(
          (reason, locallyInitiated) -> onDisconnectedPeer(peer, reason, locallyInitiated));
      // Eth2PeerManager installs the clean-disconnect handler used to send goodbye messages.
      peerHandlers.forEach(h -> h.onConnect(peer));
      if (rejectUnsuitableInboundConnection(peer)) {
        return;
      }
      connectSubscribers.forEach(c -> c.onConnected(peer));
    } else {
      LOG.trace("Disconnecting duplicate connection to {}", peer::getId);
      peer.disconnectImmediately(Optional.of(DisconnectReason.DUPLICATE_CONNECTION), true);
      throw new PeerAlreadyConnectedException(peer);
    }
  }

  @VisibleForTesting
  void onDisconnectedPeer(
      final Peer peer, final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    if (connectedPeerMap.remove(peer.getId()) != null) {
      LOG.debug("Peer disconnected: {}", peer.getId());
      reputationManager.reportDisconnection(peer.getAddress(), reason, locallyInitiated);
      peerHandlers.forEach(h -> h.onDisconnect(peer));
    }
  }

  private boolean rejectUnsuitableInboundConnection(final Peer peer) {
    if (peer.connectionInitiatedLocally()) {
      return false;
    }
    return reputationManager
        .getInboundConnectionRejectionReason(peer.getAddress())
        .map(
            reason -> {
              LOG.debug("Rejecting inbound connection from {} because {}", peer.getId(), reason);
              peer.disconnectCleanly(reason).finishTrace(LOG);
              return true;
            })
        .orElse(false);
  }

  public Stream<Peer> streamPeers() {
    return connectedPeerMap.values().stream();
  }

  public int getPeerCount() {
    return connectedPeerMap.size();
  }
}

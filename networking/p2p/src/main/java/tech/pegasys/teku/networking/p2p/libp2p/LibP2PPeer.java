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

package tech.pegasys.teku.networking.p2p.libp2p;

import static tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.DEFAULT_MAXIMUM_QUEUE_SIZE;

import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Multihash;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.IdentifyController;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.networking.p2p.peer.Transport;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;

public class LibP2PPeer implements Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<RpcMethod<?, ?, ?>, ThrottlingRpcHandler<?, ?, ?>> rpcHandlers;
  private final ReputationManager reputationManager;
  private final Function<PeerId, Double> peerScoreFunction;
  private final Connection connection;
  private final AtomicBoolean connected = new AtomicBoolean(true);
  private final MultiaddrPeerAddress peerAddress;
  private final Transport transport;
  private final PeerId peerId;
  private final PubKey pubKey;
  private volatile PeerClientType peerClientType = PeerClientType.UNKNOWN;
  private volatile Optional<String> maybeAgentString = Optional.empty();

  private volatile Optional<DisconnectReason> disconnectReason = Optional.empty();
  private volatile boolean disconnectLocallyInitiated = false;
  private volatile DisconnectRequestHandler disconnectRequestHandler =
      reason -> {
        disconnectImmediately(Optional.of(reason), true);
        return SafeFuture.COMPLETE;
      };

  public LibP2PPeer(
      final Connection connection,
      final List<? extends RpcHandler<?, ?, ?>> rpcHandlers,
      final ReputationManager reputationManager,
      final Function<PeerId, Double> peerScoreFunction) {
    this.connection = connection;
    this.rpcHandlers =
        rpcHandlers.stream()
            .collect(Collectors.toMap(RpcHandler::getRpcMethod, ThrottlingRpcHandler::new));
    this.reputationManager = reputationManager;
    this.peerScoreFunction = peerScoreFunction;
    this.peerId = connection.secureSession().getRemoteId();
    // Prefer the libp2p identity key derived from the peer id. For Noise (TCP) the secure session
    // already exposes the identity key, but for QUIC the libp2p-TLS session reports the ephemeral
    // certificate key (ECDSA) instead. Downstream consumers (e.g. discovery node id derivation)
    // require the secp256k1 identity key, which is recoverable from the peer id whenever it inlines
    // the key (the case for secp256k1 and Ed25519 identities).
    this.pubKey =
        extractIdentityPublicKey(peerId)
            .orElseGet(() -> connection.secureSession().getRemotePubKey());

    final NodeId nodeId = new LibP2PNodeId(peerId);
    final Multiaddr remoteAddress = connection.remoteAddress();
    peerAddress = new MultiaddrPeerAddress(nodeId, remoteAddress);
    this.transport = transportFromMultiaddr(remoteAddress);
    if (remoteAddress != null) {
      LOG.debug("Connected to peer {} via {}", nodeId, remoteAddress);
    }
    SafeFuture.of(connection.closeFuture())
        .finish(
            this::handleConnectionClosed,
            error ->
                LOG.trace(
                    "Peer {} connection close future completed exceptionally", peerId, error));
  }

  @Override
  public void checkPeerIdentity() {
    if (maybeAgentString.isPresent()) {
      return;
    }
    getAgentVersionFromIdentity()
        .thenAccept(
            maybeAgent -> {
              maybeAgentString = maybeAgent;
              maybeAgent.ifPresent(s -> peerClientType = getPeerTypeFromAgentString(s));
            })
        .finish(error -> LOG.debug("Failed to retrieve client identity", error));
  }

  private PeerClientType getPeerTypeFromAgentString(final String agentVersion) {
    String agent = agentVersion;
    if (agentVersion.contains("/")) {
      agent = agentVersion.substring(0, agentVersion.indexOf("/"));
    }
    return EnumUtils.getEnumIgnoreCase(PeerClientType.class, agent, PeerClientType.UNKNOWN);
  }

  public PubKey getPubKey() {
    return pubKey;
  }

  /**
   * Recovers the libp2p identity public key embedded in the peer id. Peer ids inline the identity
   * key (as an identity multihash) for small key types such as secp256k1 and Ed25519; larger keys
   * (e.g. RSA) are hashed and cannot be recovered, in which case an empty result is returned.
   */
  private static Optional<PubKey> extractIdentityPublicKey(final PeerId peerId) {
    try {
      final Multihash multihash = Multihash.Companion.of(Unpooled.wrappedBuffer(peerId.getBytes()));
      if (multihash.getDesc().getDigest() != Multihash.Digest.Identity) {
        return Optional.empty();
      }
      final byte[] marshalledKey = ByteBufUtil.getBytes(multihash.getValue());
      return Optional.of(KeyKt.unmarshalPublicKey(marshalledKey));
    } catch (final Exception e) {
      LOG.debug("Failed to recover identity public key from peer id {}", peerId, e);
      return Optional.empty();
    }
  }

  private static Transport transportFromMultiaddr(final Multiaddr multiaddr) {
    if (multiaddr == null) {
      return Transport.UNKNOWN;
    }
    if (multiaddr.hasAny(Protocol.QUICV1, Protocol.QUIC)) {
      return Transport.QUIC;
    }
    if (multiaddr.has(Protocol.TCP)) {
      return Transport.TCP;
    }
    return Transport.UNKNOWN;
  }

  @Override
  public PeerAddress getAddress() {
    return peerAddress;
  }

  @Override
  public Double getGossipScore() {
    return peerScoreFunction.apply(peerId);
  }

  @Override
  public Optional<String> getAgentVersion() {
    return maybeAgentString;
  }

  @Override
  public boolean isConnected() {
    return connected.get();
  }

  @Override
  public PeerClientType getPeerClientType() {
    return peerClientType;
  }

  @Override
  public Transport getTransport() {
    return transport;
  }

  @Override
  public void disconnectImmediately(
      final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    if (connected.getAndSet(false)) {
      internalDisconnectImmediately(reason, locallyInitiated);
    }
  }

  private void internalDisconnectImmediately(
      final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    // capture the remote address before closing the connection as it may no longer be available
    // afterwards (e.g. for QUIC connections)
    final Multiaddr remoteAddress = connection.remoteAddress();
    disconnectReason = reason;
    disconnectLocallyInitiated = locallyInitiated;
    SafeFuture.of(connection.close())
        .finish(
            () ->
                LOG.trace(
                    "Disconnected forcibly {} because {} from {}",
                    locallyInitiated ? "locally" : "remotely",
                    reason,
                    remoteAddress),
            error -> LOG.warn("Failed to disconnect from peer {}", getId(), error));
  }

  private SafeFuture<Optional<String>> getAgentVersionFromIdentity() {
    return getIdentify()
        .thenApply(
            id -> id.hasAgentVersion() ? Optional.of(id.getAgentVersion()) : Optional.empty());
  }

  private SafeFuture<IdentifyOuterClass.Identify> getIdentify() {
    return SafeFuture.of(
            connection
                .muxerSession()
                .createStream(new Identify())
                .getController()
                .thenCompose(IdentifyController::id))
        .exceptionallyCompose(
            error -> {
              LOG.debug("Failed to get peer identity", error);
              return SafeFuture.failedFuture(error);
            });
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    if (connected.getAndSet(false)) {
      LOG.trace("Disconnecting cleanly because {} from {}", reason, connection.remoteAddress());
      return disconnectRequestHandler
          .requestDisconnect(reason)
          .handle(
              (__, error) -> {
                if (error != null) {
                  LOG.debug("Failed to disconnect from {} cleanly.", getId(), error);
                }
                internalDisconnectImmediately(Optional.of(reason), true);
                return null;
              });
    } else {
      return SafeFuture.COMPLETE;
    }
  }

  @Override
  public void setDisconnectRequestHandler(final DisconnectRequestHandler handler) {
    this.disconnectRequestHandler = handler;
  }

  @Override
  public void subscribeDisconnect(final PeerDisconnectedSubscriber subscriber) {
    SafeFuture.of(connection.closeFuture())
        .always(() -> subscriber.onDisconnected(disconnectReason, disconnectLocallyInitiated));
  }

  @Override
  public <
          TOutgoingHandler extends RpcRequestHandler,
          TRequest extends RpcRequest,
          RespHandler extends RpcResponseHandler<?>>
      SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
          final RpcMethod<TOutgoingHandler, TRequest, RespHandler> rpcMethod,
          final RpcRequestBodySelector<TRequest> rpcRequestBodySelector,
          final RespHandler responseHandler) {
    @SuppressWarnings("unchecked")
    final ThrottlingRpcHandler<TOutgoingHandler, TRequest, RespHandler> rpcHandler =
        (ThrottlingRpcHandler<TOutgoingHandler, TRequest, RespHandler>) rpcHandlers.get(rpcMethod);
    if (rpcHandler == null) {
      throw new IllegalArgumentException(
          "Unknown rpc method invoked: " + String.join(",", rpcMethod.getIds()));
    }

    return rpcHandler.sendRequestWithBodySelector(
        connection, rpcRequestBodySelector, responseHandler);
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return connection.isInitiator();
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return !connectionInitiatedLocally();
  }

  private void handleConnectionClosed() {
    LOG.debug("Disconnected from peer {}", getId());
    connected.set(false);
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {
    final boolean shouldDisconnect = reputationManager.adjustReputation(getAddress(), adjustment);
    if (shouldDisconnect) {
      disconnectCleanly(DisconnectReason.BAD_SCORE).finishError(LOG);
    }
  }

  private static class ThrottlingRpcHandler<
      TOutgoingHandler extends RpcRequestHandler,
      TRequest extends RpcRequest,
      TRespHandler extends RpcResponseHandler<?>> {

    private final RpcHandler<TOutgoingHandler, TRequest, TRespHandler> delegate;

    private final ThrottlingTaskQueue requestsQueue =
        ThrottlingTaskQueue.create(
            NetworkConstants.MAX_CONCURRENT_REQUESTS, DEFAULT_MAXIMUM_QUEUE_SIZE);

    private ThrottlingRpcHandler(
        final RpcHandler<TOutgoingHandler, TRequest, TRespHandler> delegate) {
      this.delegate = delegate;
    }

    private SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequestWithBodySelector(
        final Connection connection,
        final RpcRequestBodySelector<TRequest> rpcRequestBodySelector,
        final TRespHandler responseHandler) {
      return requestsQueue.queueTask(
          () ->
              delegate.sendRequestWithBodySelector(
                  connection, rpcRequestBodySelector, responseHandler));
    }
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.LARGE_PENALTY;

import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.security.SecureChannel.Session;
import io.libp2p.crypto.keys.EcdsaKt;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.libp2p.protocol.IdentifyController;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import kotlin.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Transport;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;

public class LibP2PPeerTest {
  public static final String PEER_ID = "16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5";
  private final Connection connection = mock(Connection.class);

  @SuppressWarnings("unchecked")
  private final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<Void>> rpcHandler =
      mock(RpcHandler.class);

  @SuppressWarnings("unchecked")
  private final RpcMethod<RpcRequestHandler, RpcRequest, RpcResponseHandler<Void>> rpcMethod =
      mock(RpcMethod.class);

  private LibP2PPeer libP2PPeer;

  private final SafeFuture<Unit> closeFuture = new SafeFuture<>();

  @BeforeEach
  public void init() {
    when(rpcHandler.getRpcMethod()).thenReturn(rpcMethod);
    final Session secureSession = mock(Session.class);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(closeFuture);
    when(connection.remoteAddress())
        .thenReturn(Multiaddr.fromString("/ip4/123.34.58.22/tcp/5883/"));
    when(secureSession.getRemoteId()).thenReturn(PeerId.fromBase58(PEER_ID));
    libP2PPeer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, peer -> 0.0);
  }

  @Test
  public void getPubKey_recoversIdentityKeyFromPeerIdWhenSecureSessionExposesDifferentKey() {
    // Simulates a QUIC connection: the libp2p-TLS secure session reports the ephemeral
    // certificate key (ECDSA) as the remote public key, while the verified peer identity (and
    // hence the peer id) is derived from the node's secp256k1 identity key. The discovery node id
    // must be derived from the secp256k1 identity key, so getPubKey() must return that key rather
    // than the certificate key.
    final PubKey identityKey = Secp256k1Kt.generateSecp256k1KeyPair().getSecond();
    final PubKey certificateKey = EcdsaKt.generateEcdsaKeyPair().getSecond();

    final Session secureSession = mock(Session.class);
    when(connection.secureSession()).thenReturn(secureSession);
    when(secureSession.getRemoteId()).thenReturn(PeerId.fromPubKey(identityKey));
    when(secureSession.getRemotePubKey()).thenReturn(certificateKey);

    final LibP2PPeer peer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, p -> 0.0);

    assertThat(peer.getPubKey().raw()).isEqualTo(identityKey.raw());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void checkPeerIdentity_shouldExposeAgentVersion() {
    final io.libp2p.core.mux.StreamMuxer.Session muxerSession =
        mock(io.libp2p.core.mux.StreamMuxer.Session.class);
    final IdentifyController identifyController = mock(IdentifyController.class);
    final StreamPromise<IdentifyController> streamPromise =
        new StreamPromise<>(new CompletableFuture<>(), new CompletableFuture<>());
    final IdentifyOuterClass.Identify identify =
        IdentifyOuterClass.Identify.newBuilder().setAgentVersion("Lighthouse/v8.1.3").build();
    when(connection.muxerSession()).thenReturn(muxerSession);
    when(muxerSession.createStream((ProtocolBinding<IdentifyController>) any()))
        .thenReturn(streamPromise);
    when(identifyController.id()).thenReturn(CompletableFuture.completedFuture(identify));

    libP2PPeer.checkPeerIdentity();
    streamPromise.getController().complete(identifyController);

    assertThat(libP2PPeer.getAgentVersion()).contains("Lighthouse/v8.1.3");
  }

  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored", "rawtypes"})
  @Test
  public void sendRequest_throttlesRequests() {

    // fill the queue with incomplete futures
    final List<SafeFuture<RpcStreamController<RpcRequestHandler>>> queuedFutures =
        IntStream.range(0, NetworkConstants.MAX_CONCURRENT_REQUESTS)
            .mapToObj(
                __ -> {
                  final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
                      new SafeFuture<>();
                  when(rpcHandler.sendRequestWithBodySelector(eq(connection), any(), any()))
                      .thenReturn(future);
                  libP2PPeer.sendRequest(rpcMethod, (RpcRequestBodySelector) null, null);
                  return future;
                })
            .toList();

    when(rpcHandler.sendRequest(connection, null, null))
        .thenReturn(SafeFuture.completedFuture(mock(RpcStreamController.class)));

    final SafeFuture<RpcStreamController<RpcRequestHandler>> throttledRequest =
        libP2PPeer.sendRequest(rpcMethod, (RpcRequestBodySelector) null, null);

    // completed request should be throttled
    assertThat(throttledRequest).isNotDone();

    // empty the queue
    queuedFutures.forEach(future -> future.complete(mock(RpcStreamController.class)));

    // throttled request should have completed now
    assertThat(throttledRequest).isDone();
  }

  @Test
  public void disconnectCleanly_shouldCloseConnectionOnlyOnce() {
    final AtomicReference<Optional<DisconnectReason>> disconnectionReason = new AtomicReference<>();
    final AtomicBoolean disconnectionLocallyInitiated = new AtomicBoolean();
    final AtomicInteger disconnectionCount = new AtomicInteger(0);

    libP2PPeer.subscribeDisconnect(
        (reason, locallyInitiated) -> {
          disconnectionReason.set(reason);
          disconnectionLocallyInitiated.set(locallyInitiated);
          disconnectionCount.addAndGet(1);
        });

    libP2PPeer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).finish(__ -> {});
    verify(connection).close();

    libP2PPeer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).finish(__ -> {});
    libP2PPeer.disconnectImmediately(Optional.of(DisconnectReason.REMOTE_FAULT), false);

    verify(connection, times(1)).close();

    assertThat(disconnectionCount.get()).isEqualTo(0);

    closeFuture.complete(null);

    assertThat(disconnectionReason.get()).contains(DisconnectReason.IRRELEVANT_NETWORK);
    assertThat(disconnectionLocallyInitiated.get()).isTrue();
    assertThat(disconnectionCount.get()).isEqualTo(1);
  }

  @Test
  public void adjustReputation_shouldDisconnectWithBadScoreWhenThresholdIsReached() {
    final ReputationManager reputationManager = mock(ReputationManager.class);
    final LibP2PPeer peer =
        new LibP2PPeer(connection, List.of(rpcHandler), reputationManager, p -> 0.0);
    final AtomicReference<DisconnectReason> disconnectReason = new AtomicReference<>();
    peer.setDisconnectRequestHandler(
        reason -> {
          disconnectReason.set(reason);
          return SafeFuture.COMPLETE;
        });
    when(reputationManager.adjustReputation(any(PeerAddress.class), eq(LARGE_PENALTY)))
        .thenReturn(true);

    peer.adjustReputation(LARGE_PENALTY);

    assertThat(disconnectReason).hasValue(DisconnectReason.BAD_SCORE);
  }

  @Test
  public void getTransport_returnsTcpForTcpMultiaddr() {
    assertThat(libP2PPeer.getTransport()).isEqualTo(Transport.TCP);
  }

  @Test
  public void getTransport_returnsQuicForQuicMultiaddr() {
    when(connection.remoteAddress())
        .thenReturn(Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/quic-v1/"));
    final LibP2PPeer peer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, p -> 0.0);
    assertThat(peer.getTransport()).isEqualTo(Transport.QUIC);
  }

  @Test
  public void getTransport_returnsUnknownForNonTcpQuicMultiaddr() {
    when(connection.remoteAddress()).thenReturn(Multiaddr.fromString("/ip4/127.0.0.1/udp/9000/"));
    final LibP2PPeer peer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, p -> 0.0);
    assertThat(peer.getTransport()).isEqualTo(Transport.UNKNOWN);
  }
}

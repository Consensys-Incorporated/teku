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

package tech.pegasys.teku.network.p2p.peer;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;

public class StubPeer implements Peer {

  private final PeerAddress peerAddress;
  private final Subscribers<PeerDisconnectedSubscriber> disconnectedSubscribers =
      Subscribers.create(false);
  private boolean connected = true;
  private Optional<DisconnectReason> disconnectReason = Optional.empty();

  public StubPeer() {
    this(new MockNodeId());
  }

  public StubPeer(final int id) {
    this(new MockNodeId(id));
  }

  public StubPeer(final NodeId nodeId) {
    peerAddress = new PeerAddress(nodeId);
  }

  @Override
  public PeerAddress getAddress() {
    return peerAddress;
  }

  @Override
  public Double getGossipScore() {
    return 0d;
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public void disconnectImmediately(
      final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    disconnectedSubscribers.forEach(
        subscriber -> subscriber.onDisconnected(reason, locallyInitiated));
    connected = false;
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    disconnectReason = Optional.of(reason);
    disconnectedSubscribers.forEach(
        subscriber -> subscriber.onDisconnected(Optional.of(reason), true));
    connected = false;
    return SafeFuture.COMPLETE;
  }

  public Optional<DisconnectReason> getDisconnectReason() {
    return disconnectReason;
  }

  @Override
  public void setDisconnectRequestHandler(final DisconnectRequestHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void subscribeDisconnect(final PeerDisconnectedSubscriber subscriber) {
    disconnectedSubscribers.subscribe(subscriber);
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
    return null;
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return true;
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return false;
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {}

  @Override
  public String toString() {
    return "StubPeer(" + peerAddress.getId().toBase58() + ")";
  }
}

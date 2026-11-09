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

package tech.pegasys.teku.networking.p2p.peer;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.PeerClientType;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.SingleRpcRequestBodySelector;

public interface Peer {

  default NodeId getId() {
    return getAddress().getId();
  }

  PeerAddress getAddress();

  Double getGossipScore();

  default Optional<String> getAgentVersion() {
    return Optional.empty();
  }

  boolean isConnected();

  default PeerClientType getPeerClientType() {
    return PeerClientType.UNKNOWN;
  }

  default Transport getTransport() {
    return Transport.UNKNOWN;
  }

  default void checkPeerIdentity() {}

  void disconnectImmediately(Optional<DisconnectReason> reason, boolean locallyInitiated);

  SafeFuture<Void> disconnectCleanly(DisconnectReason reason);

  void setDisconnectRequestHandler(DisconnectRequestHandler handler);

  void subscribeDisconnect(PeerDisconnectedSubscriber subscriber);

  default <
          TOutgoingHandler extends RpcRequestHandler,
          TRequest extends RpcRequest,
          RespHandler extends RpcResponseHandler<?>>
      SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
          final RpcMethod<TOutgoingHandler, TRequest, RespHandler> rpcMethod,
          final TRequest request,
          final RespHandler responseHandler) {
    return sendRequest(rpcMethod, new SingleRpcRequestBodySelector<>(request), responseHandler);
  }

  <
          TOutgoingHandler extends RpcRequestHandler,
          TRequest extends RpcRequest,
          RespHandler extends RpcResponseHandler<?>>
      SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
          final RpcMethod<TOutgoingHandler, TRequest, RespHandler> rpcMethod,
          final RpcRequestBodySelector<TRequest> rpcRequestBodySelector,
          final RespHandler responseHandler);

  boolean connectionInitiatedLocally();

  boolean connectionInitiatedRemotely();

  default boolean idMatches(final Peer other) {
    return other != null && Objects.equals(getId(), other.getId());
  }

  void adjustReputation(final ReputationAdjustment adjustment);
}

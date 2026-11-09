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

package tech.pegasys.teku.networking.eth2.rpc.core.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds.getMethodId;

import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2IncomingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public class SingleProtocolEth2RpcMethod<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    extends AbstractEth2RpcMethod<TRequest, TResponse> {

  private final AsyncRunner asyncRunner;

  private final String protocolId;
  private final int protocolVersion;
  private final RpcResponseEncoder<TResponse, ?> responseEncoder;
  private final RpcContextCodec<?, TResponse> contextCodec;

  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final PeerLookup peerLookup;

  public SingleProtocolEth2RpcMethod(
      final AsyncRunner asyncRunner,
      final String protocolIdPrefix,
      final int protocolVersion,
      final RpcEncoding encoding,
      final SszSchema<TRequest> requestType,
      final boolean expectResponseToRequest,
      final RpcContextCodec<?, TResponse> contextCodec,
      final LocalMessageHandler<TRequest, TResponse> localMessageHandler,
      final PeerLookup peerLookup) {
    super(encoding, requestType, expectResponseToRequest);
    this.asyncRunner = asyncRunner;
    this.contextCodec = contextCodec;
    this.responseEncoder = new RpcResponseEncoder<>(encoding, contextCodec);
    this.protocolId = getMethodId(protocolIdPrefix, protocolVersion, encoding);
    this.protocolVersion = protocolVersion;
    this.localMessageHandler = localMessageHandler;
    this.peerLookup = peerLookup;
  }

  @Override
  public List<String> getIds() {
    return List.of(protocolId);
  }

  public String getId() {
    return protocolId;
  }

  public int getProtocolVersion() {
    return protocolVersion;
  }

  @Override
  public Eth2IncomingRequestHandler<TRequest, TResponse> createIncomingRequestHandler(
      final String protocolId) {
    return new Eth2IncomingRequestHandler<>(
        protocolId,
        responseEncoder,
        createRequestDecoder(),
        asyncRunner,
        peerLookup,
        localMessageHandler);
  }

  @Override
  public Eth2OutgoingRequestHandler<TRequest, TResponse> createOutgoingRequestHandler(
      final String protocolId,
      final TRequest request,
      final Eth2RpcResponseHandler<TResponse, ?> responseHandler) {
    return new Eth2OutgoingRequestHandler<>(
        asyncRunner,
        asyncRunner,
        protocolId,
        createResponseDecoder(),
        expectResponseToRequest,
        request,
        responseHandler);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SingleProtocolEth2RpcMethod<?, ?> rpcMethod = (SingleProtocolEth2RpcMethod<?, ?>) o;
    return protocolId.equals(rpcMethod.protocolId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(protocolId);
  }

  @Override
  public String toString() {
    return "Eth2RpcMethod{" + "id='" + protocolId + '\'' + '}';
  }

  private RpcResponseDecoder<TResponse, ?> createResponseDecoder() {
    return RpcResponseDecoder.create(encoding, contextCodec);
  }
}

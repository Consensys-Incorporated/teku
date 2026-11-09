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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcRequestDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcRequestEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public abstract class AbstractEth2RpcMethod<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    implements Eth2RpcMethod<TRequest, TResponse> {
  protected final RpcEncoding encoding;
  protected final SszSchema<TRequest> requestType;
  protected final boolean expectResponseToRequest;
  protected final RpcRequestEncoder requestEncoder;

  protected AbstractEth2RpcMethod(
      final RpcEncoding encoding,
      final SszSchema<TRequest> requestType,
      final boolean expectResponseToRequest) {
    this.encoding = encoding;
    this.requestType = requestType;
    this.expectResponseToRequest = expectResponseToRequest;
    this.requestEncoder = new RpcRequestEncoder(encoding);
  }

  @Override
  public Bytes encodeRequest(final TRequest request) {
    return requestEncoder.encodeRequest(request);
  }

  @Override
  public RpcRequestDecoder<TRequest> createRequestDecoder() {
    return new RpcRequestDecoder<>(requestType, encoding);
  }

  @Override
  public boolean shouldReceiveResponse() {
    return expectResponseToRequest;
  }
}

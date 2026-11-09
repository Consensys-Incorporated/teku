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

package tech.pegasys.teku.networking.eth2.rpc.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

/**
 * A decoder responsible for handling a single rpc request
 *
 * @param <T> The type of request to expect
 */
public final class RpcRequestDecoder<T extends RpcRequest & SszData> {
  private final RpcByteBufDecoder<T> decoder;
  private boolean complete;

  public RpcRequestDecoder(final SszSchema<T> requestType, final RpcEncoding encoding) {
    this.decoder = encoding.createDecoder(requestType);
  }

  public Optional<T> decodeRequest(final ByteBuf input) throws RpcException {
    if (complete) {
      if (input.isReadable()) {
        throw new ExtraDataAppendedException();
      } else {
        return Optional.empty();
      }
    }
    final Optional<T> request = decoder.decodeOneMessage(input);

    if (request.isPresent()) {
      // Check for extra bytes remaining
      if (input.readableBytes() != 0) {
        throw new ExtraDataAppendedException();
      }
      complete = true;
    }

    return request;
  }

  public Optional<T> complete() throws RpcException {
    Optional<T> maybeRequest = Optional.empty();
    if (!complete) {
      // complete() might be the only event on empty request
      // so we might need to produce a message with decodeRequest(EMPTY_BUFFER)
      maybeRequest = decodeRequest(Unpooled.EMPTY_BUFFER);
    }
    decoder.complete();
    if (!complete) {
      throw new PayloadTruncatedException();
    }
    return maybeRequest;
  }

  public void close() {
    decoder.close();
  }
}

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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SUCCESS_RESPONSE_CODE;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;

public final class RpcResponseEncoder<TPayload extends SszData, TContext> {
  private final RpcEncoding encoding;
  private final RpcContextCodec<TContext, TPayload> contextCodec;

  public RpcResponseEncoder(
      final RpcEncoding encoding, final RpcContextCodec<TContext, TPayload> contextCodec) {
    this.encoding = encoding;
    this.contextCodec = contextCodec;
  }

  public Bytes encodeSuccessfulResponse(final TPayload response) {
    final Bytes context = contextCodec.encodeContext(response);
    return Bytes.concatenate(
        Bytes.of(SUCCESS_RESPONSE_CODE), context, encoding.encodePayload(response));
  }

  public Bytes encodeErrorResponse(final RpcException error) {
    return Bytes.concatenate(
        Bytes.of(error.getResponseCode()), encoding.encodePayload(error.getErrorMessage()));
  }
}

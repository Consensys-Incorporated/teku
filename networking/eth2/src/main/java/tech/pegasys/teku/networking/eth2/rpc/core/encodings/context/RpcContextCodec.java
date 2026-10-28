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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;

public interface RpcContextCodec<TContext, TPayload extends SszData> {

  static <T extends SszData> RpcContextCodec<Bytes, T> noop(final SszSchema<T> schema) {
    return new NoopRpcContextCodec<T>(schema);
  }

  static <T extends SszData> RpcContextCodec<Bytes4, T> forkDigest(
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkDigestPayloadContext<T> payloadContext) {
    return new ForkDigestRpcContextCodec<T>(spec, recentChainData, payloadContext);
  }

  RpcByteBufDecoder<TContext> getContextDecoder();

  Bytes encodeContext(TPayload responsePayload);

  Optional<SszSchema<TPayload>> getSchemaFromContext(final TContext context);

  /** Throws if a fully decoded payload is inconsistent with the context bytes it arrived with. */
  void verifyPayloadMatchesContext(TContext context, TPayload responsePayload) throws RpcException;
}

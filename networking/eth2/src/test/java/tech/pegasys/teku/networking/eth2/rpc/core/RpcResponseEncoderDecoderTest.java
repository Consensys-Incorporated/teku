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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcErrorMessage;

public class RpcResponseEncoderDecoderTest extends RpcDecoderTestBase {
  private static final Bytes ERROR_CODE = Bytes.of(1);
  private final RpcContextCodec<?, RpcErrorMessage> contextCodec =
      RpcContextCodec.noop(RpcErrorMessage.SSZ_SCHEMA);
  private final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());
  private final RpcResponseEncoder<RpcErrorMessage, ?> errorMessageResponseEncoder =
      new RpcResponseEncoder<>(rpcEncoding, contextCodec);
  private final RpcResponseDecoder<RpcErrorMessage, ?> errorMessageResponseDecoder =
      RpcResponseDecoder.create(rpcEncoding, contextCodec);

  @Test
  public void shouldEncodeErrorResponse() {
    final RpcException ex = new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE);
    final Bytes actual = errorMessageResponseEncoder.encodeErrorResponse(ex);

    // sanity check that the encoded string is what we expect
    assertThat(actual.toHexString().toLowerCase(Locale.ROOT))
        .isEqualTo("0x010bff060000734e61507059010f0000c839768d4261642072657175657374");

    // when we then decode the byte stream, the same RpcException that got encoded is raised
    assertThatThrownBy(
            () -> {
              for (Iterable<ByteBuf> testByteBufSlice : testByteBufSlices(actual)) {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  errorMessageResponseDecoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                errorMessageResponseDecoder.complete();
              }
            })
        .isEqualTo(ex);
  }
}

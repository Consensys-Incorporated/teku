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
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;

class RpcRequestDecoderTest extends RpcDecoderTestBase {

  @Test
  public void shouldParseSingleResponse() throws Exception {
    List<List<ByteBuf>> testByteBufSlices = testByteBufSlices(lengthPrefix, messageData);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcRequestDecoder<BeaconBlocksByRootRequestMessage> decoder = method.createRequestDecoder();

      List<BeaconBlocksByRootRequestMessage> msgs = new ArrayList<>();
      for (ByteBuf bufSlice : bufSlices) {
        decoder.decodeRequest(bufSlice).ifPresent(msgs::add);
        bufSlice.release();
      }
      assertThat(decoder.complete()).isEmpty();
      for (ByteBuf bufSlice : bufSlices) {
        assertThat(bufSlice.refCnt()).isEqualTo(0);
      }

      assertThat(msgs).containsExactly(message);
    }
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() throws Exception {
    List<List<ByteBuf>> testByteBufSlices =
        testByteBufSlices(lengthPrefix, messageData, Bytes.fromHexString("0x1234"));

    for (int i = 0; i < testByteBufSlices.size(); i++) {
      List<ByteBuf> bufSlices = testByteBufSlices.get(i);

      RpcRequestDecoder<BeaconBlocksByRootRequestMessage> decoder = method.createRequestDecoder();

      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeRequest(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(ExtraDataAppendedException.class);
    }
  }

  @Test
  public void shouldProcessEmptyMessage() throws Exception {
    RpcRequestDecoder<EmptyMessage> decoder =
        new RpcRequestDecoder<>(EmptyMessage.SSZ_SCHEMA, encoding);
    assertThat(decoder.complete()).isNotEmpty().contains(EmptyMessage.EMPTY_MESSAGE);
  }
}

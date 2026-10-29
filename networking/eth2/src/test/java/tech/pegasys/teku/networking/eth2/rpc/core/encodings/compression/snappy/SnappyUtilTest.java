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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.DecompressionException;
import org.junit.jupiter.api.Test;

public class SnappyUtilTest {

  // masked CRC32C values computed independently of this implementation
  private static final int MASKED_EMPTY = 0xa282ead8;
  private static final int MASKED_CHECK_STRING = 0xc78ab0e5;
  private static final int MASKED_LARGE = 0x5db42102;

  @Test
  void calculateChecksum_emptyData() {
    assertThat(SnappyUtil.calculateChecksum(Unpooled.EMPTY_BUFFER)).isEqualTo(MASKED_EMPTY);
  }

  @Test
  void calculateChecksum_checkString() {
    final ByteBuf data = Unpooled.wrappedBuffer("123456789".getBytes(US_ASCII));
    assertThat(SnappyUtil.calculateChecksum(data)).isEqualTo(MASKED_CHECK_STRING);
    data.release();
  }

  @Test
  void calculateChecksum_largeHeapBuffer() {
    final ByteBuf data = Unpooled.wrappedBuffer(largeData());
    assertThat(SnappyUtil.calculateChecksum(data)).isEqualTo(MASKED_LARGE);
    data.release();
  }

  @Test
  void calculateChecksum_directBuffer() {
    final ByteBuf data = Unpooled.directBuffer().writeBytes(largeData());
    assertThat(SnappyUtil.calculateChecksum(data)).isEqualTo(MASKED_LARGE);
    data.release();
  }

  @Test
  void calculateChecksum_compositeBufferAcrossComponents() {
    final byte[] bytes = largeData();
    final CompositeByteBuf data = Unpooled.compositeBuffer();
    data.addComponent(true, Unpooled.wrappedBuffer(bytes, 0, 1000));
    data.addComponent(true, Unpooled.directBuffer().writeBytes(bytes, 1000, 30_000));
    data.addComponent(true, Unpooled.wrappedBuffer(bytes, 31_000, bytes.length - 31_000));
    assertThat(SnappyUtil.calculateChecksum(data)).isEqualTo(MASKED_LARGE);
    data.release();
  }

  @Test
  void calculateChecksum_rangeDoesNotMoveIndices() {
    final ByteBuf data = Unpooled.buffer();
    data.writeBytes(new byte[] {7, 7, 7});
    data.writeBytes("123456789".getBytes(US_ASCII));
    data.writeBytes(new byte[] {9, 9});
    data.readerIndex(1);

    assertThat(SnappyUtil.calculateChecksum(data, 3, 9)).isEqualTo(MASKED_CHECK_STRING);
    assertThat(data.readerIndex()).isEqualTo(1);
    assertThat(data.writerIndex()).isEqualTo(14);
    data.release();
  }

  @Test
  void validateChecksum_rejectsMismatch() {
    final ByteBuf data = Unpooled.wrappedBuffer("123456789".getBytes(US_ASCII));
    SnappyUtil.validateChecksum(MASKED_CHECK_STRING, data, 0, 9);
    assertThatThrownBy(() -> SnappyUtil.validateChecksum(MASKED_CHECK_STRING + 1, data, 0, 9))
        .isInstanceOf(DecompressionException.class);
    data.release();
  }

  private static byte[] largeData() {
    final byte[] bytes = new byte[256 * 300];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) i;
    }
    return bytes;
  }
}

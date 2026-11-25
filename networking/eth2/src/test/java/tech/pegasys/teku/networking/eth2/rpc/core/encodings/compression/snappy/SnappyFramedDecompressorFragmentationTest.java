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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor.Decompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadLargerThanExpectedException;

public class SnappyFramedDecompressorFragmentationTest {

  private static final Bytes STREAM_IDENTIFIER = Bytes.fromHexString("0xff060000734e61507059");

  @Test
  void uncompress_manySmallFrames() throws Exception {
    // 2000 frames of 64 bytes: small frames that still fit the compressed size bound (~48 bytes
    // is the minimum)
    final Bytes payload = Bytes.random(2000 * 64);
    final Decompressor decompressor =
        SnappyFramedCompressor.AIRCOMPRESSOR.createDecompressor(payload.size());
    final List<ByteBuf> slices = slices(uncompressedFrames(payload, 64), 7);
    final List<ByteBuf> out = new ArrayList<>();
    for (final ByteBuf slice : slices) {
      decompressor.decodeOneMessage(slice).ifPresent(out::add);
      slice.release();
    }
    decompressor.complete();
    assertThat(out).hasSize(1);
    assertThat(Bytes.wrapByteBuf(out.get(0))).isEqualTo(payload);
    out.get(0).release();
    assertThat(slices).allSatisfy(slice -> assertThat(slice.refCnt()).isZero());
  }

  @Test
  void uncompress_fragmentedStreamDecodesInLinearTime() {
    // 200_000 one-byte frames against a 10 MiB advertised length
    final List<ByteBuf> slices = slices(uncompressedFrames(Bytes.random(200_000), 1), 65536);
    final Decompressor decompressor =
        SnappyFramedCompressor.AIRCOMPRESSOR.createDecompressor(10 * 1024 * 1024);
    try {
      // runs in this thread, so the buffers are not released while still being decoded
      assertTimeout(
          Duration.ofSeconds(10),
          () -> {
            for (final ByteBuf slice : slices) {
              assertThat(decompressor.decodeOneMessage(slice)).isEmpty();
            }
          });
    } finally {
      slices.forEach(ByteBuf::release);
      decompressor.close();
    }
  }

  @Test
  void uncompress_rejectsFrameExceedingAdvertisedLength() {
    // one 10-byte frame against a 5-byte advertised length
    final ByteBuf in = Utils.toByteBuf(uncompressedFrames(Bytes.random(10), 10));
    final Decompressor decompressor = SnappyFramedCompressor.AIRCOMPRESSOR.createDecompressor(5);
    assertThatThrownBy(() -> decompressor.decodeOneMessage(in))
        .isInstanceOf(PayloadLargerThanExpectedException.class);
    in.release();
    assertThat(in.refCnt()).isEqualTo(0);
  }

  private static Bytes uncompressedFrames(final Bytes payload, final int frameSize) {
    final ByteBuf stream = Unpooled.buffer();
    stream.writeBytes(STREAM_IDENTIFIER.toArrayUnsafe());
    for (int offset = 0; offset < payload.size(); offset += frameSize) {
      final Bytes data = payload.slice(offset, Math.min(frameSize, payload.size() - offset));
      stream.writeByte(0x01);
      stream.writeMediumLE(data.size() + 4);
      stream.writeIntLE(SnappyUtil.calculateChecksum(Unpooled.wrappedBuffer(data.toArrayUnsafe())));
      stream.writeBytes(data.toArrayUnsafe());
    }
    final Bytes result = Bytes.wrap(ByteBufUtil.getBytes(stream));
    stream.release();
    return result;
  }

  private static List<ByteBuf> slices(final Bytes stream, final int sliceSize) {
    final List<ByteBuf> result = new ArrayList<>();
    for (int offset = 0; offset < stream.size(); offset += sliceSize) {
      final Bytes slice = stream.slice(offset, Math.min(sliceSize, stream.size() - offset));
      result.add(Utils.toByteBuf(slice));
    }
    return result;
  }
}

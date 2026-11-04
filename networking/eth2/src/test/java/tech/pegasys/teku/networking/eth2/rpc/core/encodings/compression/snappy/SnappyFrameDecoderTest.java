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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

public class SnappyFrameDecoderTest {

  private static final Bytes STREAM_IDENTIFIER = Bytes.fromHexString("0xff060000734e61507059");
  private static final int COMPRESSED = 0x00;
  private static final int UNCOMPRESSED = 0x01;
  private static final int SKIPPABLE = 0xfe;

  @Test
  void maxCompressedChunkSizeIsChecksumPlusLongestValidEncodingOf64KiB() {
    assertThat(SnappyFrameDecoder.MAX_COMPRESSED_CHUNK_SIZE).isEqualTo(393_225);
  }

  @Test
  void decodesChunkEncodedAsOneLiteralPerByte() throws Exception {
    final Bytes data = Bytes.random(65536);
    final ByteBuf block = Unpooled.buffer();
    // uncompressed length 65536 as a varint, then a one-byte literal (tag 0x00) per byte
    block.writeBytes(Bytes.fromHexString("0x808004").toArrayUnsafe());
    for (int i = 0; i < data.size(); i++) {
      block.writeByte(0x00);
      block.writeByte(data.get(i));
    }
    final ByteBuf in = chunkHeader(COMPRESSED, 4 + block.readableBytes());
    in.writeIntLE(SnappyUtil.calculateChecksum(Unpooled.wrappedBuffer(data.toArrayUnsafe())));
    in.writeBytes(block);
    block.release();
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    final ByteBuf frame = decoder.decodeOneMessage(in).orElseThrow();

    assertThat(Bytes.wrap(ByteBufUtil.getBytes(frame))).isEqualTo(data);
    frame.release();
    decoder.close();
    in.release();
  }

  @Test
  void acceptsCompressedChunkHeaderAtMaxEncodedLength() throws Exception {
    final ByteBuf in = chunkHeader(COMPRESSED, SnappyFrameDecoder.MAX_COMPRESSED_CHUNK_SIZE);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThat(decoder.decodeOneMessage(in)).isEmpty();

    decoder.close();
    in.release();
  }

  @Test
  void rejectsCompressedChunkLongerThanMaxEncodedLength() {
    final ByteBuf in = chunkHeader(COMPRESSED, SnappyFrameDecoder.MAX_COMPRESSED_CHUNK_SIZE + 1);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThatThrownBy(() -> decoder.decodeOneMessage(in)).isInstanceOf(CompressionException.class);

    in.release();
  }

  @Test
  void rejectsCompressedChunkShorterThanChecksum() {
    final ByteBuf in = chunkHeader(COMPRESSED, 0);
    in.writeZero(8);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThatThrownBy(() -> decoder.decodeOneMessage(in)).isInstanceOf(CompressionException.class);

    in.release();
  }

  @Test
  void rejectsCompressedChunkWithoutBlock() {
    // a snappy block always starts with its length varint, so a checksum alone is not a block
    final ByteBuf in = chunkHeader(COMPRESSED, 4);
    in.writeZero(8);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThatThrownBy(() -> decoder.decodeOneMessage(in))
        .isInstanceOf(CompressionException.class)
        .hasMessageContaining("shorter than");

    in.release();
  }

  @Test
  void rejectsUncompressedChunkShorterThanChecksum() {
    final ByteBuf in = chunkHeader(UNCOMPRESSED, 3);
    in.writeZero(8);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThatThrownBy(() -> decoder.decodeOneMessage(in)).isInstanceOf(CompressionException.class);

    in.release();
  }

  @Test
  void consumesSkippableChunkAsItArrives() throws Exception {
    final ByteBuf in = chunkHeader(SKIPPABLE, 0xFFFFFF);
    in.writeZero(1000);
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThat(decoder.decodeOneImpl(in)).isEmpty(); // stream identifier
    assertThat(decoder.decodeOneImpl(in)).isEmpty();

    assertThat(in.isReadable()).isFalse();
    decoder.close();
    in.release();
  }

  @Test
  void decodesDataAfterSkippableChunkSplitAcrossInputs() throws Exception {
    final Bytes data = Bytes.of(1, 2, 3);
    // short inputs are written as a single uncompressed chunk after the stream identifier
    final Bytes dataChunk =
        new AircompressorSnappyFrameEncoder().encode(data).slice(STREAM_IDENTIFIER.size());
    final ByteBuf first = chunkHeader(SKIPPABLE, 5);
    first.writeZero(2);
    final ByteBuf second = Unpooled.buffer();
    second.writeZero(3);
    second.writeBytes(dataChunk.toArrayUnsafe());
    final SnappyFrameDecoder decoder = new AircompressorSnappyFrameDecoder();

    assertThat(decoder.decodeOneMessage(first)).isEmpty();
    final ByteBuf frame = decoder.decodeOneMessage(second).orElseThrow();

    assertThat(Bytes.wrap(ByteBufUtil.getBytes(frame))).isEqualTo(data);
    frame.release();
    decoder.close();
    first.release();
    second.release();
  }

  private static ByteBuf chunkHeader(final int chunkType, final int chunkLength) {
    final ByteBuf in = Unpooled.buffer();
    in.writeBytes(STREAM_IDENTIFIER.toArrayUnsafe());
    in.writeByte(chunkType);
    in.writeMediumLE(chunkLength);
    return in;
  }
}

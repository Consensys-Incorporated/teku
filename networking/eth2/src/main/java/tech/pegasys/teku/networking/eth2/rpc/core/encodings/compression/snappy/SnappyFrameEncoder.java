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

import static tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyUtil.calculateChecksum;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class is mostly borrowed from the Netty implementation:
 * https://github.com/netty/netty/blob/4.1/codec/src/main/java/io/netty/handler/codec/compression/SnappyFrameEncoder.java
 *
 * <p>Compresses a {@link ByteBuf} using the Snappy framing format.
 *
 * <p>See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing
 * format</a>.
 */
public abstract class SnappyFrameEncoder {
  /**
   * The minimum amount that we'll consider actually attempting to compress. This value is preamble
   * + the minimum length our Snappy service will compress (instead of just emitting a literal).
   */
  private static final int MIN_COMPRESSIBLE_LENGTH = 18;

  /**
   * All streams should start with the "Stream identifier", containing chunk type 0xff, a length
   * field of 0x6, and 'sNaPpY' in ASCII.
   */
  private static final byte[] STREAM_START = {
    (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59
  };

  private boolean started;

  public Bytes encode(final Bytes in) {
    ByteBuf inBuf = Unpooled.wrappedBuffer(in.toArrayUnsafe());
    ByteBuf outBuf = Unpooled.buffer(in.size() / 2);
    try {
      encode(inBuf, outBuf);
      byte[] bytes = new byte[outBuf.readableBytes()];
      outBuf.readBytes(bytes);
      return Bytes.wrap(bytes);
    } finally {
      inBuf.release();
      outBuf.release();
    }
  }

  public void encode(final ByteBuf in, final ByteBuf out) {
    if (!in.isReadable()) {
      return;
    }

    if (!started) {
      started = true;
      out.writeBytes(STREAM_START);
    }

    int dataLength = in.readableBytes();
    if (dataLength > MIN_COMPRESSIBLE_LENGTH) {
      while (true) {
        final int lengthIdx = out.writerIndex() + 1;
        if (dataLength < MIN_COMPRESSIBLE_LENGTH) {
          ByteBuf slice = in.readSlice(dataLength);
          writeUnencodedChunk(slice, out, dataLength);
          break;
        }

        out.writeInt(0);
        if (dataLength > Short.MAX_VALUE) {
          ByteBuf slice = in.readSlice(Short.MAX_VALUE);
          calculateAndWriteChecksum(slice, out);
          encodeCompressedChunk(slice, out, Short.MAX_VALUE);
          setChunkLength(out, lengthIdx);
          dataLength -= Short.MAX_VALUE;
        } else {
          ByteBuf slice = in.readSlice(dataLength);
          calculateAndWriteChecksum(slice, out);
          encodeCompressedChunk(slice, out, dataLength);
          setChunkLength(out, lengthIdx);
          break;
        }
      }
    } else {
      writeUnencodedChunk(in, out, dataLength);
    }
  }

  private static void writeUnencodedChunk(
      final ByteBuf in, final ByteBuf out, final int dataLength) {
    out.writeByte(1);
    writeChunkLength(out, dataLength + 4);
    calculateAndWriteChecksum(in, out);
    out.writeBytes(in, dataLength);
  }

  private static void setChunkLength(final ByteBuf out, final int lengthIdx) {
    int chunkLength = out.writerIndex() - lengthIdx - 3;
    if (chunkLength >>> 24 != 0) {
      throw new IllegalArgumentException("compressed data too large: " + chunkLength);
    }
    out.setMediumLE(lengthIdx, chunkLength);
  }

  /**
   * Writes the 2-byte chunk length to the output buffer.
   *
   * @param out The buffer to write to
   * @param chunkLength The length to write
   */
  private static void writeChunkLength(final ByteBuf out, final int chunkLength) {
    out.writeMediumLE(chunkLength);
  }

  /**
   * Calculates and writes the 4-byte checksum to the output buffer
   *
   * @param slice The data to calculate the checksum for
   * @param out The output buffer to write the checksum to
   */
  private static void calculateAndWriteChecksum(final ByteBuf slice, final ByteBuf out) {
    out.writeIntLE(calculateChecksum(slice));
  }

  protected abstract void encodeCompressedChunk(ByteBuf input, ByteBuf output, int dataLength);
}

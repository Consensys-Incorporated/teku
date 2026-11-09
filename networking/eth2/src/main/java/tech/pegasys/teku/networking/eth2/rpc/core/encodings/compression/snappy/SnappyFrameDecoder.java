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

import static tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyUtil.validateChecksum;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

/**
 * This class is mostly borrowed from the Netty implementation:
 * https://github.com/netty/netty/blob/4.1/codec/src/main/java/io/netty/handler/codec/compression/SnappyFrameDecoder.java
 *
 * <p>Uncompresses a {@link ByteBuf} encoded with the Snappy framing format.
 *
 * <p>See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing
 * format</a>.
 */
public abstract class SnappyFrameDecoder
    extends AbstractByteBufDecoder<ByteBuf, CompressionException> {

  private enum ChunkType {
    STREAM_IDENTIFIER,
    COMPRESSED_DATA,
    UNCOMPRESSED_DATA,
    RESERVED_UNSKIPPABLE,
    RESERVED_SKIPPABLE
  }

  private static final int SNAPPY_IDENTIFIER_LEN = 6;
  // See https://github.com/google/snappy/blob/1.1.9/framing_format.txt#L95
  private static final int MAX_UNCOMPRESSED_DATA_SIZE = 65536 + 4;
  // an uncompressed chunk holds at least its checksum
  private static final int MIN_UNCOMPRESSED_DATA_SIZE = 4;
  // a compressed chunk holds its checksum and a block, which starts with a length varint
  private static final int MIN_COMPRESSED_CHUNK_SIZE = 5;
  // See https://github.com/google/snappy/blob/1.1.9/framing_format.txt#L82
  protected static final int MAX_DECOMPRESSED_DATA_SIZE = 65536;
  // A compressed chunk holds a checksum and a snappy block of at most MAX_DECOMPRESSED_DATA_SIZE
  // bytes. The format bounds such a block at 5 + 6n bytes: a length varint of at most 5 bytes, a
  // literal of L >= 1 bytes takes at most L + 5, and a copy of at least 1 byte at most 5.
  static final int MAX_COMPRESSED_CHUNK_SIZE = 4 + 5 + 6 * MAX_DECOMPRESSED_DATA_SIZE;

  private final boolean validateChecksums;

  private boolean started;
  private boolean corrupted;
  // remaining bytes of a skippable chunk, dropped as they arrive instead of being buffered
  private int bytesToSkip;

  /**
   * Creates a new snappy-framed decoder with validation of checksums turned on. To turn checksum
   * validation off, please use the alternate {@link #SnappyFrameDecoder(boolean)} constructor.
   */
  public SnappyFrameDecoder() {
    this(true);
  }

  /**
   * Creates a new snappy-framed decoder with validation of checksums as specified.
   *
   * @param validateChecksums If true, the checksum field will be validated against the actual
   *     uncompressed data, and if the checksums do not match, a suitable {@link
   *     CompressionException} will be thrown
   */
  public SnappyFrameDecoder(final boolean validateChecksums) {
    this.validateChecksums = validateChecksums;
  }

  @Override
  protected Optional<ByteBuf> decodeOneImpl(final ByteBuf in) throws CompressionException {
    if (corrupted) {
      in.skipBytes(in.readableBytes());
      return Optional.empty();
    }

    ByteBuf ret = null;

    try {
      if (bytesToSkip > 0) {
        skipPendingBytes(in);
        return Optional.empty();
      }

      int idx = in.readerIndex();
      final int inSize = in.readableBytes();
      if (inSize < 4) {
        // We need to be at least able to read the chunk type identifier (one byte),
        // and the length of the chunk (3 bytes) in order to proceed
        return Optional.empty();
      }

      final int chunkTypeVal = in.getUnsignedByte(idx);
      final ChunkType chunkType = mapChunkType((byte) chunkTypeVal);
      final int chunkLength = in.getUnsignedMediumLE(idx + 1);

      switch (chunkType) {
        case STREAM_IDENTIFIER -> {
          if (started) {
            throw new CompressionException("Extra Snappy stream identifier");
          }
          if (chunkLength != SNAPPY_IDENTIFIER_LEN) {
            throw new CompressionException(
                "Unexpected length of stream identifier: " + chunkLength);
          }

          if (inSize < 4 + SNAPPY_IDENTIFIER_LEN) {
            break;
          }

          in.skipBytes(4);
          int offset = in.readerIndex();
          in.skipBytes(SNAPPY_IDENTIFIER_LEN);

          checkByte(in.getByte(offset++), (byte) 's');
          checkByte(in.getByte(offset++), (byte) 'N');
          checkByte(in.getByte(offset++), (byte) 'a');
          checkByte(in.getByte(offset++), (byte) 'P');
          checkByte(in.getByte(offset++), (byte) 'p');
          checkByte(in.getByte(offset), (byte) 'Y');

          started = true;
        }
        case RESERVED_SKIPPABLE -> {
          if (!started) {
            throw new CompressionException(
                "Received RESERVED_SKIPPABLE tag before STREAM_IDENTIFIER");
          }

          in.skipBytes(4);
          bytesToSkip = chunkLength;
          skipPendingBytes(in);
        }
        case RESERVED_UNSKIPPABLE ->
            // The spec mandates that reserved unskippable chunks must immediately
            // return an error, as we must assume that we cannot decode the stream
            // correctly
            throw new CompressionException(
                "Found reserved unskippable chunk type: 0x" + Integer.toHexString(chunkTypeVal));
        case UNCOMPRESSED_DATA -> {
          if (!started) {
            throw new CompressionException(
                "Received UNCOMPRESSED_DATA tag before STREAM_IDENTIFIER");
          }
          if (chunkLength > MAX_UNCOMPRESSED_DATA_SIZE) {
            throw new CompressionException(
                "Received UNCOMPRESSED_DATA larger than " + MAX_UNCOMPRESSED_DATA_SIZE + " bytes");
          }
          if (chunkLength < MIN_UNCOMPRESSED_DATA_SIZE) {
            throw new CompressionException(
                "Received UNCOMPRESSED_DATA shorter than "
                    + MIN_UNCOMPRESSED_DATA_SIZE
                    + " bytes: "
                    + chunkLength);
          }

          if (inSize < 4 + chunkLength) {
            return Optional.empty();
          }

          in.skipBytes(4);
          if (validateChecksums) {
            int checksum = in.readIntLE();
            validateChecksum(checksum, in, in.readerIndex(), chunkLength - 4);
          } else {
            in.skipBytes(4);
          }
          ret = in.readRetainedSlice(chunkLength - 4);
        }
        case COMPRESSED_DATA -> {
          if (!started) {
            throw new CompressionException("Received COMPRESSED_DATA tag before STREAM_IDENTIFIER");
          }

          if (chunkLength > MAX_COMPRESSED_CHUNK_SIZE) {
            throw new CompressionException(
                "Received COMPRESSED_DATA that contains"
                    + " chunk that exceeds "
                    + MAX_COMPRESSED_CHUNK_SIZE
                    + " bytes");
          }
          if (chunkLength < MIN_COMPRESSED_CHUNK_SIZE) {
            throw new CompressionException(
                "Received COMPRESSED_DATA shorter than "
                    + MIN_COMPRESSED_CHUNK_SIZE
                    + " bytes: "
                    + chunkLength);
          }

          if (inSize < 4 + chunkLength) {
            return Optional.empty();
          }

          in.skipBytes(4);
          final int checksum = in.readIntLE();
          ByteBuf uncompressed = null;
          try {
            uncompressed = decodeCompressedData(in, chunkLength - 4);
            if (validateChecksums) {
              validateChecksum(checksum, uncompressed, 0, uncompressed.writerIndex());
            }
            ret = uncompressed;
            uncompressed = null;
          } finally {
            if (uncompressed != null) {
              uncompressed.release();
            }
          }
          reset();
        }
      }
    } catch (CompressionException e) {
      corrupted = true;
      throw e;
    }
    return Optional.ofNullable(ret);
  }

  @Override
  protected void throwUnprocessedDataException(final int dataLeft) throws CompressionException {
    throw new PayloadSmallerThanExpectedException(
        "Snappy stream complete, but unprocessed data left: " + dataLeft);
  }

  private void skipPendingBytes(final ByteBuf in) {
    final int skipped = Math.min(bytesToSkip, in.readableBytes());
    in.skipBytes(skipped);
    bytesToSkip -= skipped;
  }

  private static void checkByte(final byte actual, final byte expect) throws CompressionException {
    if (actual != expect) {
      throw new CompressionException(
          "Unexpected stream identifier contents. Mismatched snappy protocol version?");
    }
  }

  /**
   * Decodes the chunk type from the type tag byte.
   *
   * @param type The tag byte extracted from the stream
   * @return The appropriate {@link ChunkType}, defaulting to {@link ChunkType#RESERVED_UNSKIPPABLE}
   */
  private static ChunkType mapChunkType(final byte type) {
    if (type == 0) {
      return ChunkType.COMPRESSED_DATA;
    } else if (type == 1) {
      return ChunkType.UNCOMPRESSED_DATA;
    } else if (type == (byte) 0xff) {
      return ChunkType.STREAM_IDENTIFIER;
    } else if ((type & 0x80) == 0x80) {
      return ChunkType.RESERVED_SKIPPABLE;
    } else {
      return ChunkType.RESERVED_UNSKIPPABLE;
    }
  }

  protected abstract ByteBuf decodeCompressedData(ByteBuf input, int compressedLength)
      throws CompressionException;

  protected void reset() {}
}

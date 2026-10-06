/*
 * Copyright Consensys Software Inc., 2026
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.DisposedDecompressorException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadLargerThanExpectedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

/** Implements snappy compression using the "framed" / streaming format. */
public class SnappyFramedCompressor implements Compressor {

  // Suppliers intentionally create fresh stateful encoders and decoders per framed RPC payload.
  public static final SnappyFramedCompressor AIRCOMPRESSOR =
      new SnappyFramedCompressor(
          AircompressorSnappyFrameEncoder::new, AircompressorSnappyFrameDecoder::new);

  private final Supplier<SnappyFrameEncoder> encoderSupplier;
  private final Supplier<SnappyFrameDecoder> decoderSupplier;

  private SnappyFramedCompressor(
      final Supplier<SnappyFrameEncoder> encoderSupplier,
      final Supplier<SnappyFrameDecoder> decoderSupplier) {
    this.encoderSupplier = encoderSupplier;
    this.decoderSupplier = decoderSupplier;
  }

  private class SnappyFramedDecompressor implements Decompressor {
    private final SnappyFrameDecoder snappyFrameDecoder;
    private final int uncompressedPayloadSize;
    private int consumedCompressedSize = 0;
    private final List<ByteBuf> decodedSnappyFrames = new ArrayList<>();
    private boolean broken = false;
    private boolean disposed = false;

    public SnappyFramedDecompressor(final int uncompressedPayloadSize) {
      this.snappyFrameDecoder = decoderSupplier.get();
      this.uncompressedPayloadSize = uncompressedPayloadSize;
    }

    @Override
    public Optional<ByteBuf> decodeOneMessage(final ByteBuf input) throws CompressionException {
      if (broken) {
        throw new CompressionException("Compressed stream is broken");
      }
      if (disposed) {
        throw new DisposedDecompressorException();
      }

      while (true) {
        try {
          Optional<ByteBuf> byteBuf;
          try {
            int beforeReadableBytes = input.readableBytes();
            byteBuf = snappyFrameDecoder.decodeOneMessage(input);
            consumedCompressedSize += beforeReadableBytes - input.readableBytes();
          } catch (Exception e) {
            throw new CompressionException("Error in Snappy decompressor", e);
          }
          byteBuf.ifPresent(decodedSnappyFrames::add);

          if (consumedCompressedSize > getMaxCompressedLength(uncompressedPayloadSize)) {
            throw new CompressionException(
                "Total compressed frame size exceeded upper boundary for Snappy format: "
                    + consumedCompressedSize
                    + ">"
                    + getMaxCompressedLength(uncompressedPayloadSize));
          }
          if (byteBuf.isEmpty()) {
            break;
          }
          int decodedFramesLength =
              decodedSnappyFrames.stream().mapToInt(ByteBuf::readableBytes).sum();
          if (decodedFramesLength == uncompressedPayloadSize) {
            // wrapped ByteBuf takes ownership of the underlying buffers
            ByteBuf ret = Unpooled.wrappedBuffer(decodedSnappyFrames.toArray(new ByteBuf[0]));
            decodedSnappyFrames.clear();
            snappyFrameDecoder.complete();
            return Optional.of(ret);
          } else if (decodedFramesLength > uncompressedPayloadSize) {
            throw new PayloadLargerThanExpectedException(
                "Decoded snappy frames len is "
                    + decodedFramesLength
                    + " while expecting "
                    + uncompressedPayloadSize);
          }
        } catch (Exception e) {
          broken = true;
          close();
          throw e;
        }
      }
      return Optional.empty();
    }

    @Override
    public void complete() throws CompressionException {
      try {
        if (broken) {
          throw new CompressionException("Compressed stream is broken");
        }
        if (disposed) {
          throw new DisposedDecompressorException();
        }
        disposed = true;
        boolean unreturnedFrames = !decodedSnappyFrames.isEmpty();
        if (unreturnedFrames) {
          throw new PayloadSmallerThanExpectedException("Unread uncompressed frames on complete");
        }
      } finally {
        close();
      }
    }

    @Override
    public void close() {
      decodedSnappyFrames.forEach(ReferenceCounted::release);
      decodedSnappyFrames.clear();
      snappyFrameDecoder.close();
    }
  }

  @Override
  public Bytes compress(final Bytes data) {
    return encoderSupplier.get().encode(data);
  }

  @Override
  public Decompressor createDecompressor(final int uncompressedPayloadSize) {
    return new SnappyFramedDecompressor(uncompressedPayloadSize);
  }

  @Override
  public int getMaxCompressedLength(final int uncompressedLength) {
    // Return worst-case compression size
    // See:
    // https://github.com/google/snappy/blob/537f4ad6240e586970fe554614542e9717df7902/snappy.cc#L98
    return 32 + uncompressedLength + uncompressedLength / 6;
  }
}

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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.noop;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.DisposedDecompressorException;

public class NoopCompressor implements Compressor {

  private static class NoopDecompressor implements Decompressor {
    private final NoopDecoder decoder;
    private final int uncompressedPayloadSize;
    private boolean disposed = false;

    public NoopDecompressor(final int uncompressedPayloadSize) {
      this.decoder = new NoopDecoder(uncompressedPayloadSize);
      this.uncompressedPayloadSize = uncompressedPayloadSize;
    }

    @Override
    public Optional<ByteBuf> decodeOneMessage(final ByteBuf input) throws CompressionException {
      if (disposed) {
        throw new DisposedDecompressorException();
      }
      return uncompressedPayloadSize > 0
          ? decoder.decodeOneMessage(input)
          : Optional.of(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void complete() throws CompressionException {
      if (disposed) {
        throw new DisposedDecompressorException();
      }
      decoder.complete();
      disposed = true;
    }

    @Override
    public void close() {
      decoder.close();
    }
  }

  @Override
  public Bytes compress(final Bytes data) {
    return data;
  }

  @Override
  public Decompressor createDecompressor(final int uncompressedPayloadSize) {
    return new NoopDecompressor(uncompressedPayloadSize);
  }

  @Override
  public int getMaxCompressedLength(final int uncompressedLength) {
    return uncompressedLength;
  }
}

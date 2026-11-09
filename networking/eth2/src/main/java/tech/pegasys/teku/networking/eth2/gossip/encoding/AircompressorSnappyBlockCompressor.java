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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import io.airlift.compress.v3.MalformedInputException;
import io.airlift.compress.v3.snappy.SnappyDecompressor;
import org.apache.tuweni.bytes.Bytes;

/**
 * Implements snappy block compression using aircompressor-v3.
 *
 * <p>The aircompressor Java compressor keeps mutable per-instance scratch state for its compression
 * hash table, so compressor instances are kept thread-local rather than shared across gossip
 * threads. The decompressor is also kept thread-local to avoid sharing native-backed instances when
 * aircompressor selects its native implementation.
 */
public class AircompressorSnappyBlockCompressor extends SnappyCompressor {

  private final ThreadLocal<io.airlift.compress.v3.snappy.SnappyCompressor> compressor =
      ThreadLocal.withInitial(io.airlift.compress.v3.snappy.SnappyCompressor::create);
  private final ThreadLocal<SnappyDecompressor> decompressor =
      ThreadLocal.withInitial(SnappyDecompressor::create);

  @Override
  protected int getUncompressedLength(final byte[] compressedData) throws MalformedInputException {
    return decompressor.get().getUncompressedLength(compressedData, 0);
  }

  @Override
  protected Bytes uncompress(final byte[] compressedData, final int uncompressedLength)
      throws MalformedInputException {
    final byte[] uncompressedData = new byte[uncompressedLength];
    final int actualUncompressedLength =
        decompressor
            .get()
            .decompress(
                compressedData, 0, compressedData.length, uncompressedData, 0, uncompressedLength);
    return Bytes.wrap(uncompressedData, 0, actualUncompressedLength);
  }

  @Override
  protected Bytes compress(final byte[] data) {
    final io.airlift.compress.v3.snappy.SnappyCompressor compressor = this.compressor.get();
    final byte[] compressedData = new byte[compressor.maxCompressedLength(data.length)];
    final int compressedLength =
        compressor.compress(data, 0, data.length, compressedData, 0, compressedData.length);
    return Bytes.wrap(compressedData, 0, compressedLength);
  }
}

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

import io.airlift.compress.v3.MalformedInputException;
import io.airlift.compress.v3.snappy.SnappyDecompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

/** Snappy framed decoder that uses aircompressor-v3 for raw snappy chunks. */
public class AircompressorSnappyFrameDecoder extends SnappyFrameDecoder {

  // Owned by a fresh frame decoder per RPC payload; this mutable codec is not shared.
  private final SnappyDecompressor decompressor = SnappyDecompressor.create();

  @Override
  protected ByteBuf decodeCompressedData(final ByteBuf input, final int compressedLength)
      throws CompressionException {
    final byte[] compressedData = new byte[compressedLength];
    input.readBytes(compressedData);

    try {
      final int uncompressedLength = decompressor.getUncompressedLength(compressedData, 0);
      if (uncompressedLength > MAX_DECOMPRESSED_DATA_SIZE) {
        throw new CompressionException(
            "Received COMPRESSED_DATA that exceeds "
                + MAX_DECOMPRESSED_DATA_SIZE
                + " bytes after decompression");
      }

      final byte[] uncompressedData = new byte[uncompressedLength];
      final int actualUncompressedLength =
          decompressor.decompress(
              compressedData, 0, compressedLength, uncompressedData, 0, uncompressedLength);
      return Unpooled.wrappedBuffer(uncompressedData, 0, actualUncompressedLength);
    } catch (final MalformedInputException | IllegalArgumentException e) {
      throw new CompressionException("Error in Snappy decompressor", e);
    }
  }
}

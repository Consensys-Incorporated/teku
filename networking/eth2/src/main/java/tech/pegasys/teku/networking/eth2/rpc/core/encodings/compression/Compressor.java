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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import io.netty.buffer.ByteBuf;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

public interface Compressor {

  /**
   * Alias Decompressor interface which also restricts the number of decoded messages to 1
   *
   * @see ByteBufDecoder
   */
  interface Decompressor extends ByteBufDecoder<ByteBuf, CompressionException> {}

  /**
   * Returns the compressed data
   *
   * @param data The data to compress
   * @return The compressed data
   */
  Bytes compress(final Bytes data);

  /**
   * Creates a Decompressor instance which would return only a single decompressed data of size
   * {@code uncompressedPayloadSize}
   */
  Decompressor createDecompressor(final int uncompressedPayloadSize);

  /**
   * Returns a maximum estimate of the size of a compressed payload given the uncompressed payload
   * size.
   *
   * @param uncompressedLength The size of an uncompressed payload.
   * @return The maximum size of a payload of size {@code uncompressedLength} after compression.
   */
  int getMaxCompressedLength(final int uncompressedLength);
}

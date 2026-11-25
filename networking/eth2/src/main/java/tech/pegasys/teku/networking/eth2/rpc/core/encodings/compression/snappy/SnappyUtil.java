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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.compression.DecompressionException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

class SnappyUtil {

  static int calculateChecksum(final ByteBuf data) {
    return calculateChecksum(data, data.readerIndex(), data.readableBytes());
  }

  static int calculateChecksum(final ByteBuf data, final int offset, final int length) {
    final CRC32C crc32 = new CRC32C();
    // Bulk updates use the JDK's CRC32C intrinsic, which is far faster than one byte at a time.
    // nioBuffers gives one view per component of a composite buffer, without copying or moving
    // the buffer's indices.
    for (final ByteBuffer buffer : data.nioBuffers(offset, length)) {
      crc32.update(buffer);
    }
    return maskChecksum((int) crc32.getValue());
  }

  static int maskChecksum(final int checksum) {
    return (checksum >>> 15 | checksum << 17) + 0xa282ead8;
  }

  static void validateChecksum(
      final int expectedChecksum, final ByteBuf data, final int offset, final int length) {
    final int actualChecksum = calculateChecksum(data, offset, length);
    if (actualChecksum != expectedChecksum) {
      throw new DecompressionException(
          "mismatching checksum: "
              + Integer.toHexString(actualChecksum)
              + " (expected: "
              + Integer.toHexString(expectedChecksum)
              + ')');
    }
  }
}

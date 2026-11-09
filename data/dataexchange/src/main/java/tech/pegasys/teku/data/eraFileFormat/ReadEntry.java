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

package tech.pegasys.teku.data.eraFileFormat;

import com.google.common.primitives.Longs;
import java.nio.ByteBuffer;
import java.util.Arrays;

class ReadEntry {
  private final byte[] type;
  private final byte[] data;

  static final byte[] INDEX_ENTRY_TYPE = {'i', '2'};
  static final byte[] BLOCK_ENTRY_TYPE = {1, 0};
  static final byte[] STATE_ENTRY_TYPE = {2, 0};

  private final long dataSize;

  public ReadEntry(final ByteBuffer byteBuffer, final int offset) {
    // 8 byte header - first 2 are type, last 6 are little endian size
    this.type = new byte[] {byteBuffer.get(offset), byteBuffer.get(offset + 1)};
    final byte[] bsize = {
      (byte) 0,
      (byte) 0,
      byteBuffer.get(offset + 7),
      byteBuffer.get(offset + 6),
      byteBuffer.get(offset + 5),
      byteBuffer.get(offset + 4),
      byteBuffer.get(offset + 3),
      byteBuffer.get(offset + 2),
    };
    dataSize = Longs.fromByteArray(bsize);

    data = new byte[(int) dataSize];
    // read data from original position plus 8 into data
    byteBuffer.slice(offset + 8, (int) dataSize).get(data);
  }

  public byte[] getType() {
    return type;
  }

  public byte[] getData() {
    return data;
  }

  public long getDataSize() {
    return dataSize;
  }

  public boolean isIndexType() {
    return Arrays.equals(getType(), INDEX_ENTRY_TYPE);
  }

  public boolean isBlockType() {
    return Arrays.equals(getType(), BLOCK_ENTRY_TYPE);
  }

  public boolean isStateType() {
    return Arrays.equals(getType(), STATE_ENTRY_TYPE);
  }
}

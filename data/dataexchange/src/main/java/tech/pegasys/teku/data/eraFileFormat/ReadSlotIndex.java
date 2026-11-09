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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Longs;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ReadSlotIndex {

  private final UInt64 startSlot;
  private final long recordStart;
  private final List<Integer> slotOffsets = new ArrayList<>();
  private final long recordEnd;
  private final int count;
  private final ReadEntry entry;

  ReadSlotIndex(final ByteBuffer byteBuffer, final int offset) {
    this.recordEnd = offset;
    this.count = (int) byteBuffer.getLong((int) recordEnd - 8);
    this.recordStart = recordEnd - (8 * count + 24);
    this.entry = new ReadEntry(byteBuffer, (int) recordStart);

    final byte[] data = entry.getData();
    this.startSlot = UInt64.valueOf(getBigEndianLongFromLittleEndianData(data, 0));

    for (int i = 8; i < entry.getDataSize() - 15; i += 8) {
      long relativePosition = getBigEndianLongFromLittleEndianData(data, i);
      slotOffsets.add((int) relativePosition);
    }
  }

  long getBigEndianLongFromLittleEndianData(final byte[] data, final int startOffset) {

    checkArgument(
        data.length >= startOffset + 8,
        "buffer length is not sufficient to read a long from position %s",
        startOffset);
    return Longs.fromBytes(
        data[startOffset + 7],
        data[startOffset + 6],
        data[startOffset + 5],
        data[startOffset + 4],
        data[startOffset + 3],
        data[startOffset + 2],
        data[startOffset + 1],
        data[startOffset]);
  }

  public UInt64 getStartSlot() {
    return startSlot;
  }

  public long getRecordStart() {
    return recordStart;
  }

  public List<Integer> getSlotOffsets() {
    return slotOffsets;
  }

  public int getCount() {
    return count;
  }

  public ReadEntry getEntry() {
    return entry;
  }
}

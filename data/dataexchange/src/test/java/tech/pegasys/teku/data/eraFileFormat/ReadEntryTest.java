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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Longs;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class ReadEntryTest {
  private final byte[] dataBytes = {10, 0, 0, 0, 0, 0, 0, 0};

  @Test
  void shouldReadTypeAndSizeFromBuffer() {
    final byte[] bytes = encodeBytes(ReadEntry.INDEX_ENTRY_TYPE, dataBytes);
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    final ReadEntry entry = new ReadEntry(byteBuffer, 0);
    assertThat(entry.isIndexType()).isTrue();
    assertThat(entry.getDataSize()).isEqualTo(dataBytes.length);
    assertThat(entry.getData()).isEqualTo(dataBytes);
  }

  @Test
  void shouldDetectIncorrectType() {
    final byte[] type = {'z', 'z'};
    final byte[] bytes = encodeBytes(type, dataBytes);
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    final ReadEntry entry = new ReadEntry(byteBuffer, 0);
    assertThat(entry.isIndexType()).isFalse();
  }

  @Test
  void shouldDetectStateType() {
    final byte[] bytes = encodeBytes(ReadEntry.STATE_ENTRY_TYPE, dataBytes);
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    final ReadEntry entry = new ReadEntry(byteBuffer, 0);
    assertThat(entry.isStateType()).isTrue();
    assertThat(entry.getDataSize()).isEqualTo(dataBytes.length);
    assertThat(entry.getData()).isEqualTo(dataBytes);
  }

  private byte[] encodeBytes(final byte[] type, final byte[] dataLittleEndian) {
    final byte[] bytes = new byte[8 + dataLittleEndian.length];
    bytes[0] = type[0];
    bytes[1] = type[1];
    final byte[] sizeBytes = Longs.toByteArray(dataLittleEndian.length);
    for (int i = 0; i < 7; i++) {
      // big endian to little endian, and only the first 6 bytes for size
      bytes[2 + i] = sizeBytes[7 - i];
    }
    System.arraycopy(dataLittleEndian, 0, bytes, 8, dataLittleEndian.length);
    return bytes;
  }
}

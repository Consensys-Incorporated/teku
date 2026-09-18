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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;

/// Offset-table parsing and element-buffer building shared by the packed byte-lists
/// representations of fixed ({@code AbstractSszListSchema}) and progressive
/// ({@code AbstractSszProgressiveListSchema}) list schemas.
public final class PackedByteListsUtil {

  private PackedByteListsUtil() {}

  @SuppressWarnings("ArrayRecordComponent")
  public record PackedElements(Bytes sszBytes, int[] offsets) {}

  /// Parses and validates the offset table of a serialized list-of-byte-lists variable part.
  /// Structural checks always apply (truncation, alignment, zero first offset, monotonicity);
  /// pass {@code Long.MAX_VALUE} to disable either limit check. The element count is checked
  /// before the offset table is allocated. Returns offsets with the end sentinel ({@code
  /// offsets[count] == bytes.size()}).
  public static int[] parsePackedOffsets(
      final Bytes bytes, final long maxElementCount, final long maxElementSize) {
    final int endOffset = bytes.size();
    checkSsz(
        endOffset >= SszType.SSZ_LENGTH_SIZE,
        "Invalid SSZ: trying to read more bytes than available");
    final int firstElementOffset =
        SszType.sszBytesToLength(bytes.slice(0, SszType.SSZ_LENGTH_SIZE));
    checkSsz(firstElementOffset % SszType.SSZ_LENGTH_SIZE == 0, "Invalid first element offset");
    checkSsz(firstElementOffset > 0, "Invalid first element offset");
    checkSsz(firstElementOffset <= endOffset, "Invalid first element offset");
    final int elementsCount = firstElementOffset / SszType.SSZ_LENGTH_SIZE;
    if (elementsCount > maxElementCount) {
      throw new SszDeserializeException(
          "List length " + elementsCount + " exceeds max length " + maxElementCount);
    }
    final int[] offsets = new int[elementsCount + 1];
    offsets[0] = firstElementOffset;
    for (int i = 1; i < elementsCount; i++) {
      offsets[i] =
          SszType.sszBytesToLength(
              bytes.slice(i * SszType.SSZ_LENGTH_SIZE, SszType.SSZ_LENGTH_SIZE));
    }
    offsets[elementsCount] = endOffset;
    for (int i = 0; i < elementsCount; i++) {
      final int size = offsets[i + 1] - offsets[i];
      checkSsz(size >= 0, "Invalid SSZ: wrong child offsets");
      checkSsz(size <= maxElementSize, "SSZ element length exceeds max element type length");
    }
    return offsets;
  }

  /// Serializes elements into a packed variable part (offset table + concatenated element
  /// bytes) with the matching offsets array (end sentinel included).
  public static PackedElements packElements(final List<? extends SszData> elements) {
    final int count = elements.size();
    final byte[] offsetBytes = new byte[count * SszType.SSZ_LENGTH_SIZE];
    final int[] offsets = new int[count + 1];
    final Bytes[] parts = new Bytes[count + 1];
    int offset = count * SszType.SSZ_LENGTH_SIZE;
    for (int i = 0; i < count; i++) {
      final Bytes elementSsz = elements.get(i).sszSerialize();
      offsets[i] = offset;
      offsetBytes[i * SszType.SSZ_LENGTH_SIZE] = (byte) offset;
      offsetBytes[i * SszType.SSZ_LENGTH_SIZE + 1] = (byte) (offset >>> 8);
      offsetBytes[i * SszType.SSZ_LENGTH_SIZE + 2] = (byte) (offset >>> 16);
      offsetBytes[i * SszType.SSZ_LENGTH_SIZE + 3] = (byte) (offset >>> 24);
      parts[i + 1] = elementSsz;
      offset += elementSsz.size();
    }
    offsets[count] = offset;
    parts[0] = Bytes.wrap(offsetBytes);
    return new PackedElements(Bytes.wrap(parts), offsets);
  }

  private static void checkSsz(final boolean condition, final String error) {
    if (!condition) {
      throw new SszDeserializeException(error);
    }
  }
}

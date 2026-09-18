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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitlistImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions;
import tech.pegasys.teku.infrastructure.ssz.sos.SszMaxLengthExceededException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszBitlistSchemaImpl extends SszPrimitiveListSchemaImpl<Boolean, SszBit, SszBitlist>
    implements SszBitlistSchema<SszBitlist> {

  public SszBitlistSchemaImpl(final long maxLength) {
    super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
  }

  @Override
  public DeserializableTypeDefinition<SszBitlist> getJsonTypeDefinition() {
    return SszPrimitiveTypeDefinitions.sszSerializedType(this, "SSZ hexadecimal");
  }

  @Override
  public SszBitlist createFromBackingNode(final TreeNode node) {
    return new SszBitlistImpl(this, node);
  }

  @Override
  public SszBitlist ofBits(final int size, final int... setBitIndices) {
    Preconditions.checkArgument(size <= getMaxLength(), "size > maxLength");
    return SszBitlistImpl.ofBits(this, size, setBitIndices);
  }

  @Override
  public SszBitlist wrapBitSet(final int size, final BitSet bitSet) {
    Preconditions.checkArgument(size <= getMaxLength(), "size > maxLength");
    return SszBitlistImpl.wrapBitSet(this, size, bitSet);
  }

  @Override
  public SszBitlist createFromElements(final List<? extends SszBit> elements) {
    return ofBits(
        elements.size(),
        IntStream.range(0, elements.size()).filter(i -> elements.get(i).get()).toArray());
  }

  public TreeNode createTreeFromBitData(final int size, final byte[] bitSetBytes) {
    final int dataByteLen = TreeUtil.bitsCeilToBytes(size);
    final Bytes treeBytes =
        dataByteLen == 0 ? Bytes.EMPTY : Bytes.wrap(Arrays.copyOf(bitSetBytes, dataByteLen));
    final TreeNode dataTree = SchemaUtils.createTreeFromBytes(treeBytes, treeDepth());
    return createTree(dataTree, size);
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    int elementsCount = getLength(node);
    BytesCollector bytesCollector = new BytesCollector();
    getCompatibleVectorSchema()
        .sszSerializeVector(getVectorNode(node), bytesCollector, elementsCount);
    return bytesCollector.flushWithBoundaryBit(writer, elementsCount);
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    int availableBytes = reader.getAvailableBytes();
    // preliminary rough check
    checkSsz(
        (availableBytes - 1L) * 8 <= getMaxLength(), "SSZ sequence length exceeds max type length");
    Bytes bytes = reader.read(availableBytes);
    int length = SszBitlistImpl.sszGetLengthAndValidate(bytes);
    if (length > getMaxLength()) {
      throw new SszMaxLengthExceededException("Bitlist", length, getMaxLength());
    }
    Bytes treeBytes = SszBitlistImpl.sszTruncateLeadingBit(bytes, length);
    try (SszReader sszReader = SszReader.fromBytes(treeBytes)) {
      DeserializedData data = sszDeserializeVector(sszReader);
      return createTree(data.getDataTree(), length);
    }
  }

  @Override
  public String toString() {
    return "Bitlist[" + getMaxLength() + "]";
  }

  private static class BytesCollector implements SszWriter {

    private static class UnsafeBytes {

      private final byte[] bytes;
      private final int offset;
      private final int length;

      public UnsafeBytes(final byte[] bytes, final int offset, final int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
      }
    }

    private final List<UnsafeBytes> bytesList = new ArrayList<>();
    private int size;

    @Override
    public void write(final byte[] bytes, final int offset, final int length) {
      if (length == 0) {
        return;
      }
      bytesList.add(new UnsafeBytes(bytes, offset, length));
      size += length;
    }

    public int flushWithBoundaryBit(final SszWriter writer, final int boundaryBitOffset) {
      int bitIdx = boundaryBitOffset % 8;
      checkArgument(
          TreeUtil.bitsCeilToBytes(boundaryBitOffset) == size, "Invalid boundary bit offset");
      if (bitIdx == 0) {
        bytesList.forEach(bb -> writer.write(bb.bytes, bb.offset, bb.length));
        writer.write(new byte[] {1});
        return size + 1;
      } else {
        UnsafeBytes lastBytes = bytesList.getLast();
        byte lastByte = lastBytes.bytes[lastBytes.offset + lastBytes.length - 1];
        byte lastByteWithBoundaryBit = (byte) (lastByte ^ (1 << bitIdx));

        for (int i = 0; i < bytesList.size() - 1; i++) {
          UnsafeBytes bb = bytesList.get(i);
          writer.write(bb.bytes, bb.offset, bb.length);
        }
        if (lastBytes.length > 1) {
          writer.write(lastBytes.bytes, lastBytes.offset, lastBytes.length - 1);
        }
        writer.write(new byte[] {lastByteWithBoundaryBit});
        return size;
      }
    }
  }

  @Override
  public SszBitlist fromBytes(final Bytes bytes) {
    checkArgument(bytes != null, "Input bytes cannot be null");
    try (final SszReader reader = SszReader.fromBytes(bytes)) {
      final TreeNode node = sszDeserializeTree(reader);
      return createFromBackingNode(node);
    }
  }
}

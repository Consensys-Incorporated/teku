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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.nio.ByteOrder;
import java.util.OptionalLong;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.sos.SszByteArrayWriter;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/** Base class of {@link SszSchema} with SSZ serialization related methods */
public interface SszType {

  // the size of SSZ UInt32 lengths and offsets
  int SSZ_LENGTH_SIZE = 4;

  // serializes int length to SSZ 4 bytes
  static Bytes sszLengthToBytes(final int length) {
    return Bytes.ofUnsignedInt(length, ByteOrder.LITTLE_ENDIAN);
  }

  // deserializes int length from SSZ 4 bytes
  static int sszBytesToLength(final Bytes bytes) {
    if (!bytes.slice(SSZ_LENGTH_SIZE).isZero()) {
      throw new SszDeserializeException("Invalid length bytes: " + bytes);
    }
    int ret = bytes.slice(0, SSZ_LENGTH_SIZE).toInt(ByteOrder.LITTLE_ENDIAN);
    if (ret < 0) {
      throw new SszDeserializeException("Invalid length: " + ret);
    }
    return ret;
  }

  /** Indicates whether the type is fixed or variable size */
  boolean isFixedSize();

  /** Returns the size of the fixed SSZ part for this type */
  int getSszFixedPartSize();

  /** Returns the size of the variable SSZ part for this type and specified backing subtree */
  int getSszVariablePartSize(TreeNode node);

  /** Calculates the full SSZ size in bytes for this type and specified backing subtree */
  default int getSszSize(final TreeNode node) {
    return getSszFixedPartSize() + getSszVariablePartSize(node);
  }

  /** SSZ serializes the backing tree instance of this type */
  default Bytes sszSerializeTree(final TreeNode node) {
    SszByteArrayWriter writer = new SszByteArrayWriter(getSszSize(node));
    sszSerializeTree(node, writer);
    return writer.toBytes();
  }

  /**
   * SSZ serializes the backing tree of this type and returns the data as bytes 'stream' via passed
   * {@code writer}
   */
  int sszSerializeTree(TreeNode node, SszWriter writer);

  TreeNode sszDeserializeTree(SszReader reader) throws SszDeserializeException;

  SszLengthBounds getSszLengthBounds();

  default OptionalLong getNetworkSszLengthBytesUpperBound() {
    return OptionalLong.empty();
  }

  default SszLengthBounds getNetworkSszLengthBounds() {
    if (getNetworkSszLengthBytesUpperBound().isPresent()) {
      throw new UnsupportedOperationException(
          "Schemas with a network SSZ upper bound must override getNetworkSszLengthBounds()");
    }
    return getSszLengthBounds();
  }
}

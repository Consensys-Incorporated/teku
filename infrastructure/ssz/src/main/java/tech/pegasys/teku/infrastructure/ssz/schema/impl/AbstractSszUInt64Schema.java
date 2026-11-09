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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.createUInt64Definition;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public abstract class AbstractSszUInt64Schema<T extends SszUInt64>
    extends AbstractSszPrimitiveSchema<UInt64, T> {

  private final Supplier<DeserializableTypeDefinition<T>> typeDefinition =
      Suppliers.memoize(this::createJsonTypeDefinition);

  protected AbstractSszUInt64Schema() {
    super(64);
  }

  @Override
  public UInt64 createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
    final Bytes leafNodeBytes = node.getData();
    try {
      final Bytes elementBytes = leafNodeBytes.slice(internalIndex * 8, 8);
      return UInt64.fromLongBits(elementBytes.toLong(ByteOrder.LITTLE_ENDIAN));
    } catch (final Exception e) {
      // additional info to track down the bug https://github.com/PegaSysEng/teku/issues/2579
      String info =
          "Refer to https://github.com/PegaSysEng/teku/issues/2579 if see this exception. ";
      info += "internalIndex = " + internalIndex;
      info += ", leafNodeBytes: " + leafNodeBytes.getClass().getSimpleName();
      try {
        info += ", leafNodeBytes = " + leafNodeBytes.copy();
      } catch (Exception ex) {
        info += "(" + ex + ")";
      }
      try {
        info += ", leafNodeBytes[] = " + Arrays.toString(leafNodeBytes.toArray());
      } catch (Exception ex) {
        info += "(" + ex + ")";
      }
      throw new RuntimeException(info, e);
    }
  }

  @Override
  public TreeNode updateBackingNode(
      final TreeNode srcNode, final int index, final SszData newValue) {
    final Bytes uintBytes =
        Bytes.ofUnsignedLong(((SszUInt64) newValue).longValue(), ByteOrder.LITTLE_ENDIAN);
    final Bytes curVal = ((LeafNode) srcNode).getData();
    final Bytes newBytes = updateExtending(curVal, index * 8, uintBytes);
    return LeafNode.create(newBytes);
  }

  @Override
  public TreeNode updatePackedNode(
      final TreeNode srcNode, final List<PackedNodeUpdate<UInt64, T>> updates) {
    if (updates.size() == 4) {
      final byte[] data = new byte[32];
      for (int i = 0; i < 4; i++) {
        final long longValue = updates.get(i).getNewValue().longValue();
        final int off = i * 8;
        data[off + 0] = (byte) longValue;
        data[off + 1] = (byte) (longValue >> 8);
        data[off + 2] = (byte) (longValue >> 16);
        data[off + 3] = (byte) (longValue >> 24);
        data[off + 4] = (byte) (longValue >> 32);
        data[off + 5] = (byte) (longValue >> 40);
        data[off + 6] = (byte) (longValue >> 48);
        data[off + 7] = (byte) (longValue >> 56);
      }
      return LeafNode.create(Bytes.wrap(data));
    } else {
      return super.updatePackedNode(srcNode, updates);
    }
  }

  @Override
  public TreeNode getDefaultTree() {
    return LeafNode.ZERO_LEAVES[8];
  }

  private DeserializableTypeDefinition<T> createJsonTypeDefinition() {
    return createUInt64Definition(this);
  }

  @Override
  public DeserializableTypeDefinition<T> getJsonTypeDefinition() {
    return typeDefinition.get();
  }

  @Override
  public String toString() {
    return "UInt64";
  }
}

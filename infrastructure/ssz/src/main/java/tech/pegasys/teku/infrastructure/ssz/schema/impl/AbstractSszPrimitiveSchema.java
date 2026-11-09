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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

/**
 * Represents primitive view type
 *
 * @param <SszDataT> Class of the basic view of this type
 */
public abstract class AbstractSszPrimitiveSchema<DataT, SszDataT extends SszPrimitive<DataT>>
    implements SszPrimitiveSchema<DataT, SszDataT> {

  protected static Bytes updateExtending(
      final Bytes origBytes, final int origOff, final Bytes newBytes) {
    if (origOff == origBytes.size()) {
      return Bytes.wrap(origBytes, newBytes);
    } else {
      final MutableBytes dest;
      if (origOff + newBytes.size() > origBytes.size()) {
        dest = MutableBytes.create(origOff + newBytes.size());
        origBytes.copyTo(dest, 0);
      } else {
        dest = origBytes.mutableCopy();
      }
      newBytes.copyTo(dest, origOff);
      return dest;
    }
  }

  private final int bitsSize;
  private final int sszSize;
  private final SszLengthBounds sszLengthBounds;

  protected AbstractSszPrimitiveSchema(final int bitsSize) {
    checkArgument(
        bitsSize == 0 || (bitsSize > 0 && bitsSize <= 256 && 256 % bitsSize == 0),
        "Invalid bitsize: %s",
        bitsSize);
    this.bitsSize = bitsSize;
    this.sszSize = getSSZBytesSize();
    this.sszLengthBounds = SszLengthBounds.ofBits(bitsSize);
  }

  @Override
  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public SszDataT createFromBackingNode(final TreeNode node) {
    return createFromPackedNode(node, 0);
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    nodeStore.storeLeafNode(node, rootGIndex);
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (rootHash.isZero()) {
      return LeafNode.ZERO_LEAVES[sszSize];
    } else {
      final Bytes data = nodeSource.loadLeafNode(rootHash, rootGIndex);
      return LeafNode.create(data.slice(0, sszSize));
    }
  }

  @Override
  @SuppressWarnings("PatternMatchingInstanceof")
  public final DataT createFromPackedNodeUnboxed(final TreeNode node, final int internalIndex) {
    assert node instanceof LeafDataNode;
    return createFromLeafBackingNode((LeafDataNode) node, internalIndex);
  }

  protected abstract DataT createFromLeafBackingNode(LeafDataNode node, int internalIndex);

  public TreeNode createBackingNode(final SszData newValue) {
    return updateBackingNode(LeafNode.EMPTY_LEAF, 0, newValue);
  }

  @Override
  public TreeNode updatePackedNode(
      final TreeNode srcNode, final List<PackedNodeUpdate<DataT, SszDataT>> updates) {
    TreeNode res = srcNode;
    for (PackedNodeUpdate<DataT, SszDataT> update : updates) {
      res = updateBackingNode(res, update.getInternalIndex(), update.getNewValue());
    }
    return res;
  }

  protected abstract TreeNode updateBackingNode(
      TreeNode srcNode, int internalIndex, SszData newValue);

  private int getSSZBytesSize() {
    return bitsCeilToBytes(getBitsSize());
  }

  @Override
  public boolean isFixedSize() {
    return true;
  }

  @Override
  public int getSszFixedPartSize() {
    return sszSize;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    return 0;
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final Bytes nodeData;
    if (node instanceof final LeafDataNode leafDataNode) {
      // small perf optimization
      nodeData = leafDataNode.getData();
    } else {
      nodeData = node.hashTreeRoot();
    }
    writer.write(nodeData.toArrayUnsafe(), 0, sszSize);
    return sszSize;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    Bytes bytes = reader.read(sszSize);
    if (reader.getAvailableBytes() > 0) {
      throw new SszDeserializeException("Extra " + reader.getAvailableBytes() + " bytes found");
    }
    return createNodeFromSszBytes(bytes);
  }

  /**
   * Creates a leaf node from SSZ-encoded bytes containing one or more packed values of this type,
   * validating value encodings where the type constrains them (e.g. booleans must be 0 or 1).
   * Packed-list deserializers call this once per (up to 32-byte) chunk.
   */
  public LeafNode createNodeFromSszBytes(final Bytes bytes) {
    return LeafNode.create(bytes);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds;
  }
}

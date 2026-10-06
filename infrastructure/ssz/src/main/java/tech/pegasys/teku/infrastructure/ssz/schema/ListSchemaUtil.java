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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Shared helpers for SSZ list-like schemas that store their backing tree as BranchNode(dataTree,
 * lengthNode). Used by {@code AbstractSszListSchema}, {@code SszProgressiveListSchema}, and {@code
 * SszProgressiveBitlistSchema}.
 */
public class ListSchemaUtil {

  private ListSchemaUtil() {}

  /** SSZ length bounds of a list of at most {@code maxLength} elements of the given schema. */
  public static SszLengthBounds computeListSszLengthBounds(
      final SszSchema<?> elementSchema, final long maxLength) {
    final SszLengthBounds elementLengthBounds = elementSchema.getSszLengthBounds();
    // if elements are of dynamic size the offset size should be added for every element
    final SszLengthBounds elementAndOffsetLengthBounds =
        elementLengthBounds.addBytes(elementSchema.isFixedSize() ? 0 : SszType.SSZ_LENGTH_SIZE);
    final SszLengthBounds maxLenBounds =
        SszLengthBounds.ofBits(0, elementAndOffsetLengthBounds.mul(maxLength).getMaxBits());
    // adding 1 boundary bit for BitlistImpl
    return maxLenBounds
        .addBits(elementSchema.equals(SszPrimitiveSchemas.BIT_SCHEMA) ? 1 : 0)
        .ceilToBytes();
  }

  public static TreeNode toLengthNode(final int length) {
    return length == 0
        ? LeafNode.ZERO_LEAVES[8]
        : LeafNode.create(Bytes.ofUnsignedLong(length, ByteOrder.LITTLE_ENDIAN));
  }

  public static long fromLengthNode(final TreeNode lengthNode) {
    assert lengthNode instanceof LeafNode;
    return ((LeafNode) lengthNode).getData().toLong(ByteOrder.LITTLE_ENDIAN);
  }

  public static int getLength(final TreeNode listNode) {
    long longLength = fromLengthNode(listNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX));
    return Math.toIntExact(longLength);
  }

  public static TreeNode getVectorNode(final TreeNode listNode) {
    return listNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  /**
   * Builds the backing tree of a packed progressive list — BranchNode(progressiveDataTree,
   * lengthNode) — by splitting the packed data into 32-byte leaf chunks.
   *
   * @param length the element count stored in the length node (bits for bitlists, elements
   *     otherwise), independent of {@code packedBytes} size
   */
  public static TreeNode createPackedProgressiveListTree(
      final Bytes packedBytes, final int length) {
    return createPackedProgressiveListTree(packedBytes, LeafNode::create, length);
  }

  /**
   * Variant of {@link #createPackedProgressiveListTree(Bytes, int)} taking a chunk factory so
   * element schemas can validate constrained encodings while creating each leaf.
   */
  public static TreeNode createPackedProgressiveListTree(
      final Bytes packedBytes, final Function<Bytes, LeafNode> chunkFactory, final int length) {
    final int size = packedBytes.size();
    final List<LeafNode> chunks = new ArrayList<>(size / LeafNode.MAX_BYTE_SIZE + 1);
    int offset = 0;
    while (offset < size) {
      final int chunkSize = Math.min(LeafNode.MAX_BYTE_SIZE, size - offset);
      chunks.add(chunkFactory.apply(packedBytes.slice(offset, chunkSize)));
      offset += chunkSize;
    }
    return BranchNode.create(
        ProgressiveTreeUtil.createProgressiveTree(chunks), toLengthNode(length));
  }
}

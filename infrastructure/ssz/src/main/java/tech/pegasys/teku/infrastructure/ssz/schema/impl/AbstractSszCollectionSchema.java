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
import static java.lang.Integer.min;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszNodeTemplate;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

/** Type of homogeneous collections (like List and Vector) */
public abstract class AbstractSszCollectionSchema<
        SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    implements SszCompositeSchema<SszCollectionT> {

  private final long maxLength;
  private final SszSchema<SszElementT> elementSchema;
  private final SszSchemaHints hints;
  protected final Supplier<SszNodeTemplate> elementSszSupernodeTemplate =
      Suppliers.memoize(() -> SszNodeTemplate.createFromType(getElementSchema()));
  private final int treeDepth;
  private volatile TreeNode defaultTree;

  protected AbstractSszCollectionSchema(
      final long maxLength,
      final SszSchema<SszElementT> elementSchema,
      final SszSchemaHints hints) {
    checkArgument(maxLength >= 0);
    this.maxLength = maxLength;
    this.elementSchema = elementSchema;
    this.hints = hints;
    this.treeDepth = SszCompositeSchema.super.treeDepth();
  }

  protected abstract TreeNode createDefaultTree();

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      this.defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  @Override
  public long getMaxLength() {
    return maxLength;
  }

  @Override
  public int treeDepth() {
    return treeDepth;
  }

  public SszSchema<SszElementT> getElementSchema() {
    return elementSchema;
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    if (index >= maxLength) {
      throw new IndexOutOfBoundsException("Child index " + index + " >= maxLength " + maxLength);
    }
    return getElementSchema();
  }

  @Override
  public int getElementsPerChunk() {
    SszSchema<SszElementT> elementSchema = getElementSchema();
    return elementSchema.isPrimitive()
        ? (256 / ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize())
        : 1;
  }

  protected int getSszElementBitSize() {
    SszSchema<SszElementT> elementSchema = getElementSchema();
    return elementSchema.isPrimitive()
        ? ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize()
        : elementSchema.getSszFixedPartSize() * 8;
  }

  protected int getVariablePartSize(final TreeNode vectorNode, final int length) {
    if (isFixedSize()) {
      return 0;
    } else {
      SszSchema<SszElementT> elementSchema = getElementSchema();
      if (elementSchema.isFixedSize()) {
        return elementSchema.getSszFixedPartSize() * length;
      } else {
        int size = 0;
        for (int i = 0; i < length; i++) {
          size += elementSchema.getSszSize(vectorNode.get(getChildGeneralizedIndex(i)));
        }
        return size;
      }
    }
  }

  /**
   * Serializes {@code elementsCount} from the content of this collection
   *
   * @param vectorNode for a {@link SszVectorSchemaImpl} type - the node itself, for a {@link
   *     SszListSchemaImpl} - the left sibling node of list size node
   */
  public int sszSerializeVector(
      final TreeNode vectorNode, final SszWriter writer, final int elementsCount) {
    if (getElementSchema().isFixedSize()) {
      return sszSerializeFixedVectorFast(vectorNode, writer, elementsCount);
    } else {
      return sszSerializeVariableVector(vectorNode, writer, elementsCount);
    }
  }

  private int sszSerializeFixedVectorFast(
      final TreeNode vectorNode, final SszWriter writer, final int elementsCount) {
    if (elementsCount == 0) {
      return 0;
    }
    int nodesCount = getChunks(elementsCount);
    int[] bytesCnt = new int[1];
    TreeUtil.iterateLeavesData(
        vectorNode,
        getChildGeneralizedIndex(0),
        getChildGeneralizedIndex(nodesCount - 1),
        leafData -> {
          writer.write(leafData);
          bytesCnt[0] += leafData.size();
        });
    return bytesCnt[0];
  }

  private int sszSerializeVariableVector(
      final TreeNode vectorNode, final SszWriter writer, final int elementsCount) {
    SszSchema<?> elementType = getElementSchema();
    int variableOffset = SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getChildGeneralizedIndex(i));
      int childSize = elementType.getSszSize(childSubtree);
      writer.write(SszType.sszLengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getChildGeneralizedIndex(i));
      elementType.sszSerializeTree(childSubtree, writer);
    }
    return variableOffset;
  }

  protected DeserializedData sszDeserializeVector(final SszReader reader) {
    if (getElementSchema().isFixedSize()) {
      Optional<SszSuperNodeHint> sszSuperNodeHint = getHints().getHint(SszSuperNodeHint.class);
      return sszSuperNodeHint
          .map(superNodeHint -> sszDeserializeSupernode(reader, superNodeHint.getDepth()))
          .orElseGet(() -> sszDeserializeFixed(reader));
    } else {
      return sszDeserializeVariable(reader);
    }
  }

  private DeserializedData sszDeserializeSupernode(
      final SszReader reader, final int supernodeDepth) {
    SszNodeTemplate template = elementSszSupernodeTemplate.get();
    int sszSize = reader.getAvailableBytes();
    if (sszSize % template.getSszLength() != 0) {
      throw new SszDeserializeException("Ssz length is not multiple of element length");
    }
    int elementsCount = sszSize / template.getSszLength();
    int chunkSize = (1 << supernodeDepth) * template.getSszLength();
    int bytesRemain = sszSize;
    List<SszSuperNode> sszNodes = new ArrayList<>(bytesRemain / chunkSize + 1);
    while (bytesRemain > 0) {
      int toRead = min(bytesRemain, chunkSize);
      bytesRemain -= toRead;
      Bytes bytes = reader.read(toRead);
      SszSuperNode node = new SszSuperNode(supernodeDepth, template, bytes);
      sszNodes.add(node);
    }
    TreeNode tree =
        TreeUtil.createTree(
            sszNodes,
            new SszSuperNode(supernodeDepth, template, Bytes.EMPTY),
            treeDepth() - supernodeDepth);
    return new DeserializedData(tree, elementsCount);
  }

  private DeserializedData sszDeserializeFixed(final SszReader reader) {
    int bytesSize = reader.getAvailableBytes();
    checkSsz(
        bytesSize % getElementSchema().getSszFixedPartSize() == 0,
        "SSZ sequence length is not multiple of fixed element size");
    int elementBitSize = getSszElementBitSize();
    if (elementBitSize >= 8) {
      checkSsz(
          bytesSize * 8L / elementBitSize <= getMaxLength(),
          "SSZ sequence length exceeds max type length");
    } else {
      // preliminary rough check
      checkSsz(
          (bytesSize - 1) * 8L / elementBitSize <= getMaxLength(),
          "SSZ sequence length exceeds max type length");
    }
    if (getElementSchema() instanceof AbstractSszPrimitiveSchema) {
      int bytesRemain = bytesSize;
      List<LeafNode> childNodes = new ArrayList<>(bytesRemain / LeafNode.MAX_BYTE_SIZE + 1);
      while (bytesRemain > 0) {
        int toRead = min(bytesRemain, LeafNode.MAX_BYTE_SIZE);
        bytesRemain -= toRead;
        Bytes bytes = reader.read(toRead);
        LeafNode node = LeafNode.create(bytes);
        childNodes.add(node);
      }

      Optional<Byte> lastByte;
      if (childNodes.isEmpty()) {
        lastByte = Optional.empty();
      } else {
        Bytes lastNodeData = childNodes.getLast().getData();
        lastByte = Optional.of(lastNodeData.get(lastNodeData.size() - 1));
      }
      return new DeserializedData(
          TreeUtil.createTree(childNodes, treeDepth()), bytesSize * 8 / elementBitSize, lastByte);
    } else {
      int elementsCount = bytesSize / getElementSchema().getSszFixedPartSize();
      List<TreeNode> childNodes = new ArrayList<>();
      for (int i = 0; i < elementsCount; i++) {
        try (SszReader sszReader = reader.slice(getElementSchema().getSszFixedPartSize())) {
          TreeNode childNode = getElementSchema().sszDeserializeTree(sszReader);
          childNodes.add(childNode);
        }
      }
      return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), elementsCount);
    }
  }

  private DeserializedData sszDeserializeVariable(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    final List<TreeNode> childNodes = new ArrayList<>();
    if (endOffset > 0) {
      int firstElementOffset = SszType.sszBytesToLength(reader.read(SSZ_LENGTH_SIZE));
      checkSsz(firstElementOffset % SSZ_LENGTH_SIZE == 0, "Invalid first element offset");
      int elementsCount = firstElementOffset / SSZ_LENGTH_SIZE;
      checkSsz(elementsCount <= getMaxLength(), "SSZ sequence length exceeds max type length");
      IntList elementOffsets = new IntArrayList(elementsCount + 1);
      elementOffsets.add(firstElementOffset);
      for (int i = 1; i < elementsCount; i++) {
        int offset = SszType.sszBytesToLength(reader.read(SSZ_LENGTH_SIZE));
        elementOffsets.add(offset);
      }
      elementOffsets.add(endOffset);

      IntList elementSizes = new IntArrayList();
      IntStream.range(0, elementOffsets.size() - 1)
          .forEach(i -> elementSizes.add(elementOffsets.getInt(i + 1) - elementOffsets.getInt(i)));

      if (elementSizes.intStream().anyMatch(s -> s < 0)) {
        throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
      }

      for (int elementSize : elementSizes) {
        try (SszReader sszReader = reader.slice(elementSize)) {
          childNodes.add(getElementSchema().sszDeserializeTree(sszReader));
        }
      }
    }
    return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), childNodes.size());
  }

  protected static void checkSsz(final boolean condition, final String error) {
    if (!condition) {
      throw new SszDeserializeException(error);
    }
  }

  public SszSchemaHints getHints() {
    return hints;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszCollectionSchema<?, ?> that = (AbstractSszCollectionSchema<?, ?>) o;
    return maxLength == that.maxLength && elementSchema.equals(that.elementSchema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementSchema);
  }

  protected static class DeserializedData {

    private final TreeNode dataTree;
    private final int childrenCount;
    private final Optional<Byte> lastSszByte;

    public DeserializedData(final TreeNode dataTree, final int childrenCount) {
      this(dataTree, childrenCount, Optional.empty());
    }

    public DeserializedData(
        final TreeNode dataTree, final int childrenCount, final Optional<Byte> lastSszByte) {
      this.dataTree = dataTree;
      this.childrenCount = childrenCount;
      this.lastSszByte = lastSszByte;
    }

    public TreeNode getDataTree() {
      return dataTree;
    }

    public int getChildrenCount() {
      return childrenCount;
    }

    public Optional<Byte> getLastSszByte() {
      return lastSszByte;
    }
  }
}

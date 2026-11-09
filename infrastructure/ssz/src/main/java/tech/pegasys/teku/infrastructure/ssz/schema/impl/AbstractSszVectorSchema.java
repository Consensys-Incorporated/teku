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

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import com.google.common.base.Suppliers;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszVectorSchema<
        SszElementT extends SszData, SszVectorT extends SszVector<SszElementT>>
    extends AbstractSszCollectionSchema<SszElementT, SszVectorT>
    implements SszVectorSchema<SszElementT, SszVectorT> {

  private final boolean isListBacking;
  private final int fixedPartSize;
  private final SszLengthBounds sszLengthBounds;

  private final Supplier<DeserializableTypeDefinition<SszVectorT>> jsonTypeDefinition =
      Suppliers.memoize(this::createTypeDefinition);

  protected AbstractSszVectorSchema(
      final SszSchema<SszElementT> elementType, final long vectorLength) {
    this(elementType, vectorLength, false);
  }

  protected AbstractSszVectorSchema(
      final SszSchema<SszElementT> elementType,
      final long vectorLength,
      final boolean isListBacking) {
    this(elementType, vectorLength, isListBacking, SszSchemaHints.none());
  }

  protected AbstractSszVectorSchema(
      final SszSchema<SszElementT> elementSchema,
      final long vectorLength,
      final boolean isListBacking,
      final SszSchemaHints hints) {
    super(vectorLength, elementSchema, hints);
    this.isListBacking = isListBacking;
    this.fixedPartSize = calcSszFixedPartSize();
    this.sszLengthBounds = computeSszLengthBounds(elementSchema, vectorLength);
  }

  protected DeserializableTypeDefinition<SszVectorT> createTypeDefinition() {
    return new DeserializableArrayTypeDefinition<>(
        getElementSchema().getJsonTypeDefinition(), this::createFromElements);
  }

  @Override
  public SszVectorT getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  protected TreeNode createDefaultTree() {
    if (isListBacking) {
      Optional<SszSuperNodeHint> sszSuperNodeHint = getHints().getHint(SszSuperNodeHint.class);
      if (sszSuperNodeHint.isPresent()) {
        int superNodeDepth = sszSuperNodeHint.get().getDepth();
        SszSuperNode defaultSuperSszNode =
            new SszSuperNode(superNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
        int binaryDepth = treeDepth() - superNodeDepth;
        return TreeUtil.createTree(emptyList(), defaultSuperSszNode, binaryDepth);
      } else {
        return TreeUtil.createDefaultTree(maxChunks(), LeafNode.EMPTY_LEAF);
      }
    } else if (getElementsPerChunk() == 1) {
      return TreeUtil.createDefaultTree(maxChunks(), getElementSchema().getDefaultTree());
    } else {
      // packed vector
      int fullZeroNodesCount = getLength() / getElementsPerChunk();
      int lastNodeElementCount = getLength() % getElementsPerChunk();
      int lastNodeSizeBytes = bitsCeilToBytes(lastNodeElementCount * getSszElementBitSize());
      Stream<TreeNode> fullZeroNodes =
          Stream.<TreeNode>generate(() -> LeafNode.ZERO_LEAVES[32]).limit(fullZeroNodesCount);
      Stream<TreeNode> lastZeroNode =
          lastNodeSizeBytes > 0
              ? Stream.of(LeafNode.ZERO_LEAVES[lastNodeSizeBytes])
              : Stream.empty();
      return TreeUtil.createTree(Stream.concat(fullZeroNodes, lastZeroNode).toList());
    }
  }

  @Override
  public abstract SszVectorT createFromBackingNode(TreeNode node);

  @Override
  public int getLength() {
    long maxLength = getMaxLength();
    if (maxLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxLength);
    }
    return (int) maxLength;
  }

  public int getChunksCount() {
    long maxChunks = maxChunks();
    if (maxChunks > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxChunks);
    }
    return (int) maxChunks;
  }

  @Override
  public boolean isFixedSize() {
    return getElementSchema().isFixedSize();
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    return getVariablePartSize(node, getLength());
  }

  @Override
  public final int getSszFixedPartSize() {
    return fixedPartSize;
  }

  private int calcSszFixedPartSize() {
    if (isListBacking) {
      return 0;
    } else {
      int bitsPerChild = isFixedSize() ? getSszElementBitSize() : SSZ_LENGTH_SIZE * 8;
      return bitsCeilToBytes(getLength() * bitsPerChild);
    }
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    return sszSerializeVector(node, writer, getLength());
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    if (getElementSchema().equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      throw new UnsupportedOperationException(
          "Bitvector deserialization is only supported by SszBitvectorSchema");
    }

    DeserializedData data = sszDeserializeVector(reader);
    if (data.getChildrenCount() != getLength()) {
      throw new SszDeserializeException("Invalid Vector ssz");
    }
    return data.getDataTree();
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, treeDepth());
    return LoadingUtil.loadNodesToDepth(
        nodeSource,
        rootHash,
        rootGIndex,
        treeDepth(),
        getDefault().getBackingNode(),
        lastUsefulGIndex,
        this::loadChildNode);
  }

  private TreeNode loadChildNode(
      final TreeNodeSource nodeSource, final Bytes32 childHash, final long childGIndex) {
    return LoadingUtil.loadCollectionChild(
        nodeSource,
        childHash,
        childGIndex,
        getLength(),
        getElementsPerChunk(),
        treeDepth(),
        getElementSchema());
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds;
  }

  private static SszLengthBounds computeSszLengthBounds(
      final SszSchema<?> elementSchema, final long length) {
    return elementSchema
        .getSszLengthBounds()
        // if elements are of dynamic size the offset size should be added for every element
        .addBytes(elementSchema.isFixedSize() ? 0 : SSZ_LENGTH_SIZE)
        .mul(length)
        .ceilToBytes();
  }

  @Override
  public DeserializableTypeDefinition<SszVectorT> getJsonTypeDefinition() {
    return jsonTypeDefinition.get();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractSszVectorSchema)) {
      return false;
    }
    AbstractSszVectorSchema<?, ?> that = (AbstractSszVectorSchema<?, ?>) o;
    return getElementSchema().equals(that.getElementSchema())
        && getMaxLength() == that.getMaxLength();
  }

  @Override
  public String toString() {
    return "Vector[" + getElementSchema() + ", " + getLength() + "]";
  }
}

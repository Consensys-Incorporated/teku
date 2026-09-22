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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.toLengthNode;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.nio.ByteOrder;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszPackedByteListsHint;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.LoadingUtil.ChildLoader;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.StoringUtil.TargetDepthNodeHandler;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszListSchema<
        ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    extends AbstractSszCollectionSchema<ElementDataT, SszListT>
    implements SszListSchema<ElementDataT, SszListT> {
  private final SszVectorSchemaImpl<ElementDataT> compatibleVectorSchema;
  private final SszLengthBounds sszLengthBounds;
  private final DeserializableTypeDefinition<SszListT> jsonTypeDefinition;
  // null when the SszPackedByteListsHint is not present
  private final SszByteListSchema<?> packedByteListElementSchema;

  protected AbstractSszListSchema(
      final SszSchema<ElementDataT> elementSchema, final long maxLength) {
    this(elementSchema, maxLength, SszSchemaHints.none());
  }

  protected AbstractSszListSchema(
      final SszSchema<ElementDataT> elementSchema,
      final long maxLength,
      final SszSchemaHints hints) {
    super(maxLength, elementSchema, hints);
    if (hints.getHint(SszPackedByteListsHint.class).isPresent()) {
      checkArgument(
          !(elementSchema instanceof SszProgressiveByteListSchema),
          "SszPackedByteListsHint on a fixed list requires a fixed byte list element schema but got %s",
          elementSchema);
      checkArgument(
          elementSchema instanceof SszByteListSchema,
          "SszPackedByteListsHint requires a byte list element schema but got %s",
          elementSchema);
      checkArgument(
          maxLength >= 2, "SszPackedByteListsHint requires maxLength >= 2 but got %s", maxLength);
      this.packedByteListElementSchema = (SszByteListSchema<?>) elementSchema;
    } else {
      this.packedByteListElementSchema = null;
    }
    this.compatibleVectorSchema =
        new SszVectorSchemaImpl<>(elementSchema, getMaxLength(), true, getHints());
    this.sszLengthBounds = ListSchemaUtil.computeListSszLengthBounds(elementSchema, maxLength);
    this.jsonTypeDefinition =
        new DeserializableArrayTypeDefinition<>(
            getElementSchema().getJsonTypeDefinition(), this::createFromElements);
  }

  @Override
  public TreeNode createTreeFromElements(final List<? extends ElementDataT> elements) {
    if (packedByteListElementSchema == null || elements.isEmpty()) {
      return SszListSchema.super.createTreeFromElements(elements);
    }
    checkArgument(
        elements.size() <= getMaxLength(),
        "Too many elements for this collection type (max length %s, size %s)",
        getMaxLength(),
        elements.size());
    final PackedByteListsUtil.PackedElements packed = PackedByteListsUtil.packElements(elements);
    final SszPackedByteListsNode packedNode =
        new SszPackedByteListsNode(
            packed.sszBytes(),
            packed.offsets(),
            packedByteListElementSchema.treeDepth(),
            treeDepth(),
            this::materializePackedElement);
    return createTree(packedNode, elements.size());
  }

  @Override
  protected TreeNode createDefaultTree() {
    return createTree(getCompatibleVectorSchema().getDefaultTree(), 0);
  }

  protected TreeNode createTree(final TreeNode dataNode, final int length) {
    return BranchNode.create(dataNode, toLengthNode(length));
  }

  @Override
  public SszListT getDefault() {
    return createFromBackingNode(createDefaultTree());
  }

  @Override
  public abstract SszListT createFromBackingNode(TreeNode node);

  protected AbstractSszVectorSchema<ElementDataT, ?> getCompatibleVectorSchema() {
    return compatibleVectorSchema;
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(
        GIndexUtil.LEFT_CHILD_G_INDEX, super.getChildGeneralizedIndex(elementIndex));
  }

  @Override
  public int getSszFixedPartSize() {
    return 0;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (getVectorNode(node) instanceof SszPackedByteListsNode packedNode) {
      return packedNode.getSszBytes().size();
    }
    int length = getLength(node);
    SszSchema<?> elementSchema = getElementSchema();
    if (elementSchema.isFixedSize()) {
      if (getSszElementBitSize() == 1) {
        // BitlistImpl is handled specially
        return length / 8 + 1;
      } else {
        return Math.toIntExact(bitsCeilToBytes((long) length * getSszElementBitSize()));
      }
    } else {
      return getCompatibleVectorSchema().getVariablePartSize(getVectorNode(node), length)
          + length * SSZ_LENGTH_SIZE;
    }
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    int elementsCount = getLength(node);
    if (getElementSchema().equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      throw new UnsupportedOperationException(
          "BitlistImpl serialization is only supported by SszBitlistSchema");
    }
    if (getVectorNode(node) instanceof SszPackedByteListsNode packedNode) {
      final Bytes sszBytes = packedNode.getSszBytes();
      writer.write(sszBytes);
      return sszBytes.size();
    }
    return getCompatibleVectorSchema()
        .sszSerializeVector(getVectorNode(node), writer, elementsCount);
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    if (getElementSchema().equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      throw new UnsupportedOperationException(
          "BitlistImpl deserialization is only supported by SszBitlistSchema");
    }
    if (packedByteListElementSchema != null) {
      return sszDeserializePacked(reader);
    }
    DeserializedData data = sszDeserializeVector(reader);
    return createTree(data.getDataTree(), data.getChildrenCount());
  }

  private TreeNode sszDeserializePacked(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    if (endOffset == 0) {
      return createDefaultTree();
    }
    final Bytes bytes = reader.read(endOffset);
    final int[] offsets = parsePackedOffsets(bytes);
    final SszPackedByteListsNode packedNode =
        new SszPackedByteListsNode(
            bytes,
            offsets,
            packedByteListElementSchema.treeDepth(),
            treeDepth(),
            this::materializePackedElement);
    return createTree(packedNode, packedNode.getElementCount());
  }

  private int[] parsePackedOffsets(final Bytes bytes) {
    return PackedByteListsUtil.parsePackedOffsets(
        bytes, getMaxLength(), packedByteListElementSchema.getMaxLength());
  }

  private TreeNode materializePackedElement(final Bytes elementSsz) {
    try (SszReader elementReader = SszReader.fromBytes(elementSsz)) {
      return getElementSchema().sszDeserializeTree(elementReader);
    }
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    // Lists start with a branch with the data on the left and length on the right
    final TreeNode vectorNode = getVectorNode(node);
    final TreeNode lengthNode = node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);

    // Store vector data (omitting empty list items at the end...)
    storeVectorNodes(
        nodeStore,
        maxBranchLevelsSkipped,
        GIndexUtil.gIdxLeftGIndex(rootGIndex),
        vectorNode,
        getLength(node));

    // Store leaf node
    nodeStore.storeLeafNode(lengthNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    // Store list root node
    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {vectorNode.hashTreeRoot(), lengthNode.hashTreeRoot()});
  }

  private void storeVectorNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node,
      final int length) {
    if (length == 0) {
      // Nothing useful to store
      return;
    }
    final int superNodeDepth = getSuperNodeDepth();
    final int childDepth = compatibleVectorSchema.treeDepth() - superNodeDepth;
    if (childDepth == 0 && superNodeDepth == 0) {
      // Only one child so wrapper is omitted
      storeChildNode(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
      return;
    }
    final long lastUsefulGIndex = getVectorLastUsefulGIndex(rootGIndex, length, superNodeDepth);
    // NOTE: storing SszSuperNodes via storeLeafNode is unsound for payloads <= 32 bytes: stores
    // assume a leaf's root equals rightPad(data) and skip persisting them (see
    // KvStoreTreeNodeStore.storeLeafNode), which does not hold for computed-root data nodes.
    // Left as-is: no production schema currently stores supernode-hinted structures.
    final TargetDepthNodeHandler targetDepthNodeHandler =
        superNodeDepth == 0
            ? (targetDepthNode, targetDepthGIndex) ->
                compatibleVectorSchema.storeChildNode(
                    nodeStore, maxBranchLevelsSkipped, targetDepthGIndex, targetDepthNode)
            : nodeStore::storeLeafNode;
    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        node,
        rootGIndex,
        childDepth,
        lastUsefulGIndex,
        targetDepthNodeHandler);
  }

  private int getSuperNodeDepth() {
    return getHints().getHint(SszSuperNodeHint.class).map(SszSuperNodeHint::getDepth).orElse(0);
  }

  private long getVectorLastUsefulGIndex(
      final long rootGIndex, final int length, final int superNodeDepth) {
    if (length == 0) {
      return rootGIndex;
    }
    final int elementsPerSuperNode = Math.max(1, Math.toIntExact(1L << superNodeDepth));
    final int elementsPerNode = compatibleVectorSchema.getElementsPerChunk() * elementsPerSuperNode;
    final int fullNodeCount = length / elementsPerNode;
    int lastNodeElementCount = length % elementsPerNode;
    final long lastUsefulChildIndex = lastNodeElementCount == 0 ? fullNodeCount - 1 : fullNodeCount;
    return GIndexUtil.gIdxChildGIndex(
        rootGIndex, lastUsefulChildIndex, compatibleVectorSchema.treeDepth() - superNodeDepth);
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }
    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "List root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "List root node must have depth of 1");
    final Bytes32 vectorHash = branchData.getChildren()[0];
    final Bytes32 lengthHash = branchData.getChildren()[1];
    final int length =
        nodeSource
            .loadLeafNode(lengthHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);

    final int superNodeDepth = getSuperNodeDepth();
    final ChildLoader childLoader =
        superNodeDepth == 0
            ? (childNodeSource, childHash, childGIndex) ->
                LoadingUtil.loadCollectionChild(
                    childNodeSource,
                    childHash,
                    childGIndex,
                    length,
                    compatibleVectorSchema.getElementsPerChunk(),
                    compatibleVectorSchema.treeDepth(),
                    compatibleVectorSchema.getElementSchema())
            : (childNodeSource, childHash, childGIndex) -> {
              final Bytes data;
              if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(childHash)) {
                data = Bytes.EMPTY;
              } else {
                data = nodeSource.loadLeafNode(childHash, childGIndex);
              }
              return new SszSuperNode(superNodeDepth, elementSszSupernodeTemplate.get(), data);
            };
    final long vectorRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);
    final long lastUsefulGIndex =
        getVectorLastUsefulGIndex(vectorRootGIndex, length, superNodeDepth);
    final TreeNode vectorNode =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            vectorHash,
            vectorRootGIndex,
            compatibleVectorSchema.treeDepth() - superNodeDepth,
            compatibleVectorSchema.getDefault().getBackingNode(),
            lastUsefulGIndex,
            childLoader);
    return BranchNode.create(vectorNode, toLengthNode(length));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds;
  }

  @Override
  public DeserializableTypeDefinition<SszListT> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractSszListSchema)) {
      return false;
    }
    AbstractSszListSchema<?, ?> that = (AbstractSszListSchema<?, ?>) o;
    return getElementSchema().equals(that.getElementSchema())
        && getMaxLength() == that.getMaxLength();
  }

  @Override
  public String toString() {
    return "List[" + getElementSchema() + ", " + getMaxLength() + "]" + getHints();
  }
}

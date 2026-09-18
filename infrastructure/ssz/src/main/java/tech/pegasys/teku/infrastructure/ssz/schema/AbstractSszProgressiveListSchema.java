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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.createPackedProgressiveListTree;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.toLengthNode;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import com.google.common.base.Suppliers;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszPackedByteListsHint;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.LoadingUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.PackedByteListsUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.StoringUtil;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszNodeTemplate;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedProgressiveByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

/**
 * Base schema for ProgressiveList (EIP-7916) — a variable-length homogeneous collection with no max
 * capacity that uses a progressive merkle tree for stable generalized indices.
 *
 * <p>The {@code SszListT} type parameter is the concrete list type produced by the schema. Most
 * usages are {@link SszProgressiveListSchema} where {@code SszListT} is {@link SszList}, but
 * specialized subclasses (e.g. a progressive byte list) produce {@link SszList} subtypes so they
 * can preserve Teku's existing collection interfaces.
 *
 * <p>Tree structure: BranchNode(progressiveDataTree, lengthNode)
 *
 * <p>The progressive data tree is a right-leaning asymmetric tree where subtree capacities grow by
 * 4x per level.
 */
public abstract class AbstractSszProgressiveListSchema<
        ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    implements SszListSchema<ElementDataT, SszListT> {

  private final SszSchema<ElementDataT> elementSchema;
  private final SszSchemaHints hints;
  private final TreeNode defaultTree;
  private final int elementsPerChunk;
  private final DeserializableTypeDefinition<SszListT> jsonTypeDefinition;
  private final Supplier<SszNodeTemplate> elementSszSupernodeTemplate =
      Suppliers.memoize(() -> SszNodeTemplate.createFromType(getElementSchema()));

  private final boolean packByteListElements;

  /**
   * Optional length limit. Progressive lists have no SSZ-level capacity, but consensus may still
   * bound them (e.g. {@code MAX_ATTESTATIONS_ELECTRA}); when set, the limit is enforced on
   * construction and on deserialization, before any element is materialized. {@link Long#MAX_VALUE}
   * means unlimited.
   */
  private final long maxLength;

  protected AbstractSszProgressiveListSchema(final SszSchema<ElementDataT> elementSchema) {
    this(elementSchema, SszSchemaHints.none());
  }

  protected AbstractSszProgressiveListSchema(
      final SszSchema<ElementDataT> elementSchema, final SszSchemaHints hints) {
    this(elementSchema, hints, Long.MAX_VALUE);
  }

  protected AbstractSszProgressiveListSchema(
      final SszSchema<ElementDataT> elementSchema,
      final SszSchemaHints hints,
      final long maxLength) {
    checkArgument(maxLength >= 0, "maxLength must not be negative but was %s", maxLength);
    this.elementSchema = elementSchema;
    this.hints = hints;
    this.maxLength = maxLength;
    if (hints.getHint(SszPackedByteListsHint.class).isPresent()) {
      checkArgument(
          elementSchema instanceof SszProgressiveByteListSchema,
          "SszPackedByteListsHint on a progressive list requires a progressive byte list element schema but got %s",
          elementSchema);
      checkArgument(
          hints.getHint(SszSuperNodeHint.class).isEmpty(),
          "SszPackedByteListsHint and SszSuperNodeHint are mutually exclusive");
      this.packByteListElements = true;
    } else {
      this.packByteListElements = false;
    }
    this.elementsPerChunk = computeElementsPerChunk(elementSchema);
    this.defaultTree =
        BranchNode.create(ProgressiveTreeUtil.createProgressiveTree(List.of()), toLengthNode(0));
    this.jsonTypeDefinition =
        new DeserializableArrayTypeDefinition<>(
            elementSchema.getJsonTypeDefinition(), this::createFromElements);
  }

  private static int computeElementsPerChunk(final SszSchema<?> schema) {
    if (schema.isPrimitive()) {
      return 256 / ((SszPrimitiveSchema<?, ?>) schema).getBitsSize();
    }
    return 1;
  }

  // ===== SszCollectionSchema =====

  @Override
  public SszSchema<ElementDataT> getElementSchema() {
    return elementSchema;
  }

  @Override
  public TreeNode createTreeFromElements(final List<? extends ElementDataT> elements) {
    checkArgument(
        elements.size() <= maxLength,
        "Too many elements for this collection type (element type: %s, max length %s, size %s)",
        elementSchema,
        maxLength,
        elements.size());
    if (packByteListElements && !elements.isEmpty()) {
      final PackedByteListsUtil.PackedElements packed = PackedByteListsUtil.packElements(elements);
      final SszPackedProgressiveByteListsNode packedNode =
          new SszPackedProgressiveByteListsNode(
              packed.sszBytes(), packed.offsets(), this::materializePackedElement);
      return BranchNode.create(packedNode, toLengthNode(elements.size()));
    }
    final List<TreeNode> chunks = packElementsToChunks(elements);
    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    return BranchNode.create(progressiveTree, toLengthNode(elements.size()));
  }

  // ===== SszCompositeSchema =====

  @Override
  public long getMaxLength() {
    return maxLength;
  }

  private void checkDeserializedLength(final long elementsCount) {
    if (elementsCount > maxLength) {
      throw new SszDeserializeException(
          "List length " + elementsCount + " exceeds max length " + maxLength);
    }
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return elementSchema;
  }

  @Override
  public int getElementsPerChunk() {
    return elementsPerChunk;
  }

  @Override
  public long maxChunks() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed maxChunks");
  }

  @Override
  public int treeDepth() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed tree depth");
  }

  @Override
  public long treeWidth() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed tree width");
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(
        GIndexUtil.LEFT_CHILD_G_INDEX,
        ProgressiveTreeUtil.getElementGeneralizedIndex(elementIndex));
  }

  @Override
  public void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    elementSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  // ===== SszSchema =====

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszProgressiveListImpl<>(this, node);
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public int getSszFixedPartSize() {
    return 0;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (getVectorNode(node) instanceof SszPackedProgressiveByteListsNode packedNode) {
      return packedNode.getSszBytes().size();
    }
    final int length = getLength(node);
    if (elementSchema.isFixedSize()) {
      return (int) bitsCeilToBytes((long) length * getSszElementBitSize());
    } else {
      int size = 0;
      for (int i = 0; i < length; i++) {
        size += elementSchema.getSszSize(node.get(getChildGeneralizedIndex(i)));
        size += SszType.SSZ_LENGTH_SIZE;
      }
      return size;
    }
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final int elementsCount = getLength(node);
    if (elementsCount == 0) {
      return 0;
    }
    final TreeNode dataNode = getVectorNode(node);
    if (dataNode instanceof SszPackedProgressiveByteListsNode packedNode) {
      final Bytes sszBytes = packedNode.getSszBytes();
      writer.write(sszBytes);
      return sszBytes.size();
    }
    if (elementSchema.isFixedSize()) {
      return sszSerializeFixed(dataNode, writer, elementsCount);
    } else {
      return sszSerializeVariable(dataNode, writer, elementsCount);
    }
  }

  private int sszSerializeFixed(
      final TreeNode dataNode, final SszWriter writer, final int elementsCount) {
    if (elementSchema instanceof AbstractSszPrimitiveSchema) {
      // Primitive packing: multiple values per 32-byte leaf chunk
      final int chunksCount = getChunks(elementsCount);
      int bytesCnt = 0;
      for (int c = 0; c < chunksCount; c++) {
        final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(c);
        final LeafNode leafNode = (LeafNode) dataNode.get(gIdx);
        final Bytes data = leafNode.getData();
        writer.write(data);
        bytesCnt += data.size();
      }
      return bytesCnt;
    } else {
      // Fixed-size composite elements: one per chunk
      int bytesCnt = 0;
      for (int i = 0; i < elementsCount; i++) {
        final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
        final TreeNode childSubtree = dataNode.get(gIdx);
        bytesCnt += elementSchema.sszSerializeTree(childSubtree, writer);
      }
      return bytesCnt;
    }
  }

  private int sszSerializeVariable(
      final TreeNode dataNode, final SszWriter writer, final int elementsCount) {
    int variableOffset = SszType.SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      final TreeNode childSubtree = dataNode.get(gIdx);
      final int childSize = elementSchema.getSszSize(childSubtree);
      writer.write(SszType.sszLengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      final TreeNode childSubtree = dataNode.get(gIdx);
      elementSchema.sszSerializeTree(childSubtree, writer);
    }
    return variableOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    if (elementSchema.isFixedSize()) {
      return sszDeserializeFixed(reader);
    }
    if (packByteListElements) {
      return sszDeserializePacked(reader);
    }
    return sszDeserializeVariable(reader);
  }

  private TreeNode sszDeserializePacked(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    if (endOffset == 0) {
      return defaultTree;
    }
    final Bytes bytes = reader.read(endOffset);
    final int[] offsets = PackedByteListsUtil.parseUnboundedPackedOffsets(bytes);
    final SszPackedProgressiveByteListsNode packedNode =
        new SszPackedProgressiveByteListsNode(bytes, offsets, this::materializePackedElement);
    checkDeserializedLength(packedNode.getElementCount());
    return BranchNode.create(packedNode, toLengthNode(packedNode.getElementCount()));
  }

  private TreeNode materializePackedElement(final Bytes elementSsz) {
    try (SszReader elementReader = SszReader.fromBytes(elementSsz)) {
      return elementSchema.sszDeserializeTree(elementReader);
    }
  }

  private TreeNode sszDeserializeFixed(final SszReader reader) {
    final int bytesSize = reader.getAvailableBytes();
    final int elementBitSize = getSszElementBitSize();
    if (elementBitSize >= 8) {
      if (bytesSize * 8L % elementBitSize != 0) {
        throw new SszDeserializeException(
            "SSZ sequence length is not multiple of fixed element size");
      }
    }

    if (elementSchema instanceof final AbstractSszPrimitiveSchema<?, ?> primitiveElementSchema) {
      // Primitive packing: multiple values per 32-byte leaf. The element schema validates
      // constrained encodings per chunk (e.g., booleans must be 0 or 1)
      final int elementsCount = (int) (bytesSize * 8L / elementBitSize);
      checkDeserializedLength(elementsCount);
      return createPackedProgressiveListTree(
          reader.read(bytesSize), primitiveElementSchema::createNodeFromSszBytes, elementsCount);
    } else {
      // Fixed-size composite elements: one per chunk
      final int elementsCount = bytesSize / elementSchema.getSszFixedPartSize();
      checkDeserializedLength(elementsCount);
      final int superNodeDepth = getSuperNodeDepth();
      if (superNodeDepth > 0) {
        final TreeNode progressiveTree =
            createSuperNodeDataTree(reader.read(bytesSize), elementsCount, superNodeDepth);
        return BranchNode.create(progressiveTree, toLengthNode(elementsCount));
      }
      final List<TreeNode> childNodes = new ArrayList<>();
      for (int i = 0; i < elementsCount; i++) {
        try (SszReader sszReader = reader.slice(elementSchema.getSszFixedPartSize())) {
          childNodes.add(elementSchema.sszDeserializeTree(sszReader));
        }
      }
      final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(childNodes);
      return BranchNode.create(progressiveTree, toLengthNode(elementsCount));
    }
  }

  private TreeNode sszDeserializeVariable(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    final List<TreeNode> childNodes = new ArrayList<>();
    if (endOffset > 0) {
      final int firstElementOffset = SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE));
      if (firstElementOffset % SszType.SSZ_LENGTH_SIZE != 0
          || firstElementOffset < SszType.SSZ_LENGTH_SIZE) {
        throw new SszDeserializeException("Invalid first element offset: " + firstElementOffset);
      }
      if (firstElementOffset > endOffset) {
        throw new SszDeserializeException(
            "First element offset exceeds available data: "
                + firstElementOffset
                + " > "
                + endOffset);
      }
      final int elementsCount = firstElementOffset / SszType.SSZ_LENGTH_SIZE;
      checkDeserializedLength(elementsCount);
      final List<Integer> elementOffsets = new ArrayList<>(elementsCount + 1);
      elementOffsets.add(firstElementOffset);
      for (int i = 1; i < elementsCount; i++) {
        elementOffsets.add(SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE)));
      }
      elementOffsets.add(endOffset);

      for (int i = 0; i < elementsCount; i++) {
        final int elementSize = elementOffsets.get(i + 1) - elementOffsets.get(i);
        if (elementSize < 0) {
          throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
        }
        try (SszReader sszReader = reader.slice(elementSize)) {
          childNodes.add(elementSchema.sszDeserializeTree(sszReader));
        }
      }
    }
    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(childNodes);
    return BranchNode.create(progressiveTree, toLengthNode(childNodes.size()));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    if (maxLength == Long.MAX_VALUE) {
      // Progressive lists have no max capacity — use Long.MAX_VALUE bits directly
      // to avoid overflow when converting from bytes to bits
      return SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    }
    return ListSchemaUtil.computeListSszLengthBounds(elementSchema, maxLength);
  }

  @Override
  public DeserializableTypeDefinition<SszListT> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode dataTree = getVectorNode(node);
    final TreeNode lengthNode = node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
    final int length = getLength(node);
    final int totalChunks = getChunks(length);

    storeProgressiveDataTree(
        nodeStore,
        maxBranchLevelsSkipped,
        GIndexUtil.gIdxLeftGIndex(rootGIndex),
        dataTree,
        totalChunks);

    nodeStore.storeLeafNode(lengthNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {dataTree.hashTreeRoot(), lengthNode.hashTreeRoot()});
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

    final Bytes32 dataHash = branchData.getChildren()[0];
    final Bytes32 lengthHash = branchData.getChildren()[1];
    final int length =
        nodeSource
            .loadLeafNode(lengthHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);

    final int totalChunks = getChunks(length);
    final long dataRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);

    final TreeNode dataTree =
        loadProgressiveDataTree(nodeSource, dataHash, dataRootGIndex, totalChunks);

    return BranchNode.create(dataTree, toLengthNode(length));
  }

  // ===== Store/Load helpers =====

  private int getSuperNodeDepth() {
    if (elementSchema.isPrimitive() || !elementSchema.isFixedSize()) {
      // SuperNodes only apply to fixed-size composite elements; hint is ignored otherwise
      return 0;
    }
    return hints.getHint(SszSuperNodeHint.class).map(SszSuperNodeHint::getDepth).orElse(0);
  }

  private void storeProgressiveDataTree(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long dataRootGIndex,
      final TreeNode dataTree,
      final int totalChunks) {
    final int superNodeDepth = getSuperNodeDepth();
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        dataTree,
        totalChunks,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            storeLevelSubtree(
                nodeStore,
                maxBranchLevelsSkipped,
                levelGIndex,
                levelSubtree,
                chunksInLevel,
                depth,
                superNodeDepth));
  }

  private void storeLevelSubtree(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long levelGIndex,
      final TreeNode levelSubtree,
      final int chunksInLevel,
      final int depth,
      final int superNodeDepth) {
    if (chunksInLevel == 0) {
      return;
    }

    final int childDepth = Math.max(0, depth - superNodeDepth);

    if (depth == 0) {
      // Level 0: single chunk
      elementSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, levelGIndex, levelSubtree);
    } else if (childDepth == 0) {
      // SuperNode covers entire level subtree
      // NOTE: storing SszSuperNodes via storeLeafNode is unsound for payloads <= 32 bytes: stores
      // assume a leaf's root equals rightPad(data) and skip persisting them (see
      // KvStoreTreeNodeStore.storeLeafNode), which does not hold for computed-root data nodes.
      // Left as-is: no production schema currently stores supernode-hinted structures.
      nodeStore.storeLeafNode(levelSubtree, levelGIndex);
    } else {
      final long lastUsefulGIndex =
          computeLevelLastUsefulGIndex(levelGIndex, chunksInLevel, childDepth, superNodeDepth);

      final StoringUtil.TargetDepthNodeHandler handler =
          superNodeDepth == 0
              ? (targetNode, targetGIndex) ->
                  elementSchema.storeBackingNodes(
                      nodeStore, maxBranchLevelsSkipped, targetGIndex, targetNode)
              : nodeStore::storeLeafNode;

      StoringUtil.storeNodesToDepth(
          nodeStore,
          maxBranchLevelsSkipped,
          levelSubtree,
          levelGIndex,
          childDepth,
          lastUsefulGIndex,
          handler);
    }
  }

  private static long computeLevelLastUsefulGIndex(
      final long levelGIndex,
      final int chunksInLevel,
      final int childDepth,
      final int superNodeDepth) {
    final int chunksPerTargetNode = Math.max(1, 1 << superNodeDepth);
    final int lastUsefulTargetIndex = (chunksInLevel - 1) / chunksPerTargetNode;
    return GIndexUtil.gIdxChildGIndex(levelGIndex, lastUsefulTargetIndex, childDepth);
  }

  private TreeNode loadProgressiveDataTree(
      final TreeNodeSource nodeSource,
      final Bytes32 dataHash,
      final long dataRootGIndex,
      final int totalChunks) {
    final int superNodeDepth = getSuperNodeDepth();
    return ProgressiveTreeUtil.loadProgressiveSpine(
        nodeSource,
        dataHash,
        dataRootGIndex,
        totalChunks,
        (levelHash, levelGIndex, chunksInLevel, depth) ->
            loadLevelSubtree(
                nodeSource, levelHash, levelGIndex, chunksInLevel, depth, superNodeDepth));
  }

  private TreeNode loadLevelSubtree(
      final TreeNodeSource nodeSource,
      final Bytes32 levelHash,
      final long levelGIndex,
      final int chunksInLevel,
      final int depth,
      final int superNodeDepth) {
    final int childDepth = Math.max(0, depth - superNodeDepth);

    if (depth == 0) {
      // Level 0: single chunk
      return loadChunkNode(nodeSource, levelHash, levelGIndex);
    } else if (childDepth == 0) {
      // SuperNode covers entire level subtree
      if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(levelHash)) {
        return new SszSuperNode(depth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
      }
      final Bytes data = nodeSource.loadLeafNode(levelHash, levelGIndex);
      return new SszSuperNode(depth, elementSszSupernodeTemplate.get(), data);
    } else {
      final long lastUsefulGIndex =
          computeLevelLastUsefulGIndex(levelGIndex, chunksInLevel, childDepth, superNodeDepth);

      final TreeNode defaultSubtree = TreeUtil.ZERO_TREES[depth];

      final LoadingUtil.ChildLoader childLoader =
          superNodeDepth == 0
              ? (childNodeSource, childHash, childGIndex) ->
                  loadChunkNode(childNodeSource, childHash, childGIndex)
              : (childNodeSource, childHash, childGIndex) -> {
                if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(childHash)) {
                  return new SszSuperNode(
                      superNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
                }
                final Bytes data = childNodeSource.loadLeafNode(childHash, childGIndex);
                return new SszSuperNode(superNodeDepth, elementSszSupernodeTemplate.get(), data);
              };

      return LoadingUtil.loadNodesToDepth(
          nodeSource,
          levelHash,
          levelGIndex,
          childDepth,
          defaultSubtree,
          lastUsefulGIndex,
          childLoader);
    }
  }

  private TreeNode loadChunkNode(
      final TreeNodeSource nodeSource, final Bytes32 chunkHash, final long chunkGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(chunkHash)) {
      if (elementSchema.isPrimitive()) {
        return LeafNode.ZERO_LEAVES[LeafNode.MAX_BYTE_SIZE];
      } else {
        return elementSchema.getDefaultTree();
      }
    }
    if (elementSchema.isPrimitive()) {
      final Bytes data = nodeSource.loadLeafNode(chunkHash, chunkGIndex);
      return LeafNode.create(data);
    } else {
      return elementSchema.loadBackingNodes(nodeSource, chunkHash, chunkGIndex);
    }
  }

  // ===== SuperNode packing (SszSuperNodeHint) =====
  //
  // With a SszSuperNodeHint of depth s, each progressive level L >= 1 stores its elements in
  // SszSuperNodes of depth min(2L, s) at the bottom of the level subtree, with a plain binary
  // tree of depth max(2L - s, 0) above. The supernode depth is capped per level: a fixed
  // depth-s supernode on a shallow level (2L < s) would hash as a 2^s-chunk subtree instead of
  // the level's 2^2L chunks and mis-slice update gIndices. Level 0 is always a plain element
  // node (store/load routes depth-0 levels through the element schema). Packed and plain trees
  // produce identical hashTreeRoot and serialization.
  //
  // The packed representation is only BUILT by SSZ deserialization (sszDeserializeFixed) and
  // PRESERVED by mutations (via getLevelDefaultSubtree) and by store/load.
  // createTreeFromElements intentionally builds a plain tree: all at-scale production paths
  // enter via SSZ deserialization, store/load or mutation, and fork upgrades must
  // rematerialize lists via serialize -> deserialize rather than createFromElements to keep
  // packing. Consequently only JSON-parsed lists (whose type definition is backed by
  // createFromElements) are unpacked, and such plain trees must not be persisted through
  // storeBackingNodes: the supernode store branches require supernode-backed level subtrees
  // (TreeNodeStore.storeLeafNode rejects branch nodes).

  /**
   * Default (all-zero) balanced subtree for a progressive level, used when mutations materialize a
   * level that doesn't exist yet. For hinted schemas the subtree's bottom nodes are empty {@link
   * SszSuperNode}s so updates keep the packed representation.
   */
  public TreeNode getLevelDefaultSubtree(final int level) {
    final int depth = ProgressiveTreeUtil.levelDepth(level);
    final int superNodeDepth = getSuperNodeDepth();
    if (superNodeDepth == 0 || depth == 0) {
      return TreeUtil.ZERO_TREES[depth];
    }
    final int levelSuperNodeDepth = Math.min(depth, superNodeDepth);
    return TreeUtil.createTree(
        List.of(),
        new SszSuperNode(levelSuperNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY),
        depth - levelSuperNodeDepth);
  }

  /** Builds the progressive data tree from packed element SSZ, with SszSuperNode leaves. */
  private TreeNode createSuperNodeDataTree(
      final Bytes elementsSsz, final int elementsCount, final int superNodeDepth) {
    if (elementsCount == 0) {
      return LeafNode.EMPTY_LEAF;
    }
    final int elementSize = elementSchema.getSszFixedPartSize();
    final int maxLevel = ProgressiveTreeUtil.levelForIndex(elementsCount - 1);
    TreeNode spine = LeafNode.EMPTY_LEAF;
    for (int level = maxLevel; level >= 0; level--) {
      final int from =
          level > 0 ? Math.toIntExact(ProgressiveTreeUtil.cumulativeCapacity(level - 1)) : 0;
      final int to =
          Math.toIntExact(Math.min(elementsCount, ProgressiveTreeUtil.cumulativeCapacity(level)));
      final TreeNode levelSubtree =
          packLevelSubtree(elementsSsz, from, to, level, superNodeDepth, elementSize);
      spine = BranchNode.create(levelSubtree, spine);
    }
    return spine;
  }

  private TreeNode packLevelSubtree(
      final Bytes elementsSsz,
      final int from,
      final int to,
      final int level,
      final int superNodeDepth,
      final int elementSize) {
    final int depth = ProgressiveTreeUtil.levelDepth(level);
    if (depth == 0) {
      // Level 0 is always a plain element node (matches store/load's depth == 0 routing)
      try (SszReader elementReader =
          SszReader.fromBytes(elementsSsz.slice(from * elementSize, elementSize))) {
        return elementSchema.sszDeserializeTree(elementReader);
      }
    }
    final int levelSuperNodeDepth = Math.min(depth, superNodeDepth);
    final int elementsPerSuperNode = 1 << levelSuperNodeDepth;
    final List<TreeNode> superNodes = new ArrayList<>();
    for (int i = from; i < to; i += elementsPerSuperNode) {
      final int count = Math.min(elementsPerSuperNode, to - i);
      superNodes.add(
          new SszSuperNode(
              levelSuperNodeDepth,
              elementSszSupernodeTemplate.get(),
              elementsSsz.slice(i * elementSize, count * elementSize)));
    }
    return TreeUtil.createTree(
        superNodes,
        new SszSuperNode(levelSuperNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY),
        depth - levelSuperNodeDepth);
  }

  // ===== Internal helpers =====

  private int getSszElementBitSize() {
    if (elementSchema.isPrimitive()) {
      return ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize();
    }
    return elementSchema.getSszFixedPartSize() * 8;
  }

  private List<TreeNode> packElementsToChunks(final List<? extends ElementDataT> elements) {
    if (elementSchema.isPrimitive()) {
      // Pack primitive values into 32-byte chunks
      final int bitsPerElement = ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize();
      final int elemPerChunk = 256 / bitsPerElement;
      final int bytesPerElement = bitsPerElement / 8;
      final List<TreeNode> chunks = new ArrayList<>();
      for (int i = 0; i < elements.size(); i += elemPerChunk) {
        final int count = Math.min(elemPerChunk, elements.size() - i);
        final byte[] chunkData = new byte[count * bytesPerElement];
        for (int j = 0; j < count; j++) {
          final Bytes elemBytes = elements.get(i + j).getBackingNode().hashTreeRoot();
          System.arraycopy(
              elemBytes.toArrayUnsafe(), 0, chunkData, j * bytesPerElement, bytesPerElement);
        }
        chunks.add(LeafNode.create(Bytes.wrap(chunkData)));
      }
      return chunks;
    } else {
      return elements.stream().map(SszData::getBackingNode).collect(Collectors.toList());
    }
  }

  @Override
  public Optional<String> getName() {
    return Optional.of("ProgressiveList[" + elementSchema + "]");
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
    final AbstractSszProgressiveListSchema<?, ?> that = (AbstractSszProgressiveListSchema<?, ?>) o;
    return Objects.equals(elementSchema, that.elementSchema)
        && Objects.equals(hints, that.hints)
        && maxLength == that.maxLength;
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementSchema, hints, maxLength);
  }

  @Override
  public String toString() {
    return "ProgressiveList["
        + elementSchema
        + (maxLength == Long.MAX_VALUE ? "" : ", " + maxLength)
        + "]"
        + getHints();
  }
}

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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszContainerTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszContainerSchema<C extends SszContainer>
    implements SszContainerSchema<C> {

  public static class NamedSchema<T extends SszData> {
    private final String name;
    private final SszSchema<T> schema;

    public static <T extends SszData> NamedSchema<T> of(
        final String name, final SszSchema<T> schema) {
      return new NamedSchema<>(name, schema);
    }

    private NamedSchema(final String name, final SszSchema<T> schema) {
      this.name = name;
      this.schema = schema;
    }

    public String getName() {
      return name;
    }

    public SszSchema<T> getSchema() {
      return schema;
    }
  }

  protected static <T extends SszData> NamedSchema<T> namedSchema(
      final SszFieldName fieldName, final SszSchema<T> schema) {
    return namedSchema(fieldName.getSszFieldName(), schema);
  }

  protected static <T extends SszData> NamedSchema<T> namedSchema(
      final String fieldName, final SszSchema<T> schema) {
    return new NamedSchema<>(fieldName, schema);
  }

  private final Supplier<SszLengthBounds> sszLengthBounds =
      Suppliers.memoize(this::computeSszLengthBounds);
  private final Supplier<SszLengthBounds> networkSszLengthBounds =
      Suppliers.memoize(this::computeNetworkSszLengthBounds);
  private final String containerName;
  private final List<String> childrenNames = new ArrayList<>();
  private final Object2IntMap<String> childrenNamesToFieldIndex = new Object2IntOpenHashMap<>();
  private final List<? extends SszSchema<?>> childrenSchemas;
  private final int fixedPartSize;
  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  // Lazy fields — required because progressive mode needs full schema init before tree creation
  private volatile TreeNode defaultTree;
  private volatile Long treeWidth;

  // Progressive mode fields (null for regular containers)
  private final boolean[] activeFields;
  private final int[] fieldToTreePosition;
  private final int[] slotToFieldIndex;
  private final LeafNode activeFieldsLeafNode;

  // ===== Regular (non-progressive) constructors =====

  protected AbstractSszContainerSchema(
      final String name, final List<? extends NamedSchema<?>> childrenSchemas) {
    this.containerName = name;
    initFieldNames(childrenSchemas);
    this.childrenSchemas = childrenSchemas.stream().map(NamedSchema::getSchema).toList();
    this.activeFields = null;
    this.fieldToTreePosition = null;
    this.slotToFieldIndex = null;
    this.activeFieldsLeafNode = null;
    this.fixedPartSize = calcSszFixedPartSize();
    this.jsonTypeDefinition = SszContainerTypeDefinition.createFor(this);
  }

  protected AbstractSszContainerSchema(final List<SszSchema<?>> childrenSchemas) {
    this.containerName = "";
    for (int i = 0; i < childrenSchemas.size(); i++) {
      final String name = "field-" + i;
      childrenNamesToFieldIndex.put(name, i);
      childrenNames.add(name);
    }
    this.childrenSchemas = childrenSchemas;
    this.activeFields = null;
    this.fieldToTreePosition = null;
    this.slotToFieldIndex = null;
    this.activeFieldsLeafNode = null;
    this.fixedPartSize = calcSszFixedPartSize();
    this.jsonTypeDefinition = SszContainerTypeDefinition.createFor(this);
  }

  // ===== Progressive constructor =====

  protected AbstractSszContainerSchema(
      final String name,
      final boolean[] activeFields,
      final List<? extends NamedSchema<?>> childrenSchemas) {
    checkArgument(activeFields.length > 0, "activeFields must not be empty");
    checkArgument(
        activeFields[activeFields.length - 1], "Last element of activeFields must be true");
    checkArgument(activeFields.length <= 256, "activeFields length must be <= 256");

    int activeCount = 0;
    for (boolean b : activeFields) {
      if (b) {
        activeCount++;
      }
    }
    checkArgument(
        activeCount == childrenSchemas.size(),
        "Number of active fields (%s) must match number of field schemas (%s)",
        activeCount,
        childrenSchemas.size());

    this.containerName = name;
    this.activeFields = activeFields.clone();
    this.fieldToTreePosition = new int[childrenSchemas.size()];
    this.slotToFieldIndex = new int[activeFields.length];

    int fieldIdx = 0;
    for (int slot = 0; slot < activeFields.length; slot++) {
      if (activeFields[slot]) {
        final NamedSchema<?> ns = childrenSchemas.get(fieldIdx);
        childrenNames.add(ns.getName());
        if (childrenNamesToFieldIndex.containsKey(ns.getName())) {
          throw new IllegalArgumentException(
              "Duplicate field name detected for field " + ns.getName() + " at index " + fieldIdx);
        }
        childrenNamesToFieldIndex.put(ns.getName(), fieldIdx);
        this.fieldToTreePosition[fieldIdx] = slot;
        this.slotToFieldIndex[slot] = fieldIdx;
        fieldIdx++;
      } else {
        this.slotToFieldIndex[slot] = -1;
      }
    }

    this.childrenSchemas = childrenSchemas.stream().map(NamedSchema::getSchema).toList();
    this.activeFieldsLeafNode = createActiveFieldsLeafNode(activeFields);
    this.fixedPartSize = calcSszFixedPartSize();
    this.jsonTypeDefinition = SszContainerTypeDefinition.createFor(this);
  }

  // ===== Progressive mode helpers =====

  public boolean isProgressiveMode() {
    return activeFields != null;
  }

  public boolean[] getActiveFields() {
    return activeFields != null ? activeFields.clone() : null;
  }

  public int getTreePosition(final int fieldIndex) {
    if (!isProgressiveMode()) {
      throw new UnsupportedOperationException("Not a progressive container");
    }
    return fieldToTreePosition[fieldIndex];
  }

  private static LeafNode createActiveFieldsLeafNode(final boolean[] activeFields) {
    final int byteLen = (activeFields.length + 7) / 8;
    final byte[] bytes = new byte[byteLen];
    for (int i = 0; i < activeFields.length; i++) {
      if (activeFields[i]) {
        bytes[i / 8] |= (byte) (1 << (i % 8));
      }
    }
    return LeafNode.create(Bytes.wrap(bytes));
  }

  private List<TreeNode> createSlotChunks(final List<TreeNode> activeFieldNodes) {
    final List<TreeNode> slotChunks = new ArrayList<>(activeFields.length);
    int fieldIdx = 0;
    for (boolean activeField : activeFields) {
      if (activeField) {
        slotChunks.add(activeFieldNodes.get(fieldIdx));
        fieldIdx++;
      } else {
        slotChunks.add(LeafNode.EMPTY_LEAF);
      }
    }
    return slotChunks;
  }

  // ===== Field name initialization =====

  private void initFieldNames(final List<? extends NamedSchema<?>> childrenSchemas) {
    for (int i = 0; i < childrenSchemas.size(); i++) {
      final NamedSchema<?> childSchema = childrenSchemas.get(i);
      if (childrenNamesToFieldIndex.containsKey(childSchema.getName())) {
        throw new IllegalArgumentException(
            "Duplicate field name detected for field " + childSchema.getName() + " at index " + i);
      }
      childrenNamesToFieldIndex.put(childSchema.getName(), i);
      childrenNames.add(childSchema.getName());
    }
  }

  // ===== Tree creation =====

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(fieldValues.size() == getFieldsCount(), "Wrong number of filed values");
    final List<TreeNode> fieldNodes = fieldValues.stream().map(SszData::getBackingNode).toList();
    if (isProgressiveMode()) {
      final List<TreeNode> slotChunks = createSlotChunks(fieldNodes);
      final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(slotChunks);
      return BranchNode.create(progressiveTree, activeFieldsLeafNode);
    }
    return TreeUtil.createTree(fieldNodes);
  }

  @Override
  public C getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public Optional<String> getName() {
    return Optional.of(containerName);
  }

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  @Override
  public long treeWidth() {
    if (isProgressiveMode()) {
      throw new UnsupportedOperationException(
          "Progressive containers don't have a fixed tree width");
    }
    if (treeWidth == null) {
      treeWidth = SszContainerSchema.super.treeWidth();
    }
    return treeWidth;
  }

  @Override
  public int treeDepth() {
    if (isProgressiveMode()) {
      throw new UnsupportedOperationException(
          "Progressive containers don't have a fixed tree depth");
    }
    return SszContainerSchema.super.treeDepth();
  }

  @Override
  public long maxChunks() {
    if (isProgressiveMode()) {
      throw new UnsupportedOperationException(
          "Progressive containers don't have a fixed maxChunks");
    }
    return SszContainerSchema.super.maxChunks();
  }

  private TreeNode createDefaultTree() {
    final List<TreeNode> defaultChildren = new ArrayList<>(getFieldsCount());
    for (int i = 0; i < getFieldsCount(); i++) {
      defaultChildren.add(getChildSchema(i).getDefault().getBackingNode());
    }
    if (isProgressiveMode()) {
      final List<TreeNode> slotChunks = createSlotChunks(defaultChildren);
      final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(slotChunks);
      return BranchNode.create(progressiveTree, activeFieldsLeafNode);
    }
    return TreeUtil.createTree(defaultChildren);
  }

  // ===== Generalized index =====

  @Override
  public long getChildGeneralizedIndex(final long fieldIndex) {
    if (isProgressiveMode()) {
      final int treePosition = fieldToTreePosition[(int) fieldIndex];
      final long progressiveGIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(treePosition);
      return GIndexUtil.gIdxCompose(GIndexUtil.LEFT_CHILD_G_INDEX, progressiveGIdx);
    }
    return SszContainerSchema.super.getChildGeneralizedIndex(fieldIndex);
  }

  // ===== Schema accessors =====

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return childrenSchemas.get(index);
  }

  @Override
  public int getFieldIndex(final String fieldName) {
    return childrenNamesToFieldIndex.getOrDefault(fieldName, -1);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node);

  @Override
  public long getMaxLength() {
    return childrenSchemas.size();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszContainerSchema<?> that = (AbstractSszContainerSchema<?>) o;
    return childrenSchemas.equals(that.childrenSchemas)
        && Arrays.equals(activeFields, that.activeFields)
        && Objects.equals(
            getNetworkSszLengthBytesUpperBound(), that.getNetworkSszLengthBytesUpperBound());
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(childrenSchemas, getNetworkSszLengthBytesUpperBound());
    result = 31 * result + Arrays.hashCode(activeFields);
    return result;
  }

  @Override
  public boolean isFixedSize() {
    for (int i = 0; i < getFieldsCount(); i++) {
      if (!getChildSchema(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getSszFixedPartSize() {
    return fixedPartSize;
  }

  protected int calcSszFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      size += childType.isFixedSize() ? childType.getSszFixedPartSize() : SszType.SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (isFixedSize()) {
      return 0;
    }
    int size = 0;
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (!childType.isFixedSize()) {
        size += childType.getSszSize(node.get(getChildGeneralizedIndex(i)));
      }
    }
    return size;
  }

  @Override
  public List<? extends SszSchema<?>> getFieldSchemas() {
    return childrenSchemas;
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final int sszFixedPartSize = getSszFixedPartSize();
    int variableChildOffset = sszFixedPartSize;
    int[] variableSizes = new int[getFieldsCount()];
    for (int i = 0; i < getFieldsCount(); i++) {
      TreeNode childSubtree = node.get(getChildGeneralizedIndex(i));
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == childType.getSszFixedPartSize();
      } else {
        writer.write(SszType.sszLengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getChildGeneralizedIndex(i));
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == variableSizes[i];
      }
    }
    return variableChildOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    final int childCount = getFieldsCount();
    final Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    final IntList variableChildrenOffsets = new IntArrayList(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getSszFixedPartSize())) {
          fixedChildrenSubtrees.add(childType.sszDeserializeTree(sszReader));
        }
      } else {
        int childOffset = SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE));
        variableChildrenOffsets.add(childOffset);
      }
    }

    if (variableChildrenOffsets.isEmpty()) {
      if (reader.getAvailableBytes() > 0) {
        throw new SszDeserializeException("Invalid SSZ: unread bytes for fixed size container");
      }
    } else {
      if (variableChildrenOffsets.getInt(0) != endOffset - reader.getAvailableBytes()) {
        throw new SszDeserializeException(
            "First variable element offset doesn't match the end of fixed part");
      }
    }

    variableChildrenOffsets.add(endOffset);

    final ArrayDeque<Integer> variableChildrenSizes =
        new ArrayDeque<>(variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes.add(
          variableChildrenOffsets.getInt(i + 1) - variableChildrenOffsets.getInt(i));
    }

    if (variableChildrenSizes.stream().anyMatch(s -> s < 0)) {
      throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
    }

    final List<TreeNode> fieldNodes = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        fieldNodes.add(fixedChildrenSubtrees.remove());
      } else {
        try (SszReader sszReader = reader.slice(variableChildrenSizes.remove())) {
          fieldNodes.add(childType.sszDeserializeTree(sszReader));
        }
      }
    }

    if (isProgressiveMode()) {
      final List<TreeNode> slotChunks = createSlotChunks(fieldNodes);
      return BranchNode.create(
          ProgressiveTreeUtil.createProgressiveTree(slotChunks), activeFieldsLeafNode);
    }
    return TreeUtil.createTree(fieldNodes);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds.get();
  }

  @Override
  public SszLengthBounds getNetworkSszLengthBounds() {
    return networkSszLengthBounds.get();
  }

  private SszLengthBounds computeSszLengthBounds() {
    return IntStream.range(0, getFieldsCount())
        .mapToObj(this::getChildSchema)
        .map(t -> t.getSszLengthBounds().addBytes(t.isFixedSize() ? 0 : SszType.SSZ_LENGTH_SIZE))
        .map(SszLengthBounds::ceilToBytes)
        .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);
  }

  private SszLengthBounds computeNetworkSszLengthBounds() {
    final OptionalLong upperBound = getNetworkSszLengthBytesUpperBound();
    return upperBound.isPresent()
        ? getSszLengthBounds().withMaxBytesUpperBound(upperBound.getAsLong())
        : getSszLengthBounds();
  }

  protected void validateNetworkSszLengthBytesUpperBound() {
    computeNetworkSszLengthBounds();
  }

  @Override
  public String getContainerName() {
    return !containerName.isEmpty() ? containerName : getClass().getName();
  }

  @Override
  public List<String> getFieldNames() {
    return childrenNames;
  }

  @Override
  public String toString() {
    return getContainerName();
  }

  // ===== Store/load backing nodes (progressive override) =====

  @Override
  public void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    if (isProgressiveMode()) {
      throw new UnsupportedOperationException(
          "Store/load backing nodes not yet supported for progressive containers");
    }
    SszContainerSchema.super.storeChildNode(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    if (!isProgressiveMode()) {
      SszContainerSchema.super.storeBackingNodes(
          nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
      return;
    }
    final TreeNode progressiveDataTree = node.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final TreeNode activeFieldsNode = node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
    final long dataRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        progressiveDataTree,
        activeFields.length,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            storeContainerLevelSubtree(
                nodeStore,
                maxBranchLevelsSkipped,
                levelGIndex,
                levelSubtree,
                chunksInLevel,
                depth));
    nodeStore.storeLeafNode(activeFieldsNode, GIndexUtil.gIdxRightGIndex(rootGIndex));
    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {progressiveDataTree.hashTreeRoot(), activeFieldsNode.hashTreeRoot()});
  }

  private void storeContainerLevelSubtree(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long levelGIndex,
      final TreeNode levelSubtree,
      final int chunksInLevel,
      final int depth) {
    if (chunksInLevel == 0) {
      return;
    }
    final int levelNum = depth / 2; // depth = 2 * level per ProgressiveTreeUtil
    final int levelStartSlot =
        levelNum > 0 ? (int) ProgressiveTreeUtil.cumulativeCapacity(levelNum - 1) : 0;
    if (depth == 0) {
      final int fieldIdx = slotToFieldIndex[levelStartSlot];
      if (fieldIdx >= 0) {
        getChildSchema(fieldIdx)
            .storeBackingNodes(nodeStore, maxBranchLevelsSkipped, levelGIndex, levelSubtree);
      }
    } else {
      final long lastUsefulGIndex =
          GIndexUtil.gIdxChildGIndex(levelGIndex, chunksInLevel - 1, depth);
      StoringUtil.storeNodesToDepth(
          nodeStore,
          maxBranchLevelsSkipped,
          levelSubtree,
          levelGIndex,
          depth,
          lastUsefulGIndex,
          (targetNode, targetGIndex) -> {
            final int posInLevel = GIndexUtil.gIdxChildIndexFromGIndex(targetGIndex, depth);
            final int slot = levelStartSlot + posInLevel;
            final int fieldIdx = slotToFieldIndex[slot];
            if (fieldIdx >= 0) {
              getChildSchema(fieldIdx)
                  .storeBackingNodes(nodeStore, maxBranchLevelsSkipped, targetGIndex, targetNode);
            }
          });
    }
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (!isProgressiveMode()) {
      return SszContainerSchema.super.loadBackingNodes(nodeSource, rootHash, rootGIndex);
    }
    final TreeNodeSource.CompressedBranchInfo branch =
        nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branch.getChildren().length == 2, "Container root node must have exactly 2 children");
    checkState(branch.getDepth() == 1, "Container root node must have depth of 1");
    final Bytes32 dataHash = branch.getChildren()[0];
    final long dataRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);
    final TreeNode progressiveDataTree =
        ProgressiveTreeUtil.loadProgressiveSpine(
            nodeSource,
            dataHash,
            dataRootGIndex,
            activeFields.length,
            (levelHash, levelGIndex, chunksInLevel, depth) ->
                loadContainerLevelSubtree(
                    nodeSource, levelHash, levelGIndex, chunksInLevel, depth));
    return BranchNode.create(progressiveDataTree, activeFieldsLeafNode);
  }

  private TreeNode loadContainerLevelSubtree(
      final TreeNodeSource nodeSource,
      final Bytes32 levelHash,
      final long levelGIndex,
      final int chunksInLevel,
      final int depth) {
    final int levelNum = depth / 2;
    final int levelStartSlot =
        levelNum > 0 ? (int) ProgressiveTreeUtil.cumulativeCapacity(levelNum - 1) : 0;
    if (depth == 0) {
      final int fieldIdx = slotToFieldIndex[levelStartSlot];
      if (fieldIdx < 0) {
        return LeafNode.EMPTY_LEAF;
      }
      return loadChunkNode(nodeSource, levelHash, levelGIndex, getChildSchema(fieldIdx));
    }
    final long lastUsefulGIndex = GIndexUtil.gIdxChildGIndex(levelGIndex, chunksInLevel - 1, depth);
    return LoadingUtil.loadNodesToDepth(
        nodeSource,
        levelHash,
        levelGIndex,
        depth,
        TreeUtil.ZERO_TREES[depth],
        lastUsefulGIndex,
        (src, childHash, childGIndex) -> {
          final int posInLevel = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, depth);
          final int slot = levelStartSlot + posInLevel;
          final int fieldIdx = slotToFieldIndex[slot];
          if (fieldIdx < 0) {
            return LeafNode.EMPTY_LEAF;
          }
          return loadChunkNode(src, childHash, childGIndex, getChildSchema(fieldIdx));
        });
  }

  private TreeNode loadChunkNode(
      final TreeNodeSource nodeSource,
      final Bytes32 chunkHash,
      final long chunkGIndex,
      final SszSchema<?> fieldSchema) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(chunkHash) || chunkHash.equals(Bytes32.ZERO)) {
      return fieldSchema.getDefaultTree();
    }
    return fieldSchema.loadBackingNodes(nodeSource, chunkHash, chunkGIndex);
  }
}

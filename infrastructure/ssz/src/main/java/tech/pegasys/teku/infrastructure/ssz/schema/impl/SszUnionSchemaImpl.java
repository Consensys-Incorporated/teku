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

import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszUnion;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUnionImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class SszUnionSchemaImpl<SszUnionT extends SszUnion>
    implements SszUnionSchema<SszUnionT> {

  private static final int MAX_SELECTOR = 127;
  private static final int DEFAULT_SELECTOR = 0;

  private static LeafNode createSelectorNode(final int selector) {
    assert selector <= MAX_SELECTOR;
    return LeafNode.create(Bytes.of((byte) selector));
  }

  public static SszUnionSchema<SszUnion> createGenericSchema(
      final List<SszSchema<?>> childrenSchemas) {
    return new SszUnionSchemaImpl<>(childrenSchemas) {
      @Override
      public SszUnion createFromBackingNode(final TreeNode node) {
        return new SszUnionImpl(this, node);
      }
    };
  }

  private final List<SszSchema<?>> childrenSchemas;
  private final TreeNode defaultTree;
  private final Supplier<SszLengthBounds> lengthBounds =
      Suppliers.memoize(this::calcSszLengthBounds);

  public SszUnionSchemaImpl(final List<SszSchema<?>> childrenSchemas) {
    // Because of zero indexing, the max selector is one less than the maximum list size.
    checkArgument(childrenSchemas.size() <= MAX_SELECTOR + 1, "Too many child types");
    checkArgument(
        childrenSchemas.stream()
            .skip(1)
            .allMatch(schema -> !schema.equals(SszPrimitiveSchemas.NONE_SCHEMA)),
        "None is allowed for zero selector only");
    this.childrenSchemas = childrenSchemas;
    defaultTree =
        createUnionNode(childrenSchemas.get(DEFAULT_SELECTOR).getDefaultTree(), DEFAULT_SELECTOR);
  }

  @SuppressWarnings("unchecked")
  @Override
  public DeserializableTypeDefinition<SszUnionT> getJsonTypeDefinition() {
    final DeserializableObjectTypeDefinitionBuilder<SszUnionT, UnionBuilder<SszUnionT>> builder =
        DeserializableTypeDefinition.object();
    builder.initializer(() -> new UnionBuilder<>(this)).finisher(UnionBuilder::build);
    for (int i = 0; i < childrenSchemas.size(); i++) {
      final int selector = i;
      builder.withOptionalField(
          Integer.toString(i),
          (DeserializableTypeDefinition<Object>) childrenSchemas.get(i).getJsonTypeDefinition(),
          value ->
              Optional.<Object>ofNullable(value.getValue())
                  .filter(v -> value.getSelector() == selector),
          (unionBuilder, maybeValue) -> unionBuilder.set(selector, maybeValue));
    }
    return builder.build();
  }

  private static class UnionBuilder<SszUnionT extends SszUnion> {
    private final SszUnionSchema<SszUnionT> schema;

    private Integer selector;
    private SszData value;

    private UnionBuilder(final SszUnionSchema<SszUnionT> schema) {
      this.schema = schema;
    }

    public void set(final int selector, final Optional<?> maybeValue) {
      maybeValue.ifPresent(
          value -> {
            checkArgument(this.value == null, "Cannot set two values for SszUnion");
            checkArgument(value instanceof SszData, "Got invalid value for SszUnion");
            this.selector = selector;
            this.value = (SszData) value;
          });
    }

    public SszUnionT build() {
      return schema.createFromValue(selector, value);
    }
  }

  @Override
  public List<SszSchema<?>> getChildrenSchemas() {
    return childrenSchemas;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  @SuppressWarnings("unchecked")
  public abstract SszUnionT createFromBackingNode(TreeNode node);

  @Override
  public SszUnionT createFromValue(final int selector, final SszData value) {
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    checkArgument(
        getChildSchema(selector).equals(value.getSchema()),
        "Incompatible value schema for supplied selector");
    return createFromBackingNode(createUnionNode(value.getBackingNode(), selector));
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    int selector = getSelector(node);
    return childrenSchemas.get(selector).getSszSize(getValueNode(node));
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    int selector = getSelector(node);
    writer.write(Bytes.of(selector));
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    int valueSszLength = valueSchema.sszSerializeTree(getValueNode(node), writer);
    return valueSszLength + SELECTOR_SIZE_BYTES;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) throws SszDeserializeException {
    int selector = reader.read(1).get(0) & 0xFF;
    if (selector >= getTypesCount()) {
      throw new SszDeserializeException(
          "Invalid selector " + selector + " for Union schema: " + this);
    }
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    TreeNode valueNode = valueSchema.sszDeserializeTree(reader);

    return createUnionNode(valueNode, selector);
  }

  public int getSelectorFromSelectorNode(final TreeNode selectorNode) {
    checkArgument(selectorNode instanceof LeafDataNode, "Invalid selector node");
    LeafDataNode dataNode = (LeafDataNode) selectorNode;
    Bytes bytes = dataNode.getData();
    checkArgument(bytes.size() == SELECTOR_SIZE_BYTES, "Invalid selector node");
    int selector = bytes.get(0) & 0xFF;
    checkArgument(selector <= MAX_SELECTOR, "Selector exceeds max value");
    return selector;
  }

  public final TreeNode getValueNode(final TreeNode unionNode) {
    return unionNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  private TreeNode getSelectorNode(final TreeNode unionNode) {
    return unionNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
  }

  public int getSelector(final TreeNode unionNode) {
    int selector = getSelectorFromSelectorNode(getSelectorNode(unionNode));
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    return selector;
  }

  private TreeNode createUnionNode(final TreeNode valueNode, final int selector) {
    return BranchNode.create(valueNode, createSelectorNode(selector));
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    TreeNode selectorNode = getSelectorNode(node);
    TreeNode valueNode = getValueNode(node);
    int selector = getSelectorFromSelectorNode(selectorNode);
    nodeStore.storeLeafNode(selectorNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    getChildSchema(selector)
        .storeBackingNodes(
            nodeStore, maxBranchLevelsSkipped, GIndexUtil.gIdxLeftGIndex(rootGIndex), valueNode);

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {valueNode.hashTreeRoot(), selectorNode.hashTreeRoot()});
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }

    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "Union root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "Union root node must have depth of 1");
    final Bytes32 valueHash = branchData.getChildren()[0];
    final Bytes32 selectorHash = branchData.getChildren()[1];
    final int selector =
        nodeSource
            .loadLeafNode(selectorHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);
    checkState(selector < getTypesCount(), "Selector is out of bounds");
    SszSchema<?> childSchema = getChildSchema(selector);
    TreeNode valueNode =
        childSchema.loadBackingNodes(nodeSource, valueHash, GIndexUtil.gIdxLeftGIndex(rootGIndex));
    return createUnionNode(valueNode, selector);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return lengthBounds.get();
  }

  private SszLengthBounds calcSszLengthBounds() {
    return IntStream.range(0, getTypesCount())
        .mapToObj(this::getChildSchema)
        .map(SszType::getSszLengthBounds)
        .reduce(SszLengthBounds::or)
        .orElseThrow()
        .addBytes(SELECTOR_SIZE_BYTES)
        .ceilToBytes();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszUnionSchemaImpl)) {
      return false;
    }
    SszUnionSchemaImpl<?> that = (SszUnionSchemaImpl<?>) o;
    return Objects.equal(childrenSchemas, that.childrenSchemas);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(childrenSchemas);
  }

  @Override
  public String toString() {
    return "Union" + childrenSchemas;
  }
}

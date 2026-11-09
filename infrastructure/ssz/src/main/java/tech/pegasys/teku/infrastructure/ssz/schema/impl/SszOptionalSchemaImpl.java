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
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.impl.SszOptionalImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
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

/**
 * SszOptional schema according to the <a
 * href="https://eips.ethereum.org/EIPS/eip-6475">EIP-6475</a>
 */
public class SszOptionalSchemaImpl<ElementDataT extends SszData>
    implements SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> {

  private static final byte IS_PRESENT_PREFIX = 1;
  private static final int PREFIX_SIZE_BYTES = 1;
  private static final LeafNode EMPTY_OPTIONAL_LEAF = LeafNode.create(Bytes.of(0));
  private static final LeafNode PRESENT_OPTIONAL_LEAF =
      LeafNode.create(Bytes.of(IS_PRESENT_PREFIX));
  private static final TreeNode DEFAULT_TREE = createTreeNode(LeafNode.EMPTY_LEAF, false);

  private static LeafNode createOptionalNode(final boolean isPresent) {
    return isPresent ? PRESENT_OPTIONAL_LEAF : EMPTY_OPTIONAL_LEAF;
  }

  public static <ElementDataT extends SszData>
      SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> createGenericSchema(
          final SszSchema<ElementDataT> childSchema) {
    return new SszOptionalSchemaImpl<>(childSchema);
  }

  private final SszSchema<ElementDataT> childSchema;
  private final Supplier<SszLengthBounds> lengthBounds =
      Suppliers.memoize(this::calcSszLengthBounds);

  public SszOptionalSchemaImpl(final SszSchema<ElementDataT> childSchema) {
    this.childSchema = childSchema;
  }

  @Override
  public DeserializableTypeDefinition<SszOptional<ElementDataT>> getJsonTypeDefinition() {
    final DeserializableObjectTypeDefinitionBuilder<
            SszOptional<ElementDataT>, OptionalBuilder<ElementDataT, SszOptional<ElementDataT>>>
        builder = DeserializableTypeDefinition.object();
    builder.initializer(() -> new OptionalBuilder<>(this)).finisher(OptionalBuilder::build);
    builder.withOptionalField(
        "Optional[" + childSchema.getJsonTypeDefinition().getTypeName().orElse("") + "]",
        childSchema.getJsonTypeDefinition(),
        SszOptional::getValue,
        OptionalBuilder::set);
    return builder.build();
  }

  private static class OptionalBuilder<
      ElementDataT extends SszData, SszOptionalT extends SszOptional<ElementDataT>> {
    private final SszOptionalSchema<ElementDataT, SszOptionalT> schema;

    private Optional<ElementDataT> value;

    private OptionalBuilder(final SszOptionalSchema<ElementDataT, SszOptionalT> schema) {
      this.schema = schema;
    }

    public void set(final Optional<ElementDataT> maybeValue) {
      this.value = maybeValue;
    }

    public SszOptional<ElementDataT> build() {
      return schema.createFromValue(value);
    }
  }

  @Override
  public SszSchema<ElementDataT> getChildSchema() {
    return childSchema;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public TreeNode getDefaultTree() {
    return DEFAULT_TREE;
  }

  @Override
  public SszOptional<ElementDataT> createFromBackingNode(final TreeNode node) {
    return new SszOptionalImpl<>(this, node);
  }

  @Override
  public SszOptional<ElementDataT> createFromValue(final Optional<ElementDataT> value) {
    if (value.isEmpty()) {
      return createFromBackingNode(getDefaultTree());
    }
    checkArgument(getChildSchema().equals(value.get().getSchema()), "Incompatible value schema");
    return createFromBackingNode(createTreeNode(value.get().getBackingNode(), true));
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    return isPresent(node) ? childSchema.getSszSize(getValueNode(node)) + PREFIX_SIZE_BYTES : 0;
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final TreeNode optionalNode = getOptionalNode(node);
    final boolean isPresent = getIsPresentFromOptionalNode(optionalNode);
    if (!isPresent) {
      return 0;
    }
    writer.write(Bytes.of(IS_PRESENT_PREFIX));
    final int valueSszLength = childSchema.sszSerializeTree(getValueNode(node), writer);
    return valueSszLength + PREFIX_SIZE_BYTES;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) throws SszDeserializeException {
    if (reader.getAvailableBytes() == 0) {
      return getDefaultTree();
    }
    final int isPresent = reader.read(1).get(0) & 0xFF;
    if (isPresent != IS_PRESENT_PREFIX) {
      throw new SszDeserializeException(
          "Invalid prefix " + isPresent + " for Optional schema: " + this);
    }
    final TreeNode valueNode = childSchema.sszDeserializeTree(reader);
    return createTreeNode(valueNode, true);
  }

  public boolean getIsPresentFromOptionalNode(final TreeNode optionalNode) {
    checkArgument(optionalNode instanceof LeafDataNode, "Invalid optional node");
    final LeafDataNode dataNode = (LeafDataNode) optionalNode;
    final Bytes bytes = dataNode.getData();
    checkArgument(bytes.size() == PREFIX_SIZE_BYTES, "Invalid optional node");
    final int isPresent = bytes.get(0) & 0xFF;
    checkArgument(isPresent <= IS_PRESENT_PREFIX, "Incorrect optional prefix");
    return isPresent == IS_PRESENT_PREFIX;
  }

  @Override
  public TreeNode getValueNode(final TreeNode node) {
    return node.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  @Override
  public boolean isPresent(final TreeNode node) {
    return getIsPresentFromOptionalNode(getOptionalNode(node));
  }

  private TreeNode getOptionalNode(final TreeNode node) {
    return node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
  }

  private static TreeNode createTreeNode(final TreeNode valueNode, final boolean isPresent) {
    return BranchNode.create(valueNode, createOptionalNode(isPresent));
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode optionalNode = getOptionalNode(node);
    final TreeNode valueNode = getValueNode(node);
    final boolean isPresent = getIsPresentFromOptionalNode(optionalNode);
    if (!isPresent) {
      return;
    }
    nodeStore.storeLeafNode(optionalNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    childSchema.storeBackingNodes(
        nodeStore, maxBranchLevelsSkipped, GIndexUtil.gIdxLeftGIndex(rootGIndex), valueNode);

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {valueNode.hashTreeRoot(), optionalNode.hashTreeRoot()});
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }

    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "Optional root node must have exactly two child");
    checkState(branchData.getDepth() == 1, "Optional root node must have depth of 1");
    final Bytes32 valueHash = branchData.getChildren()[0];
    final Bytes32 optionalHash = branchData.getChildren()[1];
    final int isPresent =
        nodeSource
            .loadLeafNode(optionalHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);
    checkState(isPresent <= IS_PRESENT_PREFIX, "Selector is out of bounds");
    final TreeNode valueNode =
        childSchema.loadBackingNodes(nodeSource, valueHash, GIndexUtil.gIdxLeftGIndex(rootGIndex));
    return createTreeNode(valueNode, isPresent == IS_PRESENT_PREFIX);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return lengthBounds.get();
  }

  private SszLengthBounds calcSszLengthBounds() {
    return SszLengthBounds.ZERO.or(childSchema.getSszLengthBounds().addBytes(PREFIX_SIZE_BYTES));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszOptionalSchemaImpl)) {
      return false;
    }
    final SszOptionalSchemaImpl<?> that = (SszOptionalSchemaImpl<?>) o;
    return Objects.equal(childSchema, that.childSchema);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(childSchema);
  }

  @Override
  public String toString() {
    return "SszOptional[" + childSchema + "]";
  }
}

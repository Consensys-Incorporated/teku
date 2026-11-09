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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveCollection;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveCollection;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszMutableCollection;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema.PackedNodeUpdate;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;

public abstract class AbstractSszMutablePrimitiveCollection<
        ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends AbstractSszMutableCollection<SszElementT, SszElementT>
    implements SszMutablePrimitiveCollection<ElementT, SszElementT> {

  private final SszPrimitiveSchema<ElementT, SszElementT> elementSchemaCache;

  @SuppressWarnings("unchecked")
  protected AbstractSszMutablePrimitiveCollection(
      final AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchemaCache = (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  @Override
  public SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return elementSchemaCache;
  }

  @Override
  protected void validateChildSchema(final int index, final SszElementT value) {
    // no need to check primitive value schema
  }

  /**
   * Overridden to pack primitive values to tree nodes. E.g. a tree node can contain up to 8 {@link
   * SszBytes4} values
   */
  @Override
  protected TreeUpdates changesToNewNodes(
      final Stream<Map.Entry<Integer, SszElementT>> newChildValues, final TreeNode original) {
    SszCollectionSchema<?, ?> type = getSchema();
    int elementsPerChunk = type.getElementsPerChunk();

    final List<NodeUpdate<ElementT, SszElementT>> nodeUpdates = new ArrayList<>();
    newChildValues.forEach(
        new Consumer<>() {
          private int prevChildNodeIndex = 0;
          private NodeUpdate<ElementT, SszElementT> curNodeUpdate = null;

          @Override
          public void accept(final Map.Entry<Integer, SszElementT> entry) {
            int childIndex = entry.getKey();
            int childNodeIndex = childIndex / elementsPerChunk;

            if (curNodeUpdate == null || childNodeIndex != prevChildNodeIndex) {
              long gIndex = type.getChildGeneralizedIndex(childNodeIndex);
              curNodeUpdate = new NodeUpdate<>(gIndex, elementsPerChunk);
              nodeUpdates.add(curNodeUpdate);
              prevChildNodeIndex = childNodeIndex;
            }
            curNodeUpdate.addUpdate(childIndex % elementsPerChunk, entry.getValue());
          }
        });

    LongList gIndices = new LongArrayList();
    List<TreeNode> newValues = new ArrayList<>();
    SszPrimitiveSchema<ElementT, SszElementT> elementType = getPrimitiveElementSchema();
    for (NodeUpdate<ElementT, SszElementT> nodeUpdate : nodeUpdates) {
      long gIndex = nodeUpdate.getNodeGIndex();
      TreeNode originalNode =
          nodeUpdate.getUpdates().size() < elementsPerChunk
              ? original.get(gIndex)
              : LeafNode.EMPTY_LEAF;
      TreeNode newNode = elementType.updatePackedNode(originalNode, nodeUpdate.getUpdates());
      newValues.add(newNode);
      gIndices.add(gIndex);
    }

    return new TreeUpdates(gIndices, newValues);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveCollection<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveCollection<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutablePrimitiveCollection<ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException();
  }

  private static class NodeUpdate<ElementT, SszElementT extends SszPrimitive<ElementT>> {
    private final List<PackedNodeUpdate<ElementT, SszElementT>> updates;
    private final long nodeGIndex;

    public NodeUpdate(final long nodeGIndex, final int maxElementsPerChunk) {
      this.updates = new ArrayList<>(maxElementsPerChunk);
      this.nodeGIndex = nodeGIndex;
    }

    public void addUpdate(final int internalNodeIndex, final SszElementT newValue) {
      updates.add(new PackedNodeUpdate<>(internalNodeIndex, newValue));
    }

    public long getNodeGIndex() {
      return nodeGIndex;
    }

    public List<PackedNodeUpdate<ElementT, SszElementT>> getUpdates() {
      return updates;
    }
  }
}

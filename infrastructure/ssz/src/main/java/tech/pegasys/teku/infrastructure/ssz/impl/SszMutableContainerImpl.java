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

package tech.pegasys.teku.infrastructure.ssz.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszMutableContainerImpl extends AbstractSszMutableComposite<SszData, SszMutableData>
    implements SszMutableRefContainer {

  public SszMutableContainerImpl(final SszContainerImpl backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected TreeNode applyTreeChanges(
      final Stream<Map.Entry<Integer, SszData>> newChildValues,
      final TreeNode originalBackingTree) {
    if (!getSchema().isProgressiveMode()) {
      return super.applyTreeChanges(newChildValues, originalBackingTree);
    }

    final AbstractSszContainerSchema<?> schema = getSchema();
    final TreeNode dataTree = originalBackingTree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final TreeNode activeFieldsNode = originalBackingTree.get(GIndexUtil.RIGHT_CHILD_G_INDEX);

    final Int2ObjectMap<TreeNode> slotUpdates = new Int2ObjectOpenHashMap<>();
    newChildValues.forEach(
        entry ->
            slotUpdates.put(
                schema.getTreePosition(entry.getKey()), entry.getValue().getBackingNode()));

    final TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(
            dataTree, slotUpdates, schema.getActiveFields().length);

    return BranchNode.create(updatedDataTree, activeFieldsNode);
  }

  @Override
  protected SszContainerImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszData> viewCache) {
    return new SszContainerImpl(getSchema(), backingNode, viewCache);
  }

  @Override
  public AbstractSszContainerSchema<?> getSchema() {
    return (AbstractSszContainerSchema<?>) super.getSchema();
  }

  @Override
  public SszContainer commitChanges() {
    return (SszContainer) super.commitChanges();
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable SszData only");
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }
}

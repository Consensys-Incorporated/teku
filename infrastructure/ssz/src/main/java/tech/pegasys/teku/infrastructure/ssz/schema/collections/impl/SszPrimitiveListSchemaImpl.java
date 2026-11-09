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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszPrimitiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszPrimitiveListSchemaImpl<
        ElementT,
        SszElementT extends SszPrimitive<ElementT>,
        SszListT extends SszPrimitiveList<ElementT, SszElementT>>
    extends AbstractSszListSchema<SszElementT, SszListT>
    implements SszPrimitiveListSchema<ElementT, SszElementT, SszListT> {

  public SszPrimitiveListSchemaImpl(
      final SszPrimitiveSchema<ElementT, SszElementT> elementSchema, final long maxLength) {
    this(elementSchema, maxLength, SszSchemaHints.none());
  }

  public SszPrimitiveListSchemaImpl(
      final SszPrimitiveSchema<ElementT, SszElementT> elementSchema,
      final long maxLength,
      final SszSchemaHints hints) {
    super(elementSchema, maxLength, hints);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszPrimitiveListImpl<>(this, node);
  }

  @Override
  public TreeNode createTreeFromElements(final List<? extends SszElementT> elements) {
    final int size = elements.size();
    if (size == 0) {
      return createDefaultTree();
    }

    final SszPrimitiveSchema<ElementT, SszElementT> elementSchema = getPrimitiveElementSchema();
    final int bitsPerElement = elementSchema.getBitsSize();
    if (bitsPerElement < 8) {
      // Sub-byte types (e.g. bits) — fall back to generic path which already validates size
      return super.createTreeFromElements(elements);
    }

    checkArgument(
        size <= getMaxLength(),
        "Too many elements for this collection type (element type: %s, max length %s, size %s)",
        elements.getFirst().getClass().getName(),
        getMaxLength(),
        size);

    final int bytesPerElement = bitsPerElement / 8;
    final int elementsPerChunk = getElementsPerChunk();
    final int numChunks = (size + elementsPerChunk - 1) / elementsPerChunk;
    final int elementsInLastChunk = size - (numChunks - 1) * elementsPerChunk;
    final List<LeafNode> leafNodes = new ArrayList<>(numChunks);

    for (int chunk = 0; chunk < numChunks; chunk++) {
      final int chunkElements = (chunk < numChunks - 1) ? elementsPerChunk : elementsInLastChunk;
      final byte[] chunkBytes = new byte[chunkElements * bytesPerElement];
      final int elementBase = chunk * elementsPerChunk;
      for (int e = 0; e < chunkElements; e++) {
        final Bytes nodeData =
            ((LeafDataNode) elements.get(elementBase + e).getBackingNode()).getData();
        final int offset = e * bytesPerElement;
        for (int b = 0; b < bytesPerElement; b++) {
          chunkBytes[offset + b] = nodeData.get(b);
        }
      }
      leafNodes.add(LeafNode.create(Bytes.wrap(chunkBytes)));
    }

    final TreeNode dataTree = TreeUtil.createTree(leafNodes, treeDepth());
    return createTree(dataTree, size);
  }
}

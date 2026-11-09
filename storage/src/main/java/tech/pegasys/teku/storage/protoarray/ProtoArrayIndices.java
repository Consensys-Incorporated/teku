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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;

class ProtoArrayIndices {
  private final Object2IntMap<ForkChoiceNode> nodeIndices = new Object2IntOpenHashMap<>();

  boolean contains(final ForkChoiceNode node) {
    return nodeIndices.containsKey(node);
  }

  void add(final ForkChoiceNode node, final int nodeIndex) {
    nodeIndices.put(node, nodeIndex);
  }

  Optional<Integer> get(final ForkChoiceNode node) {
    final int nodeIndex = nodeIndices.getOrDefault(node, -1);
    return nodeIndex == -1 ? Optional.empty() : Optional.of(nodeIndex);
  }

  void remove(final ForkChoiceNode node) {
    nodeIndices.removeInt(node);
  }

  void offsetIndices(final int finalizedIndex) {
    nodeIndices.replaceAll(
        (key, value) -> {
          final int newIndex = value - finalizedIndex;
          checkState(newIndex >= 0, "ProtoArray: New array index less than 0.");
          return newIndex;
        });
  }

  Object2IntMap<ForkChoiceNode> getNodeIndices() {
    return nodeIndices;
  }
}

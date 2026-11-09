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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;

/**
 * Storage-side block node variants for fork choice.
 *
 * <p>The protoarray tracks node identities only. This record keeps the block-facing view used by
 * fork-aware models to map a beacon block root onto its base, EMPTY, and FULL nodes.
 */
record BlockNodeVariants(
    UInt64 slot,
    ForkChoiceNode baseNode,
    Optional<ForkChoiceNode> emptyNode,
    Optional<ForkChoiceNode> fullNode) {

  BlockNodeVariants withEmptyNode(final ForkChoiceNode node) {
    return new BlockNodeVariants(slot, baseNode, Optional.of(node), fullNode);
  }

  BlockNodeVariants withFullNode(final ForkChoiceNode node) {
    return new BlockNodeVariants(slot, baseNode, emptyNode, Optional.of(node));
  }

  Optional<ForkChoiceNode> getNode(final ForkChoicePayloadStatus payloadStatus) {
    return switch (payloadStatus) {
      case PAYLOAD_STATUS_PENDING -> Optional.of(baseNode);
      case PAYLOAD_STATUS_EMPTY -> emptyNode;
      case PAYLOAD_STATUS_FULL -> fullNode;
    };
  }

  List<ForkChoiceNode> allNodes() {
    final List<ForkChoiceNode> nodes = new ArrayList<>();
    nodes.add(baseNode);
    emptyNode.ifPresent(nodes::add);
    fullNode.ifPresent(nodes::add);
    return nodes;
  }
}

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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;

/**
 * Block-facing block-node variants index owned above the protoarray engine.
 *
 * <p>The protoarray itself only indexes {@link ForkChoiceNode}. This helper keeps the block-root
 * variants used by fork-aware models and compatibility block APIs.
 */
public class BlockNodeVariantsIndex {

  private final Map<Bytes32, BlockNodeVariants> variantsByRoot = new HashMap<>();
  private final Map<UInt64, Set<Bytes32>> blockRootsBySlot = new HashMap<>();

  static BlockNodeVariantsIndex fromProtoArray(final ProtoArray protoArray) {
    final BlockNodeVariantsIndex blockNodeVariantsIndex = new BlockNodeVariantsIndex();
    // Rebuilds the block-facing view from the protoarray's append order.
    // For a given block root, the base node must appear before any EMPTY or FULL variants.
    protoArray
        .getNodes()
        .forEach(
            node -> {
              switch (node.getPayloadStatus()) {
                case PAYLOAD_STATUS_PENDING ->
                    blockNodeVariantsIndex.putBaseNode(
                        node.getBlockRoot(), node.getBlockSlot(), node.getForkChoiceNode());
                case PAYLOAD_STATUS_EMPTY ->
                    blockNodeVariantsIndex.attachEmptyNode(
                        node.getBlockRoot(), node.getForkChoiceNode());
                case PAYLOAD_STATUS_FULL ->
                    blockNodeVariantsIndex.attachFullNode(
                        node.getBlockRoot(), node.getForkChoiceNode());
              }
            });
    return blockNodeVariantsIndex;
  }

  boolean containsBlock(final Bytes32 blockRoot) {
    return variantsByRoot.containsKey(blockRoot);
  }

  Optional<BlockNodeVariants> getVariants(final Bytes32 blockRoot) {
    return Optional.ofNullable(variantsByRoot.get(blockRoot));
  }

  Optional<UInt64> getSlot(final Bytes32 blockRoot) {
    return getVariants(blockRoot).map(BlockNodeVariants::slot);
  }

  Optional<ForkChoiceNode> getBaseNode(final Bytes32 blockRoot) {
    return getVariants(blockRoot).map(BlockNodeVariants::baseNode);
  }

  Optional<ForkChoiceNode> getEmptyNode(final Bytes32 blockRoot) {
    return getVariants(blockRoot).flatMap(BlockNodeVariants::emptyNode);
  }

  Optional<ForkChoiceNode> getFullNode(final Bytes32 blockRoot) {
    return getVariants(blockRoot).flatMap(BlockNodeVariants::fullNode);
  }

  Optional<ForkChoiceNode> getNode(
      final Bytes32 blockRoot, final ForkChoicePayloadStatus payloadStatus) {
    return getVariants(blockRoot).flatMap(variants -> variants.getNode(payloadStatus));
  }

  void putBaseNode(
      final Bytes32 blockRoot, final UInt64 slot, final ForkChoiceNode baseNodeIdentity) {
    Optional.ofNullable(variantsByRoot.get(blockRoot))
        .ifPresent(variants -> removeBlockRootFromSlot(variants.slot(), blockRoot));
    variantsByRoot.put(
        blockRoot,
        new BlockNodeVariants(slot, baseNodeIdentity, Optional.empty(), Optional.empty()));
    blockRootsBySlot.computeIfAbsent(slot, __ -> new LinkedHashSet<>()).add(blockRoot);
  }

  List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    return List.copyOf(blockRootsBySlot.getOrDefault(slot, Set.of()));
  }

  void attachEmptyNode(final Bytes32 blockRoot, final ForkChoiceNode emptyNodeIdentity) {
    final BlockNodeVariants variants = variantsByRoot.get(blockRoot);
    checkState(
        variants != null,
        "Cannot attach EMPTY node %s for unknown base block root %s",
        emptyNodeIdentity,
        blockRoot);
    variantsByRoot.put(blockRoot, variants.withEmptyNode(emptyNodeIdentity));
  }

  void attachFullNode(final Bytes32 blockRoot, final ForkChoiceNode fullNodeIdentity) {
    final BlockNodeVariants variants = variantsByRoot.get(blockRoot);
    checkState(
        variants != null,
        "Cannot attach FULL node %s for unknown base block root %s",
        fullNodeIdentity,
        blockRoot);
    variantsByRoot.put(blockRoot, variants.withFullNode(fullNodeIdentity));
  }

  boolean isBaseNode(final ForkChoiceNode node) {
    return getBaseNode(node.blockRoot()).filter(node::equals).isPresent();
  }

  void remove(final Bytes32 blockRoot) {
    Optional.ofNullable(variantsByRoot.remove(blockRoot))
        .ifPresent(variants -> removeBlockRootFromSlot(variants.slot(), blockRoot));
  }

  void removeIf(final Predicate<Bytes32> removeBlockRoot) {
    variantsByRoot.keySet().stream().filter(removeBlockRoot).toList().forEach(this::remove);
  }

  Collection<BlockNodeVariants> variants() {
    return variantsByRoot.values();
  }

  private void removeBlockRootFromSlot(final UInt64 slot, final Bytes32 blockRoot) {
    Optional.ofNullable(blockRootsBySlot.get(slot))
        .ifPresent(
            roots -> {
              roots.remove(blockRoot);
              if (roots.isEmpty()) {
                blockRootsBySlot.remove(slot);
              }
            });
  }
}
